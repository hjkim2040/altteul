package com.c203.altteulbe.friend.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.c203.altteulbe.common.utils.RedisKeys;
import com.c203.altteulbe.friend.web.dto.response.FriendRequestResponseDto;
import com.c203.altteulbe.friend.web.dto.response.FriendResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendRedisService {

	private final RedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper;

	// 친구 리스트 조회
	public Optional<List<FriendResponseDto>> getCachedFriendList(Long userId) {
		String key = RedisKeys.getFriendListKey(userId);
		String jsonFriendList = redisTemplate.opsForValue().get(key);
		if (jsonFriendList == null) {
			return Optional.empty(); // 캐시 없음
		}
		try {
			return Optional.of(objectMapper.readValue(jsonFriendList, new TypeReference<>() {
			}));
		} catch (JsonProcessingException e) {
			log.warn("친구 목록 캐시 역직렬화 실패, 캐시 미스로 처리: userId={}", userId, e);
			redisTemplate.delete(key);   // 깨진 캐시는 삭제
			return Optional.empty();
		}
	}

	// 친구 리스트 저장
	public void setFriendList(Long userId, List<FriendResponseDto> friendList) {
		try {
			String jsonFriendList = objectMapper.writeValueAsString(friendList);
			redisTemplate.opsForValue().set(RedisKeys.getFriendListKey(userId),
					jsonFriendList, 30, TimeUnit.MINUTES);
		} catch (JsonProcessingException e) {
			log.warn("친구 목록 캐시 저장 실패: userId={}", userId, e);
		}
	}

	// 친구 리스트 삭제
	public void invalidateFriendList(Long userId) {
		String key = RedisKeys.getFriendListKey(userId);
		redisTemplate.delete(key);
	}

	// 친구 관계 확인
	public boolean checkFriendRelation(Long userId1, Long userId2) {
		String key = RedisKeys.getFriendRelationKey(userId1);
		String value = redisTemplate.opsForValue().get(key);
		if (value == null) {
			return false;
		}
		try {
			List<Long> friendListIds = objectMapper.readValue(value, new TypeReference<>() {
			});
			return friendListIds.contains(userId2); // 캐시에 없으면 기본적으로 false 반환
		} catch (JsonProcessingException e) {
			log.warn("친구 관계 캐시 역직렬화 실패, DB 검증으로 위임: userId={}", userId1, e);
			redisTemplate.delete(key);
			return false;
		}


	}

	// 친구 요청 목록 캐시 저장
	public void cacheFriendRequests(Long userId, List<FriendRequestResponseDto> requests) {
		String key = RedisKeys.geFriendRequestKey(userId);
		try {
			String jsonFriendRequestList = objectMapper.writeValueAsString(requests);
			redisTemplate.opsForValue().set(key, jsonFriendRequestList, 30, TimeUnit.MINUTES);
		} catch (JsonProcessingException e) {
			log.warn("친구 요청 캐시 저장 실패: userId={}", userId, e);
		}
	}

	// 친구 요청 목록 캐시 조회
	public Optional<List<FriendRequestResponseDto>> getCachedFriendRequests(Long userId) {
		String key = RedisKeys.geFriendRequestKey(userId);
		String cachedData = redisTemplate.opsForValue().get(key);
		if (cachedData == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(cachedData, new TypeReference<>() {
			}));
		} catch (JsonProcessingException e) {
			log.warn("친구 요청 캐시 역직렬화 실패, 캐시 미스로 처리: userId={}", userId, e);
			redisTemplate.delete(key);
			return Optional.empty();
		}

	}

	// 친구 요청 캐시 무효화
	public void invalidateFriendRequests(Long userId) {
		String key = RedisKeys.geFriendRequestKey(userId);
		redisTemplate.delete(key);
	}

	// 캐시 무효화 처리
	public void invalidateCaches(Long userId, Long friendId) {
		// 친구 요청 목록 캐시 삭제
		invalidateFriendRequests(userId);

		// 친구 리스트 캐시 삭제
		invalidateFriendList(userId);
		invalidateFriendList(friendId);
	}
}
