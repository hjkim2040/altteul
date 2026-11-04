package com.c203.altteulbe;

import com.c203.altteulbe.common.utils.RedisKeys;
import com.c203.altteulbe.user.service.UserStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.stream.LongStream;

/**
 * 포트폴리오 성능 측정용 테스트.
 * 프로젝트 종료 후 개선 전후 쿼리 수를 재현 측정하기 위해 작성.
 * 실행 전 DummyDataLoader로 더미 데이터를 먼저 적재해야 함.
 */

@SpringBootTest
@ActiveProfiles("dev")
public class RedisMGETPerfTest {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired private UserStatusService userStatusService;

    private static final int SIZE = 200;
    private List<Long> userIds;

    @BeforeEach
    void setUp() {
        userIds = LongStream.rangeClosed(2, SIZE + 1).boxed().toList();
        userIds.forEach(userStatusService::setUserOnline);
    }

    @AfterEach
    void tearDown() {
        userIds.forEach(userStatusService::setUserOffline);
    }

    @Test
    void compare() {
        // 워밍업
        for (int i = 0; i < 3; i++) {
            individual();
            userStatusService.getBulkOnlineStatus(userIds);
        }

        long individualTotal = 0;
        long MGETTotal = 0;
        int rounds = 10;

        for (int i = 0; i < rounds; i++) {
            long s = System.nanoTime();
            individual();
            individualTotal += System.nanoTime() - s;

            s = System.nanoTime();
            userStatusService.getBulkOnlineStatus(userIds);
            MGETTotal += System.nanoTime() - s;
        }

        System.out.println("개별 조회 평균 = " + (individualTotal / rounds / 1_000_000.0) + "ms");
        System.out.println("MGET 평균 = " + (MGETTotal / rounds / 1_000_000.0) + "ms");
    }

    private void individual() {
        userIds.forEach(id -> redisTemplate.hasKey(RedisKeys.getUserStatusKey(id)));
    }
}
