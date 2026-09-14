package com.c203.altteulbe.user.web.dto.response;

import com.c203.altteulbe.aws.util.S3Util;
import com.c203.altteulbe.user.persistent.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchUserResponseDto {
	private Long userId;
	private String nickname;
	private String profileImg;
	private Boolean isOnline;

	public SearchUserResponseDto(Long userId, String nickname, String profileImg) {
		this.userId = userId;
		this.nickname = nickname;
		this.profileImg = S3Util.getImgUrl(profileImg);
		this.isOnline = false;
	}

	public static SearchUserResponseDto from(User user, Boolean isOnline) {
		return SearchUserResponseDto.builder()
			.userId(user.getUserId())
			.nickname(user.getNickname())
			.profileImg(S3Util.getImgUrl(user.getProfileImg()))
			.isOnline(isOnline)
			.build();
	}
}
