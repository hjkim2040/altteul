package com.c203.altteulbe.chat.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.c203.altteulbe.chat.persistent.entity.ChatMessage;
import com.c203.altteulbe.chat.persistent.entity.Chatroom;
import com.c203.altteulbe.chat.persistent.repository.ChatMessageRepository;
import com.c203.altteulbe.chat.persistent.repository.ChatroomRepository;
import com.c203.altteulbe.chat.persistent.repository.UserChatRoomRepository;
import com.c203.altteulbe.chat.service.exception.MessageContentRequiredException;
import com.c203.altteulbe.chat.service.exception.NotFoundChatroomException;
import com.c203.altteulbe.chat.service.exception.NotParticipantException;
import com.c203.altteulbe.chat.web.dto.request.ChatMessageRequestDto;
import com.c203.altteulbe.chat.web.dto.response.ChatMessageReadResponseDto;
import com.c203.altteulbe.chat.web.dto.response.ChatMessageResponseDto;
import com.c203.altteulbe.user.persistent.entity.User;
import com.c203.altteulbe.user.persistent.repository.UserRepository;
import com.c203.altteulbe.user.service.exception.NotFoundUserException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
	private final ChatroomRepository chatroomRepository;
	private final UserRepository userRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final UserChatRoomRepository userChatRoomRepository;

	// 메세지 읽음으로 처리하기
	@Transactional
	public ChatMessageReadResponseDto markMessageAsRead(Long chatroomId, Long userId) {
		List<ChatMessage> unreadMessages = chatMessageRepository.findUnreadMessages(chatroomId, userId);
		if (unreadMessages.isEmpty()) {
			return null;
		}
		List<Long> messageIds = unreadMessages.stream()
			.map(ChatMessage::getChatMessageId)
			.toList();
		Long senderId = unreadMessages.get(0).getSender().getUserId();
		chatMessageRepository.updateMessageAsRead(messageIds);
		return ChatMessageReadResponseDto.builder()
			.chatroomId(chatroomId)
			.readerId(userId)
			.senderId(senderId)
			.readAt(LocalDateTime.now())
			.messageIds(messageIds)
			.build();
	}

	// 메세지 저장
	@Transactional
	public ChatMessageResponseDto saveMessage(Long chatroomId, ChatMessageRequestDto requestDto) {
		validateMessageContent(requestDto.getContent());

		String senderNickname = userChatRoomRepository
				.findParticipantNickname(chatroomId, requestDto.getSenderId())
				.orElseThrow(NotParticipantException::new);

		Chatroom chatroom = chatroomRepository.getReferenceById(chatroomId);
		User sender = userRepository.getReferenceById(requestDto.getSenderId());

		ChatMessage saved = ChatMessageRequestDto.to(requestDto, chatroom, sender);
		return ChatMessageResponseDto.of(saved, senderNickname);
	}

	// 메세지 검증
	private void validateMessageContent(String content) {
		if (content == null || content.trim().isEmpty()) {
			throw new MessageContentRequiredException();
		}
	}
}
