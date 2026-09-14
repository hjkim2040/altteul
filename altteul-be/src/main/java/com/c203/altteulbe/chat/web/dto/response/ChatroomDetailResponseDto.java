package com.c203.altteulbe.chat.web.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.c203.altteulbe.aws.util.S3Util;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatroomDetailResponseDto {
	private Long chatroomId;

	private Long friendId;            // 친구 ID

	private String nickname;          // 친구 닉네임

	private String profileImg;        // 친구 프로필 이미지

	private Boolean isOnline;         // 친구 온라인 상태

	private List<ChatMessageResponseDto> messages;  // 최근 60개 메시지

	private LocalDateTime createdAt;  // 채팅방

	public ChatroomDetailResponseDto withOnlineStatusAndImageUrl(boolean isOnline) {
		return new ChatroomDetailResponseDto(
				this.chatroomId,
				this.friendId,
				this.nickname,
				S3Util.getImgUrl(this.profileImg),
				isOnline,
				this.messages,
				this.createdAt
		);
	}
}
