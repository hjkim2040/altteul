package com.c203.altteulbe.friend.persistent.repository;

import com.c203.altteulbe.common.dto.RequestStatus;
import com.c203.altteulbe.friend.persistent.entity.QFriendRequest;
import com.c203.altteulbe.friend.web.dto.response.FriendRequestResponseDto;
import com.c203.altteulbe.user.persistent.entity.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import java.util.List;

public class FriendRequestCustomRepositoryImpl implements FriendRequestCustomRepository {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<FriendRequestResponseDto> findPendingRequestDtos(Long toUserId) {
        QFriendRequest fr = QFriendRequest.friendRequest;
        QUser fromUser = new QUser("fromUser");
        return queryFactory
                .select(Projections.constructor(FriendRequestResponseDto.class,
                        fr.friendRequestId, fromUser.userId, fromUser.nickname,
                        fromUser.profileImg, fr.requestStatus))
                .from(fr)
                .join(fr.from, fromUser)
                .where(fr.to.userId.eq(toUserId), fr.requestStatus.eq(RequestStatus.P))
                .fetch();
    }
}
