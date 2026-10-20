package com.c203.altteulbe.friend.persistent.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.c203.altteulbe.common.dto.RequestStatus;
import com.c203.altteulbe.friend.persistent.entity.FriendRequest;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long>, FriendRequestCustomRepository {
	List<FriendRequest> findAllByToUserIdAndRequestStatus(Long userId, RequestStatus status);

	boolean existsByFromUserIdAndToUserIdAndRequestStatus(Long fromUserId, Long toUserId, RequestStatus requestStatus);
}
