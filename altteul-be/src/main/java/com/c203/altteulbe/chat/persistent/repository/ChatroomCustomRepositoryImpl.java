package com.c203.altteulbe.chat.persistent.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
import org.springframework.stereotype.Repository;

import com.c203.altteulbe.chat.persistent.entity.Chatroom;
import com.c203.altteulbe.chat.persistent.entity.QChatMessage;
import com.c203.altteulbe.chat.persistent.entity.QChatroom;
import com.c203.altteulbe.chat.persistent.entity.QUserChatRoom;
import com.c203.altteulbe.chat.web.dto.response.ChatMessageResponseDto;
import com.c203.altteulbe.chat.web.dto.response.ChatroomDetailResponseDto;
import com.c203.altteulbe.chat.web.dto.response.ChatroomListResponseDto;
import com.c203.altteulbe.friend.persistent.entity.QFriendship;
import com.c203.altteulbe.user.persistent.entity.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

@Repository
public class ChatroomCustomRepositoryImpl extends QuerydslRepositorySupport implements ChatroomCustomRepository {
	private final JPAQueryFactory queryFactory;

	private static final QChatMessage Q_CHATMESSAGE = QChatMessage.chatMessage;
	private static final QChatroom Q_CHATROOM = QChatroom.chatroom;
	private static final QUserChatRoom Q_USER_CHATROOM = QUserChatRoom.userChatRoom;
	private static final QUser Q_USER = QUser.user;
	private static final QFriendship Q_FRIENDSHIP = QFriendship.friendship;

	private record ChatroomPeer(
			Long userId, String nickname, String profileImg, LocalDateTime createdAt) {}

	public ChatroomCustomRepositoryImpl(JPAQueryFactory queryFactory) {
		super(Chatroom.class);
		this.queryFactory = queryFactory;
	}

	// 유저의 모든 채팅방 조회
	@Override
	public List<ChatroomListResponseDto> findAllChatroomsByUserId(Long userId) {
		return queryFactory
			.select(Projections.constructor(ChatroomListResponseDto.class,
				Q_USER.userId,
				Q_USER.nickname,
				Q_USER.profileImg,
				Expressions.constant(Boolean.FALSE), // 유저의 온라인 상태
				Q_CHATMESSAGE.messageContent,
				isMessageRead(userId),
				lastActivityAtExpr()))
			.from(Q_CHATROOM)
			.join(Q_USER_CHATROOM).on(Q_USER_CHATROOM.chatroom.eq(Q_CHATROOM))
			.join(Q_USER).on(Q_USER_CHATROOM.user.eq(Q_USER))
			.leftJoin(Q_CHATMESSAGE).on(
				Q_CHATMESSAGE.chatroom.eq(Q_CHATROOM)
					.and(isLatestMessage()))
			.where(Q_USER_CHATROOM.user.userId.ne(userId)
				.and(isFriendWith(userId))
				.and(isParticipant(userId)))
			.orderBy( lastActivityAtPath().desc())
			.fetch();
	}


	// 단일 채팅방 상세 정보
	@Override
	public Optional<ChatroomDetailResponseDto> findChatroomById(Long chatroomId, Long userId) {

		if (chatroomId == null) {
			return Optional.empty();
		}

		// 채팅방 기본 정보 조회
		ChatroomPeer peer = queryFactory
			.select(Projections.constructor(ChatroomPeer.class,
				Q_USER.userId,
				Q_USER.nickname,
				Q_USER.profileImg,
				Q_CHATROOM.createdAt
			))
			.distinct()
			.from(Q_CHATROOM)
			.join(Q_USER_CHATROOM).on(Q_USER_CHATROOM.chatroom.eq(Q_CHATROOM))
			.join(Q_USER).on(Q_USER_CHATROOM.user.eq(Q_USER))
			.where(Q_CHATROOM.chatroomId.eq(chatroomId)
				.and(Q_USER_CHATROOM.user.userId.ne(userId)))
			.fetchFirst();

		if (peer == null) {
			return Optional.empty();
		}

		List<ChatMessageResponseDto> messages = queryFactory
				.select(Projections.constructor(ChatMessageResponseDto.class,
						Q_CHATMESSAGE.chatMessageId,
						Q_USER.userId,
						Q_USER.nickname,
						Q_CHATMESSAGE.messageContent,
						Q_CHATMESSAGE.checked,
						Q_CHATMESSAGE.createdAt))
				.from(Q_CHATMESSAGE)
				.join(Q_CHATMESSAGE.sender, Q_USER)
				.where(Q_CHATMESSAGE.chatroom.chatroomId.eq(chatroomId))
				.orderBy(Q_CHATMESSAGE.createdAt.asc())
				.limit(60)
				.fetch();

		return Optional.of(ChatroomDetailResponseDto.builder()
				.chatroomId(chatroomId)
				.friendId(peer.userId())
				.nickname(peer.nickname())
				.profileImg(peer.profileImg())
				.isOnline(false)
				.messages(messages)
				.createdAt(peer.createdAt())
				.build());
	}

	@Override
	public Optional<ChatroomDetailResponseDto> findExistingChatroom(Long userId, Long friendId) {
		QUserChatRoom me = new QUserChatRoom("me");
		QUserChatRoom peer = new QUserChatRoom("peer");
		Long chatroomId = queryFactory
			.select(Q_CHATROOM.chatroomId)
			.from(Q_CHATROOM)
			.where(
				JPAExpressions
					.selectOne()
					.from(me)
					.where(
						me.chatroom.eq(Q_CHATROOM)
							.and(me.user.userId.eq(userId))
					).exists()
					.and(
						JPAExpressions
							.selectOne()
							.from(peer)
							.where(
									peer.chatroom.eq(Q_CHATROOM)
									.and(peer.user.userId.eq(friendId))
							).exists()
					)
			)
			.fetchFirst();
		return findChatroomById(chatroomId, userId);
	}

	// 내가 이 방의 참여자인가
	private static BooleanExpression isParticipant(Long userId) {
		QUserChatRoom sub = new QUserChatRoom("participantSub");
		return JPAExpressions.selectOne()
				.from(sub)
				.where(sub.chatroom.eq(Q_CHATROOM)
						.and(sub.user.userId.eq(userId)))
				.exists();
	}

	// 상대방과 친구인가
	private static BooleanExpression isFriendWith(Long userId) {
		return JPAExpressions.selectOne()
				.from(Q_FRIENDSHIP)
				.where(Q_FRIENDSHIP.user.userId.eq(userId).and(Q_FRIENDSHIP.friend.userId.eq(Q_USER.userId))
						.or(Q_FRIENDSHIP.user.userId.eq(Q_USER.userId).and(Q_FRIENDSHIP.friend.userId.eq(userId))))
				.exists();
	}

	// 방의 최신 메시지인가
	private static BooleanExpression isLatestMessage() {
		QChatMessage sub = new QChatMessage("latestSub");
		return Q_CHATMESSAGE.chatMessageId.eq(
				JPAExpressions.select(sub.chatMessageId.max())
						.from(sub)
						.where(sub.chatroom.eq(Q_CHATROOM)));
	}

	// 내가 상대 메시지를 다 읽었는가
	private static Expression<Boolean> isMessageRead(Long userId) {
		QChatMessage sub = new QChatMessage("unreadSub");
		return Expressions.cases()
				.when(JPAExpressions.selectOne()
						.from(sub)
						.where(sub.chatroom.eq(Q_CHATROOM)
								.and(sub.sender.userId.ne(userId))
								.and(sub.checked.isFalse()))
						.exists())
				.then(Boolean.FALSE)
				.otherwise(Boolean.TRUE);
	}

	private static Expression<LocalDateTime> lastActivityAtExpr() {
		return Expressions.as(
				Expressions.cases()
						.when(Q_CHATMESSAGE.createdAt.isNotNull())
						.then(Q_CHATMESSAGE.createdAt)
						.otherwise(Q_CHATROOM.createdAt),
				"lastActivityAt");
	}

	private static DateTimePath<LocalDateTime> lastActivityAtPath() {
		return Expressions.dateTimePath(LocalDateTime.class, "lastActivityAt");
	}
}
