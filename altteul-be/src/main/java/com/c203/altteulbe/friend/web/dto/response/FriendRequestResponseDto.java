package com.c203.altteulbe.friend.web.dto.response;

import com.c203.altteulbe.aws.util.S3Util;
import com.c203.altteulbe.common.dto.RequestStatus;
import com.c203.altteulbe.friend.persistent.entity.FriendRequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendRequestResponseDto {

	private Long friendRequestId;

	private Long fromUserId;

	private String fromUserNickname;

	private String fromUserProfileImg;

	private RequestStatus requestStatus;

	public static FriendRequestResponseDto from(FriendRequest friendRequest) {
		return FriendRequestResponseDto.builder()
			.friendRequestId(friendRequest.getFriendRequestId())
			.fromUserId(friendRequest.getFrom().getUserId())
			.fromUserNickname(friendRequest.getFrom().getNickname())
			.fromUserProfileImg(S3Util.getImgUrl(friendRequest.getFrom().getProfileImg()))
			.requestStatus(friendRequest.getRequestStatus())
			.build();
	}

	public FriendRequestResponseDto withImgUrl() {
		return new FriendRequestResponseDto(friendRequestId, fromUserId, fromUserNickname,
				S3Util.getImgUrl(fromUserProfileImg), requestStatus);
	}
}
