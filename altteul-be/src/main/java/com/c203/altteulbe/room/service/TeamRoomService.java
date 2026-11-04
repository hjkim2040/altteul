package com.c203.altteulbe.room.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.c203.altteulbe.common.annotation.DistributedLock;
import com.c203.altteulbe.common.dto.BattleType;
import com.c203.altteulbe.common.exception.BusinessException;
import com.c203.altteulbe.common.utils.RedisKeys;
import com.c203.altteulbe.friend.persistent.entity.FriendId;
import com.c203.altteulbe.friend.persistent.repository.FriendshipRepository;
import com.c203.altteulbe.user.service.UserStatusService;
import com.c203.altteulbe.game.persistent.entity.Game;
import com.c203.altteulbe.game.persistent.entity.problem.Problem;
import com.c203.altteulbe.game.persistent.entity.problem.Testcase;
import com.c203.altteulbe.game.persistent.repository.game.GameRepository;
import com.c203.altteulbe.game.persistent.repository.problem.ProblemRepository;
import com.c203.altteulbe.game.persistent.repository.testcase.TestcaseRepository;
import com.c203.altteulbe.game.service.exception.GameCannotStartException;
import com.c203.altteulbe.game.service.exception.NotEnoughUserException;
import com.c203.altteulbe.game.service.exception.ProblemNotFoundException;
import com.c203.altteulbe.game.web.dto.response.GameStartForProblemDto;
import com.c203.altteulbe.game.web.dto.response.GameStartForTestcaseDto;
import com.c203.altteulbe.room.persistent.entity.TeamRoom;
import com.c203.altteulbe.room.persistent.entity.UserTeamRoom;
import com.c203.altteulbe.room.persistent.repository.team.TeamRoomRedisRepository;
import com.c203.altteulbe.room.persistent.repository.team.TeamRoomRepository;
import com.c203.altteulbe.room.persistent.repository.team.UserTeamRoomRepository;
import com.c203.altteulbe.room.service.exception.AlreadyExpiredInviteException;
import com.c203.altteulbe.room.service.exception.AlreadyInviteException;
import com.c203.altteulbe.room.service.exception.CannotLeaveRoomException;
import com.c203.altteulbe.room.service.exception.CannotMatchCancelException;
import com.c203.altteulbe.room.service.exception.CannotMatchingException;
import com.c203.altteulbe.room.service.exception.DuplicateRequestException;
import com.c203.altteulbe.room.service.exception.DuplicateRoomEntryException;
import com.c203.altteulbe.room.service.exception.NotRoomLeaderException;
import com.c203.altteulbe.room.service.exception.OnlyFriendCanInviteException;
import com.c203.altteulbe.room.service.exception.OnlyOnlineFriendCanInviteException;
import com.c203.altteulbe.room.service.exception.OnlyWaitingRoomCanInviteException;
import com.c203.altteulbe.room.service.exception.RoomFullException;
import com.c203.altteulbe.room.service.exception.RoomNotInWaitingStateException;
import com.c203.altteulbe.room.service.exception.UserNotInRoomException;
import com.c203.altteulbe.room.web.dto.request.InviteTeamAnswerRequestDto;
import com.c203.altteulbe.room.web.dto.request.InviteTeamRequestDto;
import com.c203.altteulbe.room.web.dto.response.RoomEnterResponseDto;
import com.c203.altteulbe.room.web.dto.response.RoomLeaveResponseDto;
import com.c203.altteulbe.room.web.dto.response.TeamMatchResponseDto;
import com.c203.altteulbe.room.web.dto.response.TeamRoomGameStartResponseDto;
import com.c203.altteulbe.room.web.dto.response.TeamRoomInviteResponseDto;
import com.c203.altteulbe.user.persistent.entity.User;
import com.c203.altteulbe.user.persistent.repository.UserRepository;
import com.c203.altteulbe.user.service.exception.NotFoundUserException;
import com.c203.altteulbe.user.web.dto.response.UserInfoResponseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * 팀전 대기방 입장 처리
 * 동일 유저의 중복 요청 방지 및 동시성 제어를 위해 userId를 키로 갖는 락을 생성
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamRoomService {
	private final RedisTemplate<String, String> redisTemplate;
	private final UserRepository userRepository;
	private final UserStatusService userStatusService;
	private final FriendshipRepository friendshipRepository;
	private final TeamRoomRedisRepository teamRoomRedisRepository;
	private final TeamRoomRepository teamRoomRepository;
	private final UserTeamRoomRepository userTeamRoomRepository;
	private final ProblemRepository problemRepository;
	private final TestcaseRepository testcaseRepository;
	private final GameRepository gameRepository;
	private final RoomWebSocketService roomWebSocketService;
	private final RoomValidator validator;

	/**
	 * 팀전 대기방 입장 처리
	 */
	public RoomEnterResponseDto enterTeamRoom(Long userId) {
		User user = userRepository.findByUserId(userId)
			.orElseThrow(() -> new NotFoundUserException());

		// 유저가 이미 방에 존재하는지 검증
		if (validator.isUserInAnyRoom(userId, BattleType.S)) {
			throw new DuplicateRoomEntryException();
		}
		if (validator.isUserInAnyRoom(userId, BattleType.T)) {
			throw new DuplicateRoomEntryException();
		}

		// 입장 가능한 대기방 조회
		Long existingRoomId = teamRoomRedisRepository.getAvailableRoom();

		// 입장 가능한 대기방이 있는 경우 유저 저장 (API 응답 + WebSocket 전송)
		if (existingRoomId != null) {
			RoomEnterResponseDto responseDto = teamRoomRedisRepository.insertUserToExistingRoom(existingRoomId, user);

			// 웹소켓 메시지 브로드캐스트
			roomWebSocketService.sendWebSocketMessage(responseDto.getRoomId().toString(),
				"ENTER", responseDto, BattleType.T);
			return responseDto;
		}

		// 입장 가능한 대기방이 없는 경우 대기방 생성 후 유저 저장 (API 응답)
		RoomEnterResponseDto responseDto = teamRoomRedisRepository.createRedisTeamRoom(user);
		return responseDto;
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/**
	 * 팀전 대기방 퇴장 처리
	 */
	// 정식으로 요청했을 경우의 퇴장 처리
	@DistributedLock(key = "#roomId")
	public void leaveTeamRoom(Long roomId, Long userId) {
		removeUserFromTeamRoom(roomId, userId, true);
	}

	// 웹소켓 연결이 끊겼을 경우의 퇴장 처리
	public void webSocketDisconnectLeave(Long roomId, Long userId) {
		removeUserFromTeamRoom(roomId, userId, false);
	}

	public void removeUserFromTeamRoom(Long roomId, Long userId, boolean validateRoomStatus) {

		// 퇴장하는 유저 정보 조회
		User user = userRepository.findByUserId(userId)
			.orElseThrow(() -> new NotFoundUserException());

		if (validateRoomStatus) {
			// 유저가 방에 속했는지 검증
			if (!validator.isUserInThisRoom(userId, roomId, BattleType.T)) {
				throw new UserNotInRoomException();
			}

			// 방 상태 확인
			String status = teamRoomRedisRepository.getRoomStatus(roomId);
			if (!"waiting".equals(status)) {
				throw new CannotLeaveRoomException();
			}
		}

		// Redis에서 퇴장하는 유저 삭제
		String roomUsersKey = RedisKeys.TeamRoomUsers(roomId);
		redisTemplate.opsForList().remove(roomUsersKey, 0, userId.toString());
		redisTemplate.delete(RedisKeys.userTeamRoom(userId));

		// 퇴장 후 방에 남은 유저가 없는 경우 관련 데이터 삭제
		List<String> remainingUserIds = redisTemplate.opsForList().range(roomUsersKey, 0, -1);
		if (remainingUserIds == null || remainingUserIds.isEmpty()) {
			teamRoomRedisRepository.deleteRedisTeamRoom(roomId);
			return;
		}

		// 방장 조회
		Long leaderId = Long.parseLong(remainingUserIds.get(0));

		// 떠나는 유저 정보 조회
		UserInfoResponseDto leftUserDto = UserInfoResponseDto.fromEntity(user);

		// 남은 유저들 정보 조회
		List<User> remainingUsers = getUserByIds(remainingUserIds);

		// 조회된 users를 userId 기준으로 Map 변환
		Map<Long, User> userMap = remainingUsers.stream()
			.collect(Collectors.toMap(User::getUserId, Function.identity()));

		// Redis 순서대로 정렬
		List<User> sortedUsers = remainingUserIds.stream()
			.map(id -> userMap.get(Long.parseLong(id)))  // Redis 순서 유지
			.collect(Collectors.toList());

		// DTO 변환
		List<UserInfoResponseDto> remainingUserDtos = UserInfoResponseDto.fromEntities(sortedUsers);

		RoomLeaveResponseDto responseDto = RoomLeaveResponseDto.toResponse(
			roomId, leaderId, leftUserDto, remainingUserDtos
		);
		// WebSocket 메시지 브로드캐스트
		roomWebSocketService.sendWebSocketMessage(roomId.toString(), "LEAVE", responseDto, BattleType.T);
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/**
	 * 팀전 매칭 시작
	 */
	@DistributedLock(key = "#roomId")
	public void startTeamMatch(Long roomId, Long leaderId) {

		userRepository.findByUserId(leaderId)
			.orElseThrow(() -> new NotFoundUserException());

		// 유저가 방에 속했는지 검증
		if (!validator.isUserInThisRoom(leaderId, roomId, BattleType.T)) {
			throw new UserNotInRoomException();
		}

		// 방장 여부, 인원 수 충족 여부, 대기 중 여부 검증
		if (!validator.isRoomLeader(roomId, leaderId, BattleType.T))
			throw new NotRoomLeaderException();
		if (!validator.isEnoughUsers(roomId, BattleType.T))
			throw new NotEnoughUserException();
		if (!teamRoomRedisRepository.getRoomStatus(roomId).equals("waiting"))
			throw new CannotMatchingException();

		// matching 상태로 변경
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(roomId), "matching");

		// 매칭 중 상태 전송 후 TEAM_MATCHING_ROOMS에 추가 → 스케줄러가 인식 가능
		roomWebSocketService.sendWebSocketMessageWithNote(roomId.toString(), "MATCHING", "배틀할 상대 팀을 찾고 있습니다.",
			BattleType.T);
		redisTemplate.opsForZSet().add(RedisKeys.TEAM_MATCHING_ROOMS, roomId.toString(), System.currentTimeMillis());

		log.info("팀전 매칭 시작 : roomId = {}", roomId);
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/*
	 * 스케줄러가 매칭할 팀을 찾은 후 실행되는 작업
	 */
	/*public void afterTeamMatch(String roomId1, String roomId2) {

		// 방 상태 변경
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(Long.valueOf(roomId1)), "matched");
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(Long.valueOf(roomId2)), "matched");

		// Redis에서 두 팀을 매칭 중 상태에서 제거
		redisTemplate.opsForZSet().remove(RedisKeys.TEAM_MATCHING_ROOMS, roomId1);
		redisTemplate.opsForZSet().remove(RedisKeys.TEAM_MATCHING_ROOMS, roomId2);

		String matchId = generateMatchId(roomId1, roomId2);
		Map<String, String> matchIdPayload = Map.of("matchId", matchId);

		// 매칭된 방의 matchId를 Redis에 저장
		redisTemplate.opsForValue().set(RedisKeys.TeamMatchId(Long.valueOf(roomId1)), matchId);
		redisTemplate.opsForValue().set(RedisKeys.TeamMatchId(Long.valueOf(roomId2)), matchId);

		// 해당 메시지를 전송받으면 "/sub/team/room/{matchId}"를 구독시켜야 함
		roomWebSocketService.sendWebSocketMessage(roomId1, "MATCHED", matchIdPayload, BattleType.T);
		roomWebSocketService.sendWebSocketMessage(roomId2, "MATCHED", matchIdPayload, BattleType.T);
		log.info("matchId = {}", matchId);

		// 문제 및 테스트케이스 조회
		List<Long> problemIds = problemRepository.findAllProblemIds();
		if (problemIds.isEmpty()) {
			throw new ProblemNotFoundException();
		}
		Long randomProblemId = problemIds.get(new Random().nextInt(problemIds.size()));
		Problem problemEntity = problemRepository.findById(randomProblemId)
			.orElseThrow(ProblemNotFoundException::new);

		List<Testcase> testcaseEntities = testcaseRepository.findTestcasesByProblemId(problemEntity.getId());

		GameStartForProblemDto problem = GameStartForProblemDto.from(problemEntity);
		List<GameStartForTestcaseDto> testcases = testcaseEntities.stream()
			.map(GameStartForTestcaseDto::from)
			.collect(Collectors.toList());

		// 이후 DB에 저장하기 위해 문제 pk를 redis에 저장
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomProblem(matchId), String.valueOf(randomProblemId));

		// 각 팀의 유저 정보를 가져오는 메소드 호출
		TeamMatchResponseDto teamMatchDto = getTeamMatchResponseDto(Long.parseLong(roomId1), Long.parseLong(roomId2),
			problem, testcases);

		// 두 팀의 정보를 websocket으로 전송 후 카운팅 시작
		roomWebSocketService.sendWebSocketMessage(matchId, "COUNTING_READY", teamMatchDto, BattleType.T);
		startCountingTeam(Long.parseLong(roomId1), Long.parseLong(roomId2), matchId);
	}*/

	public void afterTeamMatch(String roomId1, String roomId2) {
		// 방 상태 변경
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(Long.valueOf(roomId1)), "matched");
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(Long.valueOf(roomId2)), "matched");

		// Redis에서 두 팀을 매칭 중 상태에서 제거
		redisTemplate.opsForZSet().remove(RedisKeys.TEAM_MATCHING_ROOMS, roomId1);
		redisTemplate.opsForZSet().remove(RedisKeys.TEAM_MATCHING_ROOMS, roomId2);

		String matchId = generateMatchId(roomId1, roomId2);
		Map<String, String> matchIdPayload = Map.of("matchId", matchId);

		// 매칭된 방의 matchId를 Redis에 저장
		redisTemplate.opsForValue().set(RedisKeys.TeamMatchId(Long.valueOf(roomId1)), matchId);
		redisTemplate.opsForValue().set(RedisKeys.TeamMatchId(Long.valueOf(roomId2)), matchId);

		// MATCHED 이벤트 전송
		roomWebSocketService.sendWebSocketMessage(roomId1, "MATCHED", matchIdPayload, BattleType.T);
		roomWebSocketService.sendWebSocketMessage(roomId2, "MATCHED", matchIdPayload, BattleType.T);
		log.info("matchId = {}", matchId);

		// 문제 및 테스트케이스 조회
		List<Long> problemIds = problemRepository.findAllProblemIds();
		if (problemIds.isEmpty()) {
			throw new ProblemNotFoundException();
		}
		Long randomProblemId = problemIds.get(new Random().nextInt(problemIds.size()));
		Problem problemEntity = problemRepository.findById(randomProblemId)
			.orElseThrow(ProblemNotFoundException::new);

		List<Testcase> testcaseEntities = testcaseRepository.findTestcasesByProblemId(problemEntity.getId());

		GameStartForProblemDto problem = GameStartForProblemDto.from(problemEntity);
		List<GameStartForTestcaseDto> testcases = testcaseEntities.stream()
			.map(GameStartForTestcaseDto::from)
			.collect(Collectors.toList());

		// 이후 DB에 저장하기 위해 문제 pk를 redis에 저장
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomProblem(matchId), String.valueOf(randomProblemId));

		// 각 팀의 유저 정보를 가져오는 메소드 호출
		TeamMatchResponseDto teamMatchDto = getTeamMatchResponseDto(Long.parseLong(roomId1), Long.parseLong(roomId2), problem, testcases);

		// 2초 후 COUNTING_READY 이벤트 전송
		CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
			roomWebSocketService.sendWebSocketMessage(matchId, "COUNTING_READY", teamMatchDto, BattleType.T);
			startCountingTeam(Long.parseLong(roomId1), Long.parseLong(roomId2), matchId);
		});
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/*
	 * 두 팀에 대한 카운팅 시작
	 */
	private void startCountingTeam(Long roomId1, Long roomId2, String matchId) {
		if (!validator.isRoomMatched(roomId1))
			throw new GameCannotStartException();
		if (!validator.isRoomMatched(roomId2))
			throw new GameCannotStartException();
		if (!validator.isEnoughUsers(roomId1, BattleType.T))
			throw new NotEnoughUserException();
		if (!validator.isEnoughUsers(roomId2, BattleType.T))
			throw new NotEnoughUserException();

		// Redis에서 두 팀을 대기 중 상태에서 제거
		redisTemplate.opsForZSet().remove(RedisKeys.TEAM_WAITING_ROOMS, roomId1.toString());
		redisTemplate.opsForZSet().remove(RedisKeys.TEAM_WAITING_ROOMS, roomId2.toString());

		// 두 팀의 상태를 counting으로 변경
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(roomId1), "counting");
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(roomId2), "counting");

		// 카운트다운 시작 → Scheduler가 인식
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomCountdown(matchId), "10");
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/**
	 * 팀전 게임 시작 처리
	 */
	@Transactional
	public void startGameAfterCountDown(String matchId, Long roomId1, Long roomId2) {
		// 최소 인원 수 검증
		if (!validator.isEnoughUsers(roomId1, BattleType.T)) {
			roomWebSocketService.sendWebSocketMessageWithNote(matchId, "COUNTING_CANCEL", "최소 인원 수가 미달되었습니다.",
				BattleType.T);
			return;
		}
		if (!validator.isEnoughUsers(roomId2, BattleType.T)) {
			roomWebSocketService.sendWebSocketMessageWithNote(matchId, "COUNTING_CANCEL", "최소 인원 수가 미달되었습니다.",
				BattleType.T);
			return;
		}

		// redis에 저장된 problem Id를 조회하여 Game 저장 시 사용
		String problemId = (redisTemplate.opsForValue().get(RedisKeys.TeamRoomProblem(matchId)));
		Problem problemEntity = problemRepository.findById(Long.valueOf(problemId))
			.orElseThrow(() -> new ProblemNotFoundException());

		redisTemplate.delete(RedisKeys.TeamRoomProblem(matchId));

		// DB에 Game 저장
		Game game = Game.create(problemEntity, BattleType.T);
		gameRepository.save(game);

		// DB에 TeamRoom 저장
		TeamRoom teamRoom1 = TeamRoom.create(game);
		TeamRoom savedTeam1 = teamRoomRepository.save(teamRoom1);
		saveUserTeamRooms(roomId1, teamRoom1);

		TeamRoom teamRoom2 = TeamRoom.create(game);
		TeamRoom savedTeam2 = teamRoomRepository.save(teamRoom2);
		saveUserTeamRooms(roomId2, teamRoom2);

		// websocket으로 전송할 데이터 준비
		RoomEnterResponseDto team1Dto = getRoomEnterResponseDtoForDB(roomId1, savedTeam1.getId());
		RoomEnterResponseDto team2Dto = getRoomEnterResponseDtoForDB(roomId2, savedTeam2.getId());

		TeamRoomGameStartResponseDto responseDto = TeamRoomGameStartResponseDto.from(
			game.getId(), team1Dto, team2Dto);
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(roomId1), "gaming");
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(roomId2), "gaming");

		roomWebSocketService.sendWebSocketMessage(matchId, "GAME_START", responseDto, BattleType.T);
		log.info("각 팀에게 게임 시작 메시지 전송 완료");
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Redis에서 유저 ID를 조회하고, DB에 UserTeamRoom을 저장하는 메소드
	 */
	@Transactional
	public void saveUserTeamRooms(Long roomId, TeamRoom teamRoom) {

		redisTemplate.opsForValue().set(RedisKeys.getRoomDbId(roomId), teamRoom.getId().toString());
		redisTemplate.opsForValue().set(RedisKeys.getRoomRedisId(teamRoom.getId()), roomId.toString());

		// Redis에서 유저 ID 조회
		String roomUsersKey = RedisKeys.TeamRoomUsers(roomId);
		List<String> userIds1 = redisTemplate.opsForList().range(roomUsersKey, 0, -1);

		List<Long> userIdList = userIds1.stream().map(Long::parseLong).collect(Collectors.toList());
		List<User> usersFromDb = userRepository.findByUserIdIn(userIdList);

		// 조회된 User들을 Map으로 변환 (ID → User)
		Map<Long, User> userMap = usersFromDb.stream()
			.collect(Collectors.toMap(User::getUserId, user -> user));

		// userIds1의 순서대로 User 리스트 정렬
		List<User> users = userIdList.stream()
			.map(userMap::get)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());

		// DB에 UserTeamRoom 저장
		for (int i = 0; i < users.size(); i++) {
			User user = users.get(i);
			UserTeamRoom userTeamRoom = UserTeamRoom.create(teamRoom, user, i);
			userTeamRoomRepository.save(userTeamRoom);
		}
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/**
	 * 매칭 취소 처리
	 */
	@DistributedLock(key = "#roomId")
	public void cancelTeamMatch(Long roomId, Long userId) {

		userRepository.findByUserId(userId).orElseThrow(() -> new NotFoundUserException());

		// 방에 존재하는지 확인
		if (!validator.isUserInThisRoom(userId, roomId, BattleType.T)) {
			throw new UserNotInRoomException();
		}

		String status = teamRoomRedisRepository.getRoomStatus(roomId);

		// 비정상적인 호출에 대한 예외 처리
		if ("waiting".equals(status) || "counting".equals(status) || "gaming".equals(status)) {
			throw new BusinessException("잘못된 요청입니다", HttpStatus.BAD_REQUEST);
		}

		// 중복 취소 요청 처리
		if ("cancelling".equals(status)) {
			throw new DuplicateRequestException();
		}

		// 매칭 팀을 찾은 후에는 취소 불가능
		if ("matched".equals(status)) {
			roomWebSocketService.sendWebSocketMessageWithNote(String.valueOf(roomId), "MATCH_CANCEL_FAIL",
				"이미 매칭된 팀이 있어 취소할 수 없습니다.", BattleType.T);
			throw new CannotMatchCancelException();
		}

		// 방 상태를 매칭 취소로 변경 → 매칭 스케줄러가 인식하여 afterTeamMatchCancel 실헹
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(roomId), "cancelling");
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/*
	 * 매칭 취소 후 작업
	 */
	public void afterTeamMatchCancel(String roomId) {
		// 방 상태를 다시 waiting으로 전환
		redisTemplate.opsForValue().set(RedisKeys.TeamRoomStatus(Long.valueOf(roomId)), "waiting");
		RoomEnterResponseDto responseDto = getRoomEnterResponseDto(Long.valueOf(roomId));

		// 취소가 완료된 경우 팀에게 매칭 취소 이벤트 전송
		roomWebSocketService.sendWebSocketMessage(roomId, "MATCH_CANCEL_SUCCESS", responseDto, BattleType.T);
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/*
	 * 팀 초대 처리
	 */
	public void inviteFriendToTeam(InviteTeamRequestDto requestDto, Long userId) {

		Long roomId = requestDto.getRoomId();
		Long friendId = requestDto.getInviteeId();

		User inviter = userRepository.findByUserId(userId).orElseThrow(() -> new NotFoundUserException());
		User invitee = userRepository.findById(friendId).orElseThrow(() -> new NotFoundUserException());

		// 친구 관계 확인
		if (!friendshipRepository.existsById(new FriendId(userId, friendId))) {
			throw new OnlyFriendCanInviteException();
		}

		// 초대받은 친구가 온라인 상태인지 확인
		if (!userStatusService.isUserOnline(friendId)) {
			throw new OnlyOnlineFriendCanInviteException();
		}

		// 요청을 보낸 유저가 방에 존재하는지 확인
		String userRoomKey = RedisKeys.userTeamRoom(userId);
		String userRoomId = redisTemplate.opsForValue().get(userRoomKey);
		if (!roomId.toString().equals(userRoomId)) {
			throw new UserNotInRoomException();
		}

		// 초대받은 유저가 이미 다른 방에 있는지 확인 (우선 다른 방에 있는 경우에는 초대할 수 없도록 하였음)
		String friendRoomKey = RedisKeys.userTeamRoom(friendId);
		if (Boolean.TRUE.equals(redisTemplate.hasKey(friendRoomKey))) {
			throw new DuplicateRoomEntryException();
		}

		// 방의 상태가 대기 중인지 확인
		if (!validator.isRoomWaiting(roomId, BattleType.T)) {
			throw new OnlyWaitingRoomCanInviteException();
		}

		// 수용 가능 여부 확인
		String roomUsersKey = RedisKeys.TeamRoomUsers(roomId);
		Long userCount = redisTemplate.opsForList().size(roomUsersKey);

		if (userCount >= BattleType.T.getMaxUsers()) {
			throw new RoomFullException();
		}

		// 중복 초대 여부 확인
		String inviteKey = RedisKeys.inviteInfo(String.valueOf(roomId), String.valueOf(friendId));
		if (Boolean.TRUE.equals(redisTemplate.hasKey(inviteKey))) {
			throw new AlreadyInviteException();
		}

		// 초대 정보를 redis에 저장 (TTL 적용 - 10분)
		redisTemplate.opsForValue().set(inviteKey, "pending", 10, TimeUnit.MINUTES);

		// 초대한 유저에게 초대 성공 메시지 전송
		roomWebSocketService.sendWebSocketMessageWithNote("/sub/invite/" + userId, "INVITE_REQUEST_SUCCESS",
			"초대를 완료했습니다.");

		// 초대 받은 유저에게 초대 관련 정보 전송
		TeamRoomInviteResponseDto responseDto = TeamRoomInviteResponseDto.create(roomId,
			inviter.getNickname());

		roomWebSocketService.sendWebSocketMessage("/sub/invite/" + friendId, "INVITE_REQUEST_RECEIVED", responseDto);
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/*
	 * 팀 초대 수락 및 거절 처리
	 */
	public RoomEnterResponseDto handleInviteReaction(InviteTeamAnswerRequestDto requestDto, Long friendId) {

		User inviter = userRepository.findByNickname(requestDto.getNickname())
			.orElseThrow(() -> new NotFoundUserException());
		Long userId = inviter.getUserId();
		Long roomId = requestDto.getRoomId();
		boolean accepted = requestDto.isAccepted();

		String inviteKey = RedisKeys.inviteInfo(String.valueOf(roomId), String.valueOf(friendId));
		String redisValue = (String)redisTemplate.opsForValue().get(inviteKey);

		if (redisValue == null) {
			throw new AlreadyExpiredInviteException();
		}

		// 초대 정보 삭제
		redisTemplate.delete(inviteKey);

		// 수락한 경우
		if (accepted) {
			// 방의 상태가 대기 중인지 확인
			if (!validator.isRoomWaiting(roomId, BattleType.T)) {
				throw new RoomNotInWaitingStateException();
			}

			// 수용 가능 여부 확인
			String roomUsersKey = RedisKeys.TeamRoomUsers(roomId);
			Long userCount = redisTemplate.opsForList().size(roomUsersKey);

			if (userCount >= BattleType.T.getMaxUsers()) {
				throw new RoomFullException();
			}

			User user = userRepository.findByUserId(friendId).orElseThrow(() -> new NotFoundUserException());
			RoomEnterResponseDto responseDto = teamRoomRedisRepository.insertUserToExistingRoom(roomId, user);

			// 초대한 유저에게 수락 사실 전송, 초대받은 유저에게 수락 정상 처리 사실 전송
			roomWebSocketService.sendWebSocketMessageWithNote("/sub/invite/" + userId, "INVITE_ACCEPTED",
				"초대를 수락했습니다.");

			// 방에 있는 모든 유저들에게 websocket으로 유저 정보 전송 (+ 초대된 유저 정보 포함)
			roomWebSocketService.sendWebSocketMessage(String.valueOf(roomId), "ENTER", responseDto, BattleType.T);

			// 초대 받은 유저에게 방에 있는 유저들에 대한 정보 전송
			return responseDto;

		} else {
			// 거절한 경우
			roomWebSocketService.sendWebSocketMessageWithNote("/sub/invite/" + userId, "INVITE_REJECTED",
				"초대를 거절했습니다.");
			roomWebSocketService.sendWebSocketMessageWithNote("/sub/invite/" + friendId, "INVITE_REJECTED",
				"초대 거절 요청이 정상 처리되었습니다.");
			return null;
		}
	}

	//---------------------------------------------------------------------------------------------------------------------------

	/**
	 * 각 팀의 유저 정보를 조회하여 TeamMatchResponseDto로 변환하는 메소드
	 */
	private TeamMatchResponseDto getTeamMatchResponseDto(Long roomId1, Long roomId2,
		GameStartForProblemDto problem,
		List<GameStartForTestcaseDto> testcases) {
		RoomEnterResponseDto responseDto1 = getRoomEnterResponseDto(roomId1);
		RoomEnterResponseDto responseDto2 = getRoomEnterResponseDto(roomId2);
		return TeamMatchResponseDto.toDto(responseDto1, responseDto2, problem, testcases);
	}

	private RoomEnterResponseDto getRoomEnterResponseDto(Long roomId) {
		String roomUsersKey = RedisKeys.TeamRoomUsers(roomId);
		String leaderId = redisTemplate.opsForList().index(roomUsersKey, 0);
		List<String> userIds = redisTemplate.opsForList().range(roomUsersKey, 0, -1);
		return teamRoomRedisRepository.convertToRoomEnterResponseDto(roomId, leaderId, userIds);
	}

	/**
	 * 특정 팀의 유저 정보를 조회하여 RoomEnterResponseDto로 변환하는 메소드
	 */
	private RoomEnterResponseDto getRoomEnterResponseDtoForDB(Long roomId, Long savedId) {
		String roomUsersKey = RedisKeys.TeamRoomUsers(roomId);
		String leaderId = redisTemplate.opsForList().index(roomUsersKey, 0);
		List<String> userIds = redisTemplate.opsForList().range(roomUsersKey, 0, -1);
		return teamRoomRedisRepository.convertToRoomEnterResponseDto(savedId, leaderId, userIds);
	}

	/**
	 * 매칭되는 순간부터 두 팀을 함께 관리하기 위한 pk 생성 메소드
	 */
	public String generateMatchId(String team1Id, String team2Id) {
		List<String> teamIds = Arrays.asList(team1Id, team2Id);
		Collections.sort(teamIds);
		return teamIds.get(0) + "-" + teamIds.get(1);
	}

	// userId 리스트로 User 엔티티 조회
	private List<User> getUserByIds(List<String> userIds) {
		return userRepository.findByUserIdIn(
			userIds.stream().map(Long::parseLong).collect(Collectors.toList())
		);
	}
}
