## 👤 개인 기여 — 김성찬 (Backend)

> 6인 팀 프로젝트입니다. 아래는 제가 담당한 부분이며, 원본 팀 README는 그 아래에 그대로 두었습니다.
> 
> 📄 포트폴리오: [링크](https://trusting-gooseberry-30e.notion.site/Altteul-1fff2546fed481bfb6b5d6d65c030d62?source=copy_link)

### 담당 범위
- **친구** : 요청·수락·거절, 양방향 관계 생성·삭제, 목록 조회(캐싱 + 페이징)
- **사용자 상태** : WebSocket 연결 기반 온라인/오프라인 관리, TTL 자동 만료 (DB 테이블 없이 Redis에만 저장)
- **실시간 알림** : 친구 요청·관계 변경 시 STOMP 푸시
- **채팅** : 1:1 채팅방 생성·조회, 메시지 송수신, 읽음 처리

### 코드 위치
- 기본 경로: altteul-be/src/main/java/com/c203/altteulbe/

| 도메인 | 경로                                                              |
|---|-----------------------------------------------------------------|
| 친구 | [`friend/`](https://github.com/hjkim2040/altteul/tree/d61cd60d45ada3895ea86633b28c220156b88a27/altteul-be/src/main/java/com/c203/altteulbe/friend) |
| 사용자 상태 | [`user/service/UserStatusService.java`](https://github.com/hjkim2040/altteul/blob/d61cd60d45ada3895ea86633b28c220156b88a27/altteul-be/src/main/java/com/c203/altteulbe/user/service/UserStatusService.java)                       |
| 채팅 | [`chat/`](https://github.com/hjkim2040/altteul/tree/d61cd60d45ada3895ea86633b28c220156b88a27/altteul-be/src/main/java/com/c203/altteulbe/chat)     |

### 주요 개선
| 문제 | 해결 | 결과 | 코드                                                                                                                                                                                                                                                                                                                                                                                  |
|---|---|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 친구 목록 조회 N+1 | DTO 프로젝션 | 쿼리 401회 → 1회 | [코드](https://github.com/hjkim2040/altteul/blob/3d482a276d0eea3654a6acc75a384612ba45618f/altteul-be/src/main/java/com/c203/altteulbe/friend/persistent/repository/FriendshipRepositoryImpl.java#L45-L58), [측정 테스트](https://github.com/hjkim2040/altteul/blob/c8e18fc698826f6c87ec0b24aad77c5e79d3c650/altteul-be/src/test/java/com/c203/altteulbe/FriendQueryPerfTest.java#L74-L111) |
| 온라인 상태 조회 시 Redis N회 왕복 | 개별 EXISTS → MGET | 200건 148.6ms → 1.02ms | [코드](https://github.com/hjkim2040/altteul/blob/18788e9839fe5e7a34cbc9001d4e8fd85a9a61d3/altteul-be/src/main/java/com/c203/altteulbe/user/service/UserStatusService.java#L73-L91), [측정 테스트](https://github.com/hjkim2040/altteul/blob/d61cd60d45ada3895ea86633b28c220156b88a27/altteul-be/src/test/java/com/c203/altteulbe/RedisMGETPerfTest.java#L43-L71)                           |
| 비정상 종료 시 유령 온라인 | TTL 90초 + heartbeat 갱신 | 최대 90초 내 자동 오프라인 | [TTL](https://github.com/hjkim2040/altteul/blob/18788e9839fe5e7a34cbc9001d4e8fd85a9a61d3/altteul-be/src/main/java/com/c203/altteulbe/user/service/UserStatusService.java#L27-L40), [heartbeat](https://github.com/hjkim2040/altteul/blob/18788e9839fe5e7a34cbc9001d4e8fd85a9a61d3/altteul-be/src/main/java/com/c203/altteulbe/user/service/UserStatusService.java#L94-L111)         |
| 복합키 저장 시 불필요한 SELECT | Persistable 구현 | 저장 1건당 쿼리 2회 → 1회 | [코드](https://github.com/hjkim2040/altteul/blob/18788e9839fe5e7a34cbc9001d4e8fd85a9a61d3/altteul-be/src/main/java/com/c203/altteulbe/friend/persistent/entity/Friendship.java#L47-L50)                                                                                                                                                                                               |
| 캐시·페이징 충돌로 2페이지 이후 누락 | 전체 목록 캐싱 + 앱 페이징 | 조회 누락 해소 | [코드](https://github.com/hjkim2040/altteul/blob/c8e18fc698826f6c87ec0b24aad77c5e79d3c650/altteul-be/src/main/java/com/c203/altteulbe/friend/service/FriendshipService.java#L34-L62)                                                                                                                                                                                                  |

원인 분석과 측정 조건은 포트폴리오에 정리했습니다.

> 프로젝트 종료(2025.02.20) 이후 커밋은 개인적으로 진행한 리팩토링입니다.

---

## 💡 프로젝트 소개

![main](README_assets/main.gif)

### ⚙ 개요

- 진행 기간 : 2024/1/13 ~ 2024/2/20
- 소개 : 알고리즘 배틀 사이트

### ⚙ 기획 배경

- 혼자 알고리즘 문제를 풀었을 때 느끼는 답답함을 누군가와 공유하고 싶다는 아이디어에서 착안
- 단순히 코드만 공유할 수 있는 협업 툴이 아니라 함께 문제를 풀고 경쟁하며 성장할 수 있는 서비스로 구체화 



### ⚙ 팀 구성

|         |      포지션      |                                     역할                                      |
|:-------:|:-------------:|:---------------------------------------------------------------------------:|
| **김덕진** |    FE, 팀장     |                    팀전/개인전 매칭, 게임 초대, 친구 신청, 채팅, 회원 정보 수정                    |
| **박가희** |   FE, FE 리더   | 메인, 대전 기록, 팀전/개인전 IDE, 코드 제출 및 실행, 보너스 문제, 아이템, OpenVidu로 음성 공유, AI 코칭, 디자인 |
| **장희현** |      FE       |          로그인, 로그아웃, 디자인, 채팅 모달 일부, 공통 컴포넌트 제작, 에러 페이지, PPT, 영상 제작           |
| **진우석** | FE, BE, BE 리더 |                회원, 코드 공유, 채점 서버 연동, 게임 결과 반환, 랭킹 페이지 조회 및 검색                |
| **김성찬** |      BE       |                    친구(요청, 수락, 거절, 목록 조회), 채팅, 온라인 상태 관리                     |
| **김시아** |      BE       |               팀전/개인전 매칭, 게임 초대, 랭킹 데이터 처리 및 페이지 조회/검색 , S3 연동               |

<br>

### ⚙ 개발 환경 및 기술

![tech](README_assets/tech.png)

### ⚙ API 명세서

![swagger](README_assets/swagger.gif)

### ⚙ 기능 명세서

https://jumbled-hexagon-bd2.notion.site/19d56f9141d380a0a351f11a57e5c701?pvs=4

### ⚙ ERD

https://www.erdcloud.com/d/4NDsDbCnsSfs4cGsh

### ⚙ 아키텍처

![architecture](README_assets/architecture.png)


<br>

---

## 🆚 서비스 소개

### ⚙ 회원가입


|                                                   |                                                   |
|---------------------------------------------------|---------------------------------------------------|
| ![signup](README_assets/signup.gif)               | ![github-signup](README_assets/github-signup.gif) |
| 일반 회원가입                                           | 깃허브 회원가입                                          |



### ⚙ 로그인

![login](README_assets/login.gif)

- JWT, Spring Security 활용

### ⚙ 유저페이지

![record](README_assets/record.gif)

- 대전 기록 열람 가능
- 해당 유저의 등수, 등급, 상위 분포 확인 가능
- 플레이 시간, 함께 게임한 유저, 제출한 코드의 실행 시간 및 메모리, 문제 정보 확인 가능 


### ⚙ 친구 맺기

|                                                    |                                      |
|----------------------------------------------------|--------------------------------------|
| ![request-friend](README_assets/request-friend.gif) | ![delete-friend](README_assets/delete-friend.gif) |
| 친구 신청                                              | 친구 삭제                                |


- WebSocket을 통해 친구 신청 시 상대방에게 실시간으로 알람 생성 
- WebSocket, Redis를 통해 친구의 온라인/오프라인 상태 확인
- 친구 신청을 위한 유저 검색 기능 제공 


### ⚙ 채팅

|                                           |                                         |
|-------------------------------------------|-----------------------------------------|
| ![chatrooms](README_assets/chatrooms.gif) | ![chatting](README_assets/chatting.gif) |
| 채팅방 조회                                    | 실시간 채팅                                  |

- 가장 최근 채팅 메시지가 채팅방 목록에 함께 노출

### ⚙ 랭킹 페이지

|                                                 |                                         |
|-------------------------------------------------|-----------------------------------------|
| ![ranking-page](README_assets/ranking-page.gif) | ![ranking-login-ver](README_assets/ranking-login-ver.gif) |
| 비로그인 회원                                         | 로그인 회원                                  |



- 매일 자정에 Spring Batch로 업데이트되는 데일리 랭킹 데이터를 렌더링
- 유저별 순위, 순위 변동, 랭킹 점수, 선호 언어, 평균 통과율 확인 가능 
- 닉네임, 등급별, 언어 별 검색 기능 제공 
- 로그인한 회원의 경우 본인의 등급이 첫 번째에 위치

### ⚙ 개인전 매칭

|                                                       |                                                                     |
|-------------------------------------------------------|---------------------------------------------------------------------|
| ![single-matching](README_assets/single-matching.gif) | ![single-matching-cancel](README_assets/single-matching-cancel.gif) |
| 매칭 후 게임 화면으로 이동                                       | 방 나가기                                                               |



- 최소 2명, 최대 8명 게임 가능
- redis를 통해 대기방 상태 및 소속된 유저들 관리
- 게임 시작 시 redis에서 관리하던 데이터를 DB에 저장

### ⚙ 개인전 배틀

|                                                   |                                                     |
|---------------------------------------------------|-----------------------------------------------------|
| ![single-coding](README_assets/single-coding.gif) | ![single-execute](README_assets/single-execute.gif) |
| 코드 작성                                             | 코드 실행                                               |
| ![single-submit](README_assets/single-submit.gif) | -                                                   |
| 코드 제출 및 결과                                        | -                                                   


- 실시간으로 상대의 테스트케이스 통과 현황 파악 가능

### ⚙ 팀전 매칭

|                                                   |                                                                 |
|---------------------------------------------------|-----------------------------------------------------------------|
| ![team-matching](README_assets/team-matching.gif) | ![team-matching-cancel](README_assets/team-matching-cancel.gif) |
| 매칭 후 게임 화면으로 이동                                   | 매칭 취소                                                           |
| ![team-matched](README_assets/team-matched.gif)   | ![team-matching-leave](README_assets/team-matching-leave.gif)   |
| 매칭 완료                                             | 방 나가기                                                           


- 최소 2명, 최대 4명 게임 가능 
- redis를 통해 대기방 상태 및 소속된 유저들 관리
- 게임 시작 시 redis에서 관리하던 데이터를 DB에 저장 


### ⚙ 팀전 게임 초대

|                                     |                                                   |
|-------------------------------------|---------------------------------------------------|
| ![invite](README_assets/invite.gif) | ![invite-refuse](README_assets/invite-refuse.gif) |
| 초대 승인                               | 초대 거절                                             |


- 친구 초대 시 실시간 알람 전송
- 실시간으로 이루어지는 게임 특성 상 Redis TTL을 10분으로 설정하여 유효기간 지정

### ⚙ 팀전 배틀

|                                               |                                                 |
|-----------------------------------------------|-------------------------------------------------|
| ![team-coding](README_assets/team-coding.gif) | ![team-opponent](README_assets/team-opponent.gif) |  ![team-opponent](README_assets/team-opponent.gif) 
| 동시 코드 작성                                      | 상대팀 코드 블러 처리                                    |
|   ![bonus](README_assets/bonus.gif)     |  ![team-execute](README_assets/team-execute.gif)   |
| 보너스 문제 및 아이템 자동 적용                            | 코드 실행                                      |
|![team-submit](README_assets/team-submit.gif) |   ![team-ai](README_assets/team-ai.gif)
|  코드 제출 및 결과                                |    AI 코칭

- 실시간으로 상대의 테스트케이스 통과 현황 파악 가능
- 상대 팀의 IDE를 블러 처리한 상태로 노출
- 같은 팀끼리 실시간 코드 공유 가능
- OpenVidu를 통해 같은 팀끼리 음성 채팅 가능
- OpenAI를 통해 제출한 코드에 대해 피드백 제공

---

## 📄 PR Template

```declarative
<!-- PR 메시지 : 🔀 [FE] merge: from feature/ to dev  -->

## 개요
### 🔀 [FE] merge: from feature/ to dev 
<!---- 변경 사항 및 관련 이슈에 대해 간단하게 작성해주세요. 어떻게보다 무엇을 왜 수정했는지 설명해주세요. -->

<!---- Resolves: #(Issue Number) -->

## PR 유형
어떤 변경 사항이 있나요?

- [ ] 기능 및 코드 추가
- [ ] 기능 및 코드 수정중
- [ ] 기능 및 코드 수정 완료
- [ ] 버그 수정
- [ ] 디자인 수정
- [ ] 코드 리팩토링
- [ ] 문서작성 및 편집
- [ ] config 파일 수정
- [ ] 파일 추가
- [ ] 파일 제거
- [ ] 오타 수정
- [ ] 성능 향상
- [ ] public 폴더 업데이트
- [ ] 프로젝트 구조 변경
- [ ] 치명적인 버그 수정
- [ ] 프로젝트 개시
- [ ] 프로젝트 배포

## PR Checklist
PR이 다음 요구 사항을 충족하는지 확인하세요.

- [ ] 커밋 메시지 컨벤션에 맞게 작성했습니다.
- [ ] 변경 사항에 대한 테스트를 했습니다.(버그 수정/기능에 대한 테스트).
```

## ⚠️ Commit Convention

```declarative
- feat : 새로운 기능 추가
- fix : 버그 수정
- docs : 문서 수정
- style : 코드 포맷팅, 세미콜론 누락, 코드 변경이 없는 경우
- refactor : 코드 리팩토링
- test : 테스트 코드, 리펙토링 테스트 코드 추가
- chore : 별도 업무 수정, 패키지 매니저 수정
```