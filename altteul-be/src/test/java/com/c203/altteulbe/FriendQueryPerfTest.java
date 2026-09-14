package com.c203.altteulbe;

import com.c203.altteulbe.common.dto.RequestStatus;
import com.c203.altteulbe.friend.persistent.entity.Friendship;
import com.c203.altteulbe.friend.persistent.entity.QFriendship;
import com.c203.altteulbe.friend.persistent.repository.FriendshipRepository;
import com.c203.altteulbe.friend.service.FriendRequestService;
import com.c203.altteulbe.friend.service.FriendshipService;
import com.c203.altteulbe.friend.web.dto.response.FriendResponseDto;
import com.c203.altteulbe.user.persistent.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 포트폴리오 성능 측정용 테스트.
 * 프로젝트 종료 후 개선 전후 쿼리 수를 재현 측정하기 위해 작성.
 * 실행 전 DummyDataLoader로 더미 데이터를 먼저 적재해야 함.
 */

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class FriendQueryPerfTest{
    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private FriendRequestService friendRequestService;

    @Autowired
    private JPAQueryFactory queryFactory;

    @PersistenceContext
    private EntityManager em;

    private Statistics stats;

    private static final long TEST_USER_ID = 1L;

    @BeforeEach
    void initStatistics() {
        stats = em.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.setStatisticsEnabled(true);
    }

    private long measure(Runnable task) {
        em.flush();
        em.clear();
        long before = stats.getPrepareStatementCount();
        task.run();
        return stats.getPrepareStatementCount() - before;
    }

    @Test
    @DisplayName("fetchJoin 미적용 시 친구 수만큼 추가 쿼리 발생")
    void withoutFetchJoin() {
        QFriendship f = QFriendship.friendship;

        long count = measure(() -> {
            List<Friendship> result = queryFactory
                    .selectFrom(f)                    // fetchJoin 없음
                    .where(f.id.userId.eq(TEST_USER_ID))
                    .fetch();
            // LAZY 프록시 강제 초기화 → 여기서 N번 SELECT
            result.forEach(fs -> fs.getFriend().getNickname());
        });

        System.out.println("fetchJoin 미적용 쿼리 수 = " + count);
    }

    @Test
    @DisplayName("fetchJoin 적용 시 단일 쿼리")
    void withFetchJoin() {
        long count = measure(() -> {
            List<Friendship> result =
                    friendshipRepository.findAllByUserIdWithFriend(TEST_USER_ID);
            result.forEach(fs -> fs.getFriend().getNickname());
        });

        System.out.println("fetchJoin 적용 쿼리 수 = " + count);
    }

    @Test
    @DisplayName("DTO 변환")
    void withDto() {
        long count = measure(() -> {
            friendshipRepository.findFriendDtosByUserId(TEST_USER_ID);
        });

        System.out.println("DTO 변환 쿼리 수 = " + count);
    }

    @Test
    @DisplayName("조회 테스트")
    void existTest() throws JsonProcessingException {
        Pageable pageable = PageRequest.of(0, 10);
        friendshipService.getFriendsList(1L, pageable);

        System.out.println("성공");
    }

    @Test
    @DisplayName("친구 신청 테스트")
    void friendRequestTest() throws JsonProcessingException {
        friendRequestService.createFriendRequest(946L, 947L);
        System.out.println("성공");
    }

    @Test
    @DisplayName("친구 테스트")
    void friendTest() throws JsonProcessingException {
        friendRequestService.processRequest(3L, 947L, RequestStatus.A);
        System.out.println("성공");
    }

    @Test
    @DisplayName("merge 경로 - 저장 전 SELECT 발생")
    void saveViaMerge() {
        long count = measure(() -> {
            User a = em.find(User.class, 900L);   // measure 안에서 조회
            User b = em.find(User.class, 901L);   // 친구 관계 없는 유저
            long base = stats.getPrepareStatementCount();

            em.merge(Friendship.createFriendship(a, b));
            em.flush();

            System.out.println("merge 구간만 = " + (stats.getPrepareStatementCount() - base));
        });
        System.out.println("전체 = " + count);
    }

    @Test
    @DisplayName("persist 경로 - INSERT만 발생")
    void saveViaPersist() {
        User a = em.find(User.class, 996L);
        User b = em.find(User.class, 997L);

        em.flush();
        long before = stats.getPrepareStatementCount();

        em.persist(Friendship.createFriendship(a, b));
        em.flush();

        System.out.println("persist 경로 쿼리 수 = " + (stats.getPrepareStatementCount() - before));
    }
}
