package com.c203.altteulbe.friend.web.dto.response;

import com.c203.altteulbe.aws.util.S3Util;
import com.c203.altteulbe.friend.persistent.entity.Friendship;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@Getter
@NoArgsConstructor
public class FriendResponseDto {

	private Long userid;

	private String nickname;

	private String profileImg;

	private Boolean isOnline;

	public FriendResponseDto(Long userid, String nickname, String profileImg) {
		this.userid = userid;
		this.nickname = nickname;
		this.profileImg = S3Util.getImgUrl(profileImg);
		this.isOnline = false;
	}

	public static FriendResponseDto from(Friendship friendship, boolean isOnline) {
		return FriendResponseDto.builder()
			.userid(friendship.getFriend().getUserId())
			.nickname(friendship.getFriend().getNickname())
			.profileImg(S3Util.getImgUrl(friendship.getFriend().getProfileImg()))
			.isOnline(isOnline)
			.build();

	}

	public FriendResponseDto updateOnlineStatus(Boolean isOnline) {
		return new FriendResponseDto(
			this.userid,
			this.nickname,
			this.profileImg,
			isOnline // 새로운 온라인 상태
		);
	}
}
