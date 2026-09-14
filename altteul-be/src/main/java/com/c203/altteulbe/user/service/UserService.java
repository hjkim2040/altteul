package com.c203.altteulbe.user.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.c203.altteulbe.aws.service.S3Service;
import com.c203.altteulbe.aws.util.S3Util;
import com.c203.altteulbe.common.exception.BusinessException;
import com.c203.altteulbe.friend.service.UserStatusService;
import com.c203.altteulbe.user.persistent.entity.User;
import com.c203.altteulbe.user.persistent.repository.UserRepository;
import com.c203.altteulbe.user.service.exception.NotFoundUserException;
import com.c203.altteulbe.user.web.dto.request.UpdateProfileRequestDto;
import com.c203.altteulbe.user.web.dto.response.SearchUserResponseDto;
import com.c203.altteulbe.user.web.dto.response.UserProfileResponseDto;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
	private final S3Service s3Service;
	private final UserRepository userRepository;
	private final UserStatusService userStatusService;

	public List<SearchUserResponseDto> searchUser(Long userId, String nickname) {
		List<SearchUserResponseDto> users = userRepository.searchByNickname(nickname, userId);

		// 모든 검색된 사용자의 ID 리스트 추출
		List<Long> userIds = users.stream()
			.map(SearchUserResponseDto::getUserId)
			.toList();

		// 한 번에 모든 사용자의 온라인 상태 조회
		Map<Long, Boolean> onlineStatus = userStatusService.getBulkOnlineStatus(userIds);

		// DTO 변환 시 조회해둔 온라인 상태 맵 활용
		return users.stream()
			.map(user -> new SearchUserResponseDto(
					user.getUserId(),
					user.getNickname(),
					user.getProfileImg(),
					onlineStatus.getOrDefault(user.getUserId(), false)
			))
			.toList();
	}

	public UserProfileResponseDto getUserProfile(Long userId, Long currentUserId) {
		User user = userRepository.findWithRankingByUserId(userId)
			.orElseThrow(NotFoundUserException::new);

		Long totalCount = userRepository.count();
		return UserProfileResponseDto.from(user, totalCount, currentUserId);
	}

	public void updateUserProfile(UpdateProfileRequestDto request, MultipartFile newImg, Long currentUserId) {
		String defaultProfileImgKey = S3Util.getDefaultImgKey();
		System.out.println(currentUserId);
		if (currentUserId == null) {
			throw new BusinessException("로그인이 필요한 서비스입니다.", HttpStatus.BAD_REQUEST);
		}
		User user = userRepository.findById(currentUserId).orElseThrow(NotFoundUserException::new);
		if (!user.getUserId().equals(currentUserId)) {
			throw new BusinessException("본인의 프로필만 수정할 수 있습니다.", HttpStatus.BAD_REQUEST);
		}

		userRepository.findByNickname(request.getNickname())
			.ifPresent(existUser -> {
				if (!existUser.getUserId().equals(currentUserId)) {
					throw new BusinessException("이미 사용중인 닉네임입니다.", HttpStatus.BAD_REQUEST);
				}
			});

		user.updateProfile(request.getNickname(), request.getMainLang());

		String currentProfileImg = user.getProfileImg();

		if (newImg != null && !newImg.isEmpty()) {
			// S3에 새 이미지 업로드
			String newProfileImgKey = s3Service.uploadFiles(newImg, "uploads/");

			// 기존 이미지가 기본 이미지가 아니면 S3에서 삭제
			if (!defaultProfileImgKey.equals(currentProfileImg)) {
				s3Service.deleteFile(currentProfileImg);
			}
			// 새로운 프로필 이미지 저장
			user.updateProfileImage(newProfileImgKey);
		}
	}
}
