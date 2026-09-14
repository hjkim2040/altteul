package com.c203.altteulbe.chat.web.dto.response;

import java.time.LocalDateTime;

import com.c203.altteulbe.aws.util.S3Util;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatroomListResponseDto {

	private Long friendId;

	private String nickname;

	private String profileImg;

	private Boolean isOnline;

	private String recentMessage;

	private Boolean isMessageRead;

	private LocalDateTime createdAt;

	public ChatroomListResponseDto withOnlineStatusAndImageUrl (boolean isOnline) {
		return new ChatroomListResponseDto(
			this.friendId,
			this.nickname,
			S3Util.getImgUrl(this.profileImg),
			isOnline,
			this.recentMessage,
			this.isMessageRead,
			this.createdAt
		);
	}
}
