package com.c203.altteulbe.friend.persistent.repository;

import com.c203.altteulbe.friend.web.dto.response.FriendRequestResponseDto;

import java.util.List;

public interface FriendRequestCustomRepository {
    List<FriendRequestResponseDto> findPendingRequestDtos(Long toUserId);
}
