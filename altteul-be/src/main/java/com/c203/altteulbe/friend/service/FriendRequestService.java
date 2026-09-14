package com.c203.altteulbe.friend.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.c203.altteulbe.common.dto.RequestStatus;
import com.c203.altteulbe.common.response.PageResponse;
import com.c203.altteulbe.common.utils.PaginateUtil;
import com.c203.altteulbe.friend.persistent.entity.FriendRequest;
import com.c203.altteulbe.friend.persistent.entity.Friendship;
import com.c203.altteulbe.friend.persistent.repository.FriendRequestRepository;
import com.c203.altteulbe.friend.persistent.repository.FriendshipRepository;
import com.c203.altteulbe.friend.service.exception.AlreadyFriendException;
import com.c203.altteulbe.friend.service.exception.AlreadyProcessRequestException;
import com.c203.altteulbe.friend.service.exception.AuthorizationException;
import com.c203.altteulbe.friend.service.exception.ExistingPendingFriendRequestException;
import com.c203.altteulbe.friend.service.exception.FriendRequestNotFoundException;
import com.c203.altteulbe.friend.service.exception.InvalidFriendRequestException;
import com.c203.altteulbe.friend.service.exception.SuspendedUserException;
import com.c203.altteulbe.friend.service.exception.WithdrawUserException;
import com.c203.altteulbe.friend.web.dto.response.FriendRequestResponseDto;
import com.c203.altteulbe.user.persistent.entity.User;
import com.c203.altteulbe.user.persistent.repository.UserRepository;
import com.c203.altteulbe.user.service.exception.NotFoundUserException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendRequestService {
	private final FriendRequestRepository friendRequestRepository;
	private final UserRepository userRepository;
	private final FriendshipRepository friendshipRepository;
	private final FriendRedisService friendRedisService;

	// 친구 요청 목록 조회
	@Transactional
	public PageResponse<FriendRequestResponseDto> getPendingRequestsFromRedis(Long userId, Pageable pageable) {
		if (!userRepository.existsById(userId)) {
			log.error("유저 찾기 실패");
			throw new NotFoundUserException();
		}
		// 캐시된 친구 요청 목록 페이지네이션
		List<FriendRequestResponseDto> requests = friendRedisService.getCachedFriendRequests(userId)
				.orElseGet(() -> {
					List<FriendRequestResponseDto> fromDb = getPendingFriendRequestsFromDB(userId);
					friendRedisService.cacheFriendRequests(userId, fromDb);
					return fromDb;
				});
		Page<FriendRequestResponseDto> paginateResult = PaginateUtil.paginate(requests,
			pageable.getPageNumber(), pageable.getPageSize());
		return new PageResponse<>("friendRequests", paginateResult);
	}

	// db에서 친구 요청 목록 가져오기
	private List<FriendRequestResponseDto> getPendingFriendRequestsFromDB(Long userId) {
		return friendRequestRepository.findAllByToUserIdAndRequestStatus(
				userId,
				RequestStatus.P)
			.stream()
			.map(FriendRequestResponseDto::from)
			.collect(Collectors.toList());
	}

	// 친구 신청 생성
	@Transactional
	public FriendRequestResponseDto createFriendRequest(Long fromUserId, Long toUserId) {
		User fromUser = userRepository.findById(fromUserId).orElseThrow(NotFoundUserException::new);
		User toUser = userRepository.findById(toUserId).orElseThrow(NotFoundUserException::new);
		validateFriendRequest(fromUser, toUser);
		FriendRequest friendRequest = new FriendRequest(fromUser, toUser);
		FriendRequest saved = friendRequestRepository.save(friendRequest);
		// 친구 요청이 새로 생겼으니 이미 있던 친구 요청 목록 캐시 삭제
		friendRedisService.invalidateFriendRequests(toUser.getUserId());
		return FriendRequestResponseDto.from(saved);
	}

	// 요청 처리
	@Transactional
	public void processRequest(Long requestId, Long userId, RequestStatus status) {
		// 요청 조회 및 검증
		FriendRequest request = friendRequestRepository.findById(requestId)
			.orElseThrow(() -> {
				log.error("친구 신청 찾기 실패");
				return new FriendRequestNotFoundException();
			});

		// 수신자 본인 확인
		if (!request.getTo().getUserId().equals(userId)) {
			log.error("권한 없음");
			throw new AuthorizationException();
		}

		// 이미 처리된 요청인지 확인
		if (request.getRequestStatus() != RequestStatus.P) {
			log.error("이미 처리된 친구 신청");
			throw new AlreadyProcessRequestException();
		}

		// 상태 업데이트
		request.updateStatus(status);

		// 수락된 경우 친구 관계 생성
		if (status == RequestStatus.A) {

			validateFriendRequest(request.getFrom(), request.getTo());

			Friendship friendshipFromTo = Friendship.createFriendship(
				request.getFrom(),
				request.getTo()
			);

			Friendship friendshipToFrom = Friendship.createFriendship(
				request.getTo(),
				request.getFrom()
			);
			friendshipRepository.saveAll(
				List.of(friendshipFromTo, friendshipToFrom)
			);
		}
		// Redis 캐시 업데이트를 별도 서비스로 위임
		friendRedisService.invalidateCaches(
			userId, // 로그인한 유저 id
			request.getFrom().getUserId() // 친구 유저 id
		);
	}

	// 친구 요청 검증
	private void validateFriendRequest(User fromUser, User toUser) {
		if (fromUser.getUserId().equals(toUser.getUserId())) {
			throw new InvalidFriendRequestException();
		}

		// redis에서 친구인지 확인
		if (friendRedisService.checkFriendRelation(fromUser.getUserId(), toUser.getUserId())) {
			throw new AlreadyFriendException();
		}

		// redis에 없는 경우 db 확인
		if (friendshipRepository.existsByUserAndFriend(fromUser.getUserId(), toUser.getUserId())) {
			throw new AlreadyFriendException();
		}

		if (friendRequestRepository.existsByFromUserIdAndToUserIdAndRequestStatus(
			fromUser.getUserId(), toUser.getUserId(), RequestStatus.P) ||
			friendRequestRepository.existsByFromUserIdAndToUserIdAndRequestStatus(
				toUser.getUserId(), fromUser.getUserId(), RequestStatus.P)) {
			throw new ExistingPendingFriendRequestException();
		}

		if (toUser.getUserStatus() == User.UserStatus.D) {
			throw new WithdrawUserException();
		}

		if (toUser.getUserStatus() == User.UserStatus.S) {
			throw new SuspendedUserException();
		}
	}
}


