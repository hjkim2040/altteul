package com.c203.altteulbe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.stream.IntStream;

/**
 * 포트폴리오 성능 측정용 테스트.
 * 프로젝트 종료 후 개선 전후 쿼리 수를 재현 측정하기 위해 작성.
 * 실행 전 DummyDataLoader로 더미 데이터를 먼저 적재해야 함.
 */

@SpringBootTest
@ActiveProfiles("dev")
public class DummyDataLoader {
    @Autowired
    private JdbcTemplate jdbc;

    private static final int USER_COUNT = 1000;
    private static final long TARGET_USER = 1L;   // 측정 대상
    private static final int TARGET_FRIENDS = 200; // 이 유저의 친구 수
    private static final int CHATROOM_COUNT = 500;
    private static final int MESSAGE_COUNT = 10_000;

    @Test
    void load() {
        clean();
        insertTiers();
        insertUsers();
        insertFriendships();
        insertChatrooms();
        insertUserChatRooms();
        insertMessages();
        System.out.println("완료: user=" + count("`user`")
                + ", friendship=" + count("friendship")
                + ", chat_message=" + count("chat_message"));
    }

    private void clean() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        List.of("chat_message", "user_chat_room", "chatroom",
                        "friendship", "friend_request",
                        "today_ranking", "ranking_history", "tier_history", "point_history",
                        "`user`", "tier")
                .forEach(t -> {
                    try { jdbc.execute("TRUNCATE TABLE " + t); }
                    catch (Exception e) { System.out.println("skip: " + t); }
                });
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private void insertTiers() {
        jdbc.execute("""
        INSERT INTO tier (tier_id, tier_name, min_point, max_point, created_at) VALUES
          (1,'BRONZE',0,199,NOW()),
          (2,'SILVER',200,399,NOW()),
          (3,'GOLD',400,599,NOW()),
          (4,'PLATINUM',600,799,NOW()),
          (5,'DIAMOND',800,999,NOW())
        """);
    }

    private void insertUsers() {
        String sql = """
            INSERT INTO `user` (user_id, username, nickname, profile_img,
                                main_lang, provider, user_status, ranking_point,
                                tier_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        List<Object[]> batch = IntStream.rangeClosed(1, USER_COUNT)
                .mapToObj(i -> new Object[]{
                        (long) i, "user" + i, "닉네임" + i, "uploads/default.png",
                        i % 2 == 0 ? "JV" : "PY", "LC", "A",
                        (long) (i % 1000), (i % 5) + 1
                })
                .toList();
        jdbc.batchUpdate(sql, batch);
    }

    private void insertFriendships() {
        String sql = "INSERT INTO friendship (user_id, friend_id, created_at) VALUES (?, ?, NOW())";
        List<Object[]> batch = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 측정 대상 유저의 친구 200명 (양방향)
        for (long fid = 2; fid <= TARGET_FRIENDS + 1; fid++) {
            batch.add(new Object[]{TARGET_USER, fid});
            batch.add(new Object[]{fid, TARGET_USER});
            seen.add(TARGET_USER + ":" + fid);
            seen.add(fid + ":" + TARGET_USER);
        }

        // 나머지 랜덤 관계 500쌍
        Random r = new Random(42);
        int added = 0;
        while (added < 500) {
            long a = 300 + r.nextInt(700);
            long b = 300 + r.nextInt(700);
            if (a == b) continue;
            if (seen.contains(a + ":" + b)) continue;

            seen.add(a + ":" + b);
            seen.add(b + ":" + a);
            batch.add(new Object[]{a, b});
            batch.add(new Object[]{b, a});
            added++;
        }
        jdbc.batchUpdate(sql, batch);
    }

    private void insertChatrooms() {
        String sql = "INSERT INTO chatroom (chatroom_id, created_at) VALUES (?, NOW())";
        List<Object[]> batch = IntStream.rangeClosed(1, CHATROOM_COUNT)
                .mapToObj(i -> new Object[]{(long) i}).toList();
        jdbc.batchUpdate(sql, batch);
    }

    private void insertUserChatRooms() {
        String sql = """
            INSERT INTO user_chat_room (chatroom_chatroom_id, user_user_id, created_at)
            VALUES (?, ?, NOW())
            """;
        List<Object[]> batch = new ArrayList<>();
        for (long roomId = 1; roomId <= CHATROOM_COUNT; roomId++) {
            batch.add(new Object[]{roomId, TARGET_USER});
            batch.add(new Object[]{roomId, roomId + 1});
        }
        jdbc.batchUpdate(sql, batch);
    }

    private void insertMessages() {
        String sql = """
            INSERT INTO chat_message
                (chatroom_chatroom_id, sender_user_id, message_content, checked, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            """;
        List<Object[]> batch = new ArrayList<>();
        Random r = new Random(7);
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            long roomId = 1 + r.nextInt(CHATROOM_COUNT);
            long senderId = r.nextBoolean() ? TARGET_USER : roomId + 1;
            batch.add(new Object[]{roomId, senderId, "테스트 메시지 " + i, r.nextInt(10) > 2});
        }
        jdbc.batchUpdate(sql, batch);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
