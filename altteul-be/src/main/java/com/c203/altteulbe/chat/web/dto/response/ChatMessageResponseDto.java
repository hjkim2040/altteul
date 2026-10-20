package com.c203.altteulbe.chat.web.dto.response;

import java.time.LocalDateTime;

import com.c203.altteulbe.chat.persistent.entity.ChatMessage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponseDto {

	private Long chatMessageId;

	private Long senderId;

	private String senderNickname;

	private String messageContent;

	private boolean checked;

	private LocalDateTime createdAt;

	// Entity -> Dto
	public static ChatMessageResponseDto from(ChatMessage chatMessage) {
		return ChatMessageResponseDto.builder()
			.chatMessageId(chatMessage.getChatMessageId())
			.senderId(chatMessage.getSender().getUserId())
			.senderNickname(chatMessage.getSender().getNickname())
			.messageContent(chatMessage.getMessageContent())
			.checked(chatMessage.isChecked())
			.createdAt(chatMessage.getCreatedAt())
			.build();
	}

	public static ChatMessageResponseDto of(ChatMessage chatMessage, String senderNickname) {
		return ChatMessageResponseDto.builder()
				.chatMessageId(chatMessage.getChatMessageId())
				.senderId(chatMessage.getSender().getUserId())
				.senderNickname(senderNickname)
				.messageContent(chatMessage.getMessageContent())
				.checked(chatMessage.isChecked())
				.createdAt(chatMessage.getCreatedAt())
				.build();
	}
}
