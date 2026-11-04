package com.c203.altteulbe.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.c203.altteulbe.user.service.UserStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.c203.altteulbe.chat.persistent.entity.Chatroom;
import com.c203.altteulbe.chat.persistent.entity.UserChatRoom;
import com.c203.altteulbe.chat.persistent.repository.ChatroomRepository;
import com.c203.altteulbe.chat.persistent.repository.UserChatRoomRepository;
import com.c203.altteulbe.chat.service.exception.NotFoundChatroomException;
import com.c203.altteulbe.chat.service.exception.SelfChatroomCreateException;
import com.c203.altteulbe.chat.web.dto.response.ChatroomDetailResponseDto;
import com.c203.altteulbe.chat.web.dto.response.ChatroomListResponseDto;
import com.c203.altteulbe.friend.persistent.repository.FriendshipRepository;
import com.c203.altteulbe.friend.service.exception.FriendRelationNotFoundException;
import com.c203.altteulbe.user.persistent.entity.User;
import com.c203.altteulbe.user.persistent.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatroomService {
	private final ChatroomRepository chatroomRepository;
	private final UserRepository userRepository;
	private final UserChatRoomRepository userChatRoomRepository;
	private final FriendshipRepository friendshipRepository;
	private final UserStatusService userStatusService;

	public List<ChatroomListResponseDto> getAllChatrooms(Long userId) {
		List<ChatroomListResponseDto> chatrooms = chatroomRepository.findAllChatroomsByUserId(userId);

		List<Long> friendIds = chatrooms.stream()
				.map(ChatroomListResponseDto::getFriendId)
				.toList();
		if (friendIds.isEmpty()) {
			return chatrooms;
		}
		Map<Long, Boolean> onlineStatus = userStatusService.getBulkOnlineStatus(friendIds);

		return chatrooms.stream()
				.map(room -> room.withOnlineStatusAndImageUrl(
								onlineStatus.getOrDefault(room.getFriendId(), false)))
				.toList();
	}

	public ChatroomDetailResponseDto getChatroom(Long chatroomId, Long userId) {
		ChatroomDetailResponseDto room = chatroomRepository.findChatroomById(chatroomId, userId)
				.orElseThrow(NotFoundChatroomException::new);
		return room.withOnlineStatusAndImageUrl(userStatusService.isUserOnline(room.getFriendId()));
	}

	// 채팅방 생성 혹은 조회
	@Transactional
	public ChatroomDetailResponseDto createOrGetChatroom(Long userId, Long friendId) {
		// 자기 자신과의 채팅방 생성 시도
		if (userId.equals(friendId)) {
			throw new SelfChatroomCreateException();
		}
		// 친구 관계 검증
		if (!friendshipRepository.existsByUserAndFriend(userId, friendId)) {
			throw new FriendRelationNotFoundException();
		}

		// 이미 존재하는 1:1 채팅방 확인
		Optional<ChatroomDetailResponseDto> existingChatroom =
			chatroomRepository.findExistingChatroom(userId, friendId);
		if (existingChatroom.isPresent()) {
			return existingChatroom.get();
		}

		// 유저 찾기
		User user = userRepository.getReferenceById(userId);
		User friend = userRepository.getReferenceById(friendId);

		// 새 채팅방 생성
		Chatroom newChatroom = chatroomRepository.save(new Chatroom());

		// 사용자와 친구를 채팅방에 추가
		createUserChatRoom(newChatroom, user);
		createUserChatRoom(newChatroom, friend);
		return getChatroom(newChatroom.getChatroomId(), userId);
	}

	@Transactional
	public void createUserChatRoom(Chatroom chatroom, User user) {
		UserChatRoom userChatRoom = UserChatRoom.builder()
			.chatroom(chatroom)
			.user(user)
			.build();
		userChatRoomRepository.save(userChatRoom);
	}
}
