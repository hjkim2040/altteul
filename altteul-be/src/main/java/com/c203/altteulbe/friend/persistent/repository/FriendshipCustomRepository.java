package com.c203.altteulbe.friend.persistent.repository;

import com.c203.altteulbe.friend.persistent.entity.Friendship;
import com.c203.altteulbe.friend.web.dto.response.FriendResponseDto;

import java.util.List;

public interface FriendshipCustomRepository {
	boolean existsByUserAndFriend(Long userId, Long friendId);

	List<Friendship> findAllByUserIdWithFriend(long userId);

	List<FriendResponseDto> findFriendDtosByUserId(Long userId);
}
