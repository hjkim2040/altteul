package com.c203.altteulbe.friend.web.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.c203.altteulbe.common.dto.RequestStatus;
import com.c203.altteulbe.common.response.ApiResponse;
import com.c203.altteulbe.common.response.ApiResponseEntity;
import com.c203.altteulbe.common.response.PageResponse;
import com.c203.altteulbe.common.response.ResponseBody;
import com.c203.altteulbe.friend.service.FriendRequestService;
import com.c203.altteulbe.friend.service.FriendWSService;
import com.c203.altteulbe.friend.web.dto.request.CreateFriendRequestDto;
import com.c203.altteulbe.friend.web.dto.request.ProcessFriendRequestDto;
import com.c203.altteulbe.friend.web.dto.response.FriendRequestResponseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
@Slf4j
public class FriendRequestController {
	private final FriendRequestService friendRequestService;
	private final FriendWSService friendWSService;

	// 친구 요청 리스트 조회
	@GetMapping("/friend/request")
	public ApiResponseEntity<ResponseBody.Success<PageResponse<FriendRequestResponseDto>>> getFriendRequestList(
		@AuthenticationPrincipal Long id,
		@PageableDefault(page = 0, size = 10) Pageable pageable
	) {
		PageResponse<FriendRequestResponseDto> friendRequest = friendRequestService.getPendingRequestsFromRedis(id,
			pageable);
		return ApiResponse.success(friendRequest, HttpStatus.OK);
	}

	// 친구 신청
	@PostMapping("/friend/request")
	public ApiResponseEntity<Void> handleFriendRequest(@RequestBody CreateFriendRequestDto request,
		@AuthenticationPrincipal Long userId) {
		Long toUserId = request.getToUserId();
		FriendRequestResponseDto result = friendRequestService.createFriendRequest(userId, toUserId);

		friendWSService.sendRequestMessage(toUserId, result);
		return ApiResponse.success();
	}

	// 친구 요청 처리
	@PostMapping("/friend/request/process")
	public ApiResponseEntity<Void> handleRequestProcess(
		@RequestBody ProcessFriendRequestDto request,
		@AuthenticationPrincipal Long userId
	) {
		Long requestId = request.getFriendRequestId();
		RequestStatus status = request.getRequestStatus();
		friendRequestService.processRequest(requestId, userId, status);
		// 친구 신청을 수락했을 경우
		if (status == RequestStatus.A) {
			// 클라이언트에게 업데이트가 필요하다고 알림
			friendWSService.sendFriendListUpdateMessage(userId);
			friendWSService.sendFriendListUpdateMessage(request.getFromUserId());
		}
		return ApiResponse.success();
	}

}
