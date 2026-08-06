package com.c203.altteulbe.friend.persistent.repository;

import java.util.List;

import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
import org.springframework.stereotype.Repository;

import com.c203.altteulbe.friend.persistent.entity.Friendship;
import com.c203.altteulbe.friend.persistent.entity.QFriendship;
import com.querydsl.jpa.impl.JPAQueryFactory;

@Repository
public class FriendshipRepositoryImpl extends QuerydslRepositorySupport implements FriendshipCustomRepository {
	private final JPAQueryFactory queryFactory;
	private final QFriendship friendship = QFriendship.friendship;

	public FriendshipRepositoryImpl(JPAQueryFactory queryFactory) {
		super(Friendship.class);
		this.queryFactory = queryFactory;
	}

	@Override
	public List<Friendship> findAllByUserIdWithFriend(long userId) {
		return queryFactory
				.selectFrom(friendship)
				.leftJoin(friendship.friend).fetchJoin()
				.where(friendship.id.userId.eq(userId))
				.fetch();
	}

	@Override
	public boolean existsByUserAndFriend(Long userId, Long friendId) {
		return queryFactory
			.selectOne()
			.from(friendship)
			.where(friendship.user.userId.eq(userId)
				.and(friendship.friend.userId.eq(friendId)))
			.fetchFirst() != null;
	}
}
