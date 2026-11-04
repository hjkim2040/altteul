package com.c203.altteulbe.user.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.c203.altteulbe.common.utils.RedisKeys;
import com.c203.altteulbe.friend.service.exception.RedisConnectionFailException;
import com.c203.altteulbe.friend.service.exception.UserStatusUpdateFailException;
import com.c203.altteulbe.user.service.exception.NotFoundUserException;

import io.lettuce.core.RedisConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserStatusService {
	private final RedisTemplate<String, String> redisTemplate;

	// 유저 온라인 상태 설정
	public void setUserOnline(Long userId) {
		validateUserId(userId);
		try {
			String key = RedisKeys.getUserStatusKey(userId);
			redisTemplate.opsForValue().set(key, "online", 90, TimeUnit.SECONDS);
		} catch (RedisConnectionException e) {
			log.error("Redis 연결 실패", e);
			throw new RedisConnectionFailException();
		} catch (Exception e) {
			log.error("사용자 온라인 상태 설정 중 오류 발생", e);
			throw new UserStatusUpdateFailException();
		}

	}

	// 유저 오프라인 상태로 변경
	public void setUserOffline(Long userId) {
		validateUserId(userId);
		try {
			String key = RedisKeys.getUserStatusKey(userId);
			redisTemplate.delete(key);
		} catch (RedisConnectionException e) {
			log.error("Redis 연결 실패", e);
			throw new RedisConnectionFailException();
		} catch (Exception e) {
			log.error("사용자 오프라인 상태 설정 중 오류 발생", e);
			throw new UserStatusUpdateFailException();
		}
	}

	// 유저 온라인 상태 확인
	public boolean isUserOnline(Long userId) {
		validateUserId(userId);
		try {
			String key = RedisKeys.getUserStatusKey(userId);
			return redisTemplate.hasKey(key);
		} catch (RedisConnectionException e) {
			log.error("Redis 연결 실패", e);
			return false;
		} catch (Exception e) {
			log.error("사용자 상태 확인 중 오류 발생", e);
			return false;
		}
	}

	// 사용자들의 온라인 상태 한 번에 확인하기
	public Map<Long, Boolean> getBulkOnlineStatus(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}

		List<String> keys = userIds.stream()
				.map(RedisKeys::getUserStatusKey)
				.toList();

		List<String> values = redisTemplate.opsForValue().multiGet(keys);

		Map<Long, Boolean> result = new HashMap<>();
		for (int i = 0; i < userIds.size(); i++) {
			// values 자체가 null일 경우를 대비해 null 안전성 확보
			boolean isOnline = values != null && values.get(i) != null;
			result.put(userIds.get(i), isOnline);
		}
		return result;
	}

	// heartbeat용
	public void refreshOnline(Long userId) {
		validateUserId(userId);
		try {
			String key = RedisKeys.getUserStatusKey(userId);
			Boolean refreshed = redisTemplate.expire(key, 90, TimeUnit.SECONDS);
			if (!refreshed) {
				// 키가 이미 만료되어 없어진 경우 → 다시 온라인으로 설정
				log.warn("TTL이 만료되어 키가 없음. 재설정: userId={}", userId);
				setUserOnline(userId);
			}
		} catch (RedisConnectionException e) {
			log.error("Redis 연결 실패", e);
			throw new RedisConnectionFailException();
		} catch (Exception e) {
			log.error("온라인 상태 갱신 중 오류 발생", e);
			throw new UserStatusUpdateFailException();
		}
	}

	private void validateUserId(Long userId) {
		if (userId == null) {
			throw new NotFoundUserException();
		}
	}
}
