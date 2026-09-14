package com.c203.altteulbe.chat.web.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.c203.altteulbe.chat.service.ChatMessageService;
import com.c203.altteulbe.chat.service.exception.UnauthorizedMessageSenderException;
import com.c203.altteulbe.chat.web.dto.request.ChatMessageRequestDto;
import com.c203.altteulbe.chat.web.dto.response.ChatMessageReadResponseDto;
import com.c203.altteulbe.chat.web.dto.response.ChatMessageResponseDto;
import com.c203.altteulbe.websocket.dto.response.WebSocketResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWSController {
	private final SimpMessagingTemplate messagingTemplate;
	private final ChatMessageService chatMessageService;

	// 메세지 읽음 처리
	@MessageMapping("/chat/room/{chatroomId}/read")
	public void messageRead(@DestinationVariable(value = "chatroomId") Long chatroomId,
		SimpMessageHeaderAccessor headerAccessor) {
		Long id = Long.parseLong(headerAccessor.getSessionAttributes().get("userId").toString());
		ChatMessageReadResponseDto response = chatMessageService.markMessageAsRead(chatroomId, id);
		if (response != null) {
			messagingTemplate.convertAndSend("/sub/chat/room/" + chatroomId + "/read",
				WebSocketResponse.withData("읽은 채팅 메세지", response));
		}
	}

	// 채팅방에서 대화
	@MessageMapping("/chat/room/{chatroomId}/message")
	public void handleMessage(
		@DestinationVariable(value = "chatroomId") Long chatroomId,
		@Payload ChatMessageRequestDto requestDto,
		SimpMessageHeaderAccessor headerAccessor) {
		Long id = Long.parseLong(headerAccessor.getSessionAttributes().get("userId").toString());

		// 메시지 발신자 검증
		if (!id.equals(requestDto.getSenderId())) {
			throw new UnauthorizedMessageSenderException();
		}
		// 메세지 저장
		ChatMessageResponseDto savedMessage = chatMessageService.saveMessage(chatroomId, requestDto);
		messagingTemplate.convertAndSend(
			"/sub/chat/room/" + chatroomId,
			WebSocketResponse.withData("새 메시지", savedMessage)
		);
	}
}

