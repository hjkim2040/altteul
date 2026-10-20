package com.c203.altteulbe.chat.persistent.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import com.c203.altteulbe.chat.persistent.entity.UserChatRoom;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserChatRoomRepository extends JpaRepository<UserChatRoom, Long> {
	boolean existsByChatroom_ChatroomIdAndUser_UserId(Long chatRoomId, Long userId);

	@Query("select u.nickname from UserChatRoom ucr join ucr.user u " +
			"where ucr.chatroom.chatroomId = :chatroomId and u.userId = :userId")
    Optional<String> findParticipantNickname(@Param("chatroomId") Long chatroomId,
                                             @Param("userId") Long userId);
}
