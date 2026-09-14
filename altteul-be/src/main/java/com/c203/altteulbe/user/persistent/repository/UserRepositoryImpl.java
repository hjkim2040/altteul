package com.c203.altteulbe.user.persistent.repository;

import static com.c203.altteulbe.ranking.persistent.entity.QTier.*;
import static com.c203.altteulbe.ranking.persistent.entity.QTodayRanking.*;
import static com.c203.altteulbe.user.persistent.entity.QUser.*;

import java.util.List;
import java.util.Optional;

import com.c203.altteulbe.user.web.dto.response.SearchUserResponseDto;
import com.querydsl.core.types.Projections;
import org.springframework.stereotype.Repository;

import com.c203.altteulbe.user.persistent.entity.User;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserCustomRepository {

	private final JPAQueryFactory queryFactory;

	@Override
	public boolean existsByUsername(String username) {
		Integer fetchOne = queryFactory
			.selectOne()
			.from(user)
			.where(user.username.eq(username))
			.fetchFirst();
		return fetchOne != null;
	}

	@Override
	public boolean existsByNickname(String nickname) {
		Integer fetchOne = queryFactory
			.selectOne()
			.from(user)
			.where(user.nickname.eq(nickname))
			.fetchFirst();
		return fetchOne != null;
	}

	@Override
	public Optional<User> findByUsername(String username) {
		return Optional.ofNullable(queryFactory
			.selectFrom(user)
			.where(user.username.eq(username))
			.fetchOne()
		);
	}

	@Override
	public Optional<User> findByProviderAndUsername(User.Provider provider, String username) {
		return Optional.ofNullable(queryFactory
			.selectFrom(user)
			.where(user.username.eq(username)
				.and(user.provider.eq(provider)))
			.fetchOne()
		);
	}

	public Optional<User> findWithRankingByUserId(Long userId) {
		return Optional.ofNullable(
			queryFactory
				.selectFrom(user)
				.leftJoin(user.todayRanking, todayRanking).fetchJoin()
				.leftJoin(user.tier, tier).fetchJoin()
				.where(user.userId.eq(userId))
				.fetchOne()
		);
	}

	@Override
	public List<User> findAllOrderedByRankingPointTierUsername() {
		return queryFactory
			.selectFrom(user)
			.orderBy(
				user.rankingPoint.desc(),
				user.tier.id.desc(),
				user.username.asc()
			)
			.fetch();
	}

	@Override
	public long countUsers() {
		return queryFactory
			.select(user.count())
			.from(user)
			.fetchOne();
	}

	@Override
	public List<SearchUserResponseDto> searchByNickname(String nickname, Long currentUserId) {
		return queryFactory
			.select(Projections.constructor(SearchUserResponseDto.class,
					user.userId,
					user.nickname,
					user.profileImg
			))
			.from(user)
			.where(user.nickname.containsIgnoreCase(nickname)
					.and(user.userId.ne(currentUserId)))
			.fetch();
	}
}