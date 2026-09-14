package com.c203.altteulbe.friend.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.c203.altteulbe.common.response.PageResponse;
import com.c203.altteulbe.common.utils.PaginateUtil;
import com.c203.altteulbe.friend.persistent.entity.FriendId;
import com.c203.altteulbe.friend.persistent.repository.FriendshipRepository;
import com.c203.altteulbe.friend.service.exception.FriendRelationNotFoundException;
import com.c203.altteulbe.friend.web.dto.response.FriendResponseDto;
import com.c203.altteulbe.user.persistent.repository.UserRepository;
import com.c203.altteulbe.user.service.exception.NotFoundUserException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipService {

	private final FriendshipRepository friendshipRepository;
	private final UserStatusService userStatusService;
	private final UserRepository userRepository;
	private final FriendRedisService friendRedisService;

	@Transactional(readOnly = true)
	public PageResponse<FriendResponseDto> getFriendsList(Long userId, Pageable pageable) {
		userRepository.findByUserId(userId)
			.orElseThrow(NotFoundUserException::new);

		// 캐시된 친구 리스트 조회
		List<FriendResponseDto> friendList = friendRedisService.getCachedFriendList(userId)
				.orElseGet(() -> {
					List<FriendResponseDto> fromDb = friendshipRepository.findFriendDtosByUserId(userId);
					friendRedisService.setFriendList(userId, fromDb);
					return fromDb;
				});

		// 친구 관계이 있는 유저의 id들을 리스트로 만들기
		List<Long> friendIds = friendList.stream()
			.map(FriendResponseDto::getUserid)
			.toList();
		// 유저들의 온라인 상태 확인
		Map<Long, Boolean> onlineStatus = userStatusService.getBulkOnlineStatus(friendIds);

		List<FriendResponseDto> responseList = friendList.stream()
			.map(friend -> friend.updateOnlineStatus(onlineStatus.get(friend.getUserid())))
			.toList();

		// 친구 리스트 캐싱

		Page<FriendResponseDto> paginateResult = PaginateUtil.paginate(responseList, pageable.getPageNumber(),
			pageable.getPageSize());
		return new PageResponse<>("friends", paginateResult);
	}

	// 친구 관계 삭제
	@Transactional
	public void deleteFriendship(Long userId, Long friendId) {
		if (!friendshipRepository.existsByUserAndFriend(userId, friendId)) {
			throw new FriendRelationNotFoundException();
		}
		friendshipRepository.deleteById(new FriendId(userId, friendId));
		friendshipRepository.deleteById(new FriendId(friendId, userId));
		// redis에서도 친구 관계 삭제
		// Redis 캐시 업데이트를 하나의 트랜잭션으로 처리
		friendRedisService.invalidateCaches(userId, friendId);
	}
}

