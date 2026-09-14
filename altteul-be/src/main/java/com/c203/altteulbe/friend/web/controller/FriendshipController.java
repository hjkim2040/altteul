package com.c203.altteulbe.friend.web.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.c203.altteulbe.common.response.ApiResponse;
import com.c203.altteulbe.common.response.ApiResponseEntity;
import com.c203.altteulbe.common.response.PageResponse;
import com.c203.altteulbe.common.response.ResponseBody;
import com.c203.altteulbe.friend.service.FriendWSService;
import com.c203.altteulbe.friend.service.FriendshipService;
import com.c203.altteulbe.friend.web.dto.request.DeleteFriendRequestDto;
import com.c203.altteulbe.friend.web.dto.response.FriendResponseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class FriendshipController {
	private final FriendshipService friendshipService;
	private final FriendWSService friendWSService;

	// 친구 리스트 조회
	@GetMapping("/friends")
	@PreAuthorize("isAuthenticated()")
	public ApiResponseEntity<ResponseBody.Success<PageResponse<FriendResponseDto>>> getFriends(
		@AuthenticationPrincipal Long id,
		@PageableDefault(page = 0, size = 10) Pageable pageable
	) {
		PageResponse<FriendResponseDto> friends = friendshipService.getFriendsList(id, pageable);
		return ApiResponse.success(friends, HttpStatus.OK);
	}

	// 친구 삭제
	@DeleteMapping("/friend/delete")
	public ApiResponseEntity<Void> handleFriendDelete(@RequestBody DeleteFriendRequestDto request,
		@AuthenticationPrincipal Long userId) {
		friendshipService.deleteFriendship(userId, request.getFriendId());
		// 친구 삭제 했을 경우 클라이언트에게 업데이트가 필요하다고 알림
		friendWSService.sendFriendListUpdateMessage(userId);
		friendWSService.sendFriendListUpdateMessage(request.getFriendId());
		return ApiResponse.success();
	}
}
