# 구현 기능 실서버 테스트

검증일: 2026-11-14 (한국 시간). 대상: 현재 학부모 앱과 지정된 Spring Boot/MySQL 개발 서버. 미구현 기능은 대상에서 제외한다.

## 실행 환경과 범위

- Android 17 Medium Phone 에뮬레이터 → `http://10.0.2.2:8080`.
- 앱과 동일한 `Api` HTTP 클라이언트 및 `normalizeJourney` 변환을 사용한다.
- 매 실행마다 `example.invalid` 이메일, 자동검증기관, 검증아이/미연결아이, 차량, 노선, 정류장을 새로 생성한다. 기존 계정/운행은 변경하지 않는다.
- 관리자/차량 API는 테스트 데이터 준비와 가상 운행 상태 갱신에만 사용한다. 학부모 조회는 별도 보호자 토큰으로 검증한다.
- 생성된 테스트 레코드는 개발 DB에 남는다. 실제 서비스 데이터로 사용하지 않는다. 테스트용 공유 링크는 검증 후 폐기하며 앱에 로그인한 테스트 세션은 종료한다.
- 서버 소스/설정 파일은 수정하지 않는다.

## 실서버 API 검증

| 항목 | 확인 내용 | 결과 |
| --- | --- | --- |
| 서버/DB | `/health/database`의 UP | 통과 |
| 계정 | 인증번호 발급·확인, 회원가입, 보호자 로그인 | 통과 |
| 아이 연결 | 기관 초대 발급, 보호자 수락, 아이 목록 | 통과 |
| 접근 제어 | 미연결 아이의 오늘 운행·알림 상세 접근 거절 | 통과 |
| 운행 없음 | NO_TRIP_TODAY 처리 | 통과 |
| 출결 변경 | 다음 날짜 직접 픽업 신청 및 조회 | 통과 |
| 마중 보호자 | 대리인 등록·승인·하원 지정·조회, 등원 본인 지정 | 통과 |
| 위치/ETA | 가상 운행 출발, 위치/ETA 업로드, 보호자 조회와 앱 변환 | 통과 |
| 공유 | 링크 생성·조회·폐기 후 조회 거절 | 통과 |
| 하원 기록 | 도착·탑승·하차·운행 종료, 운행 이력과 알림 대상 상세 | 통과 |
| 세션 | refresh token 갱신 후 아이 목록 접근 | 통과 |

## 앱 화면과 최종 결과

- 실제 MainActivity 로그인 화면에서 개발 서버 주소/테스트 계정을 입력하여 로그인, 해당 아이 홈과 하차 완료 표시 확인: 통과.
- `childId`/`tripId` 알림 extras로 MainActivity를 다시 열어 해당 아이·운행·한국 날짜·하차 완료 표시 확인: 통과. FCM을 통한 실제 전달 검증은 아니다.
- 기존 화면 테스트 19개: 통과. 일정/아이 선택, 출결 입력, 과거 일정 읽기 전용, 공유 버튼, 상태 표시, ETA 숨김, 알림 오류 처리를 포함한다. 일부 화면 테스트는 고정된 입력과 콜백으로 검증하므로 실제 서버 조회 검증과 구분한다.
- 단위 테스트 16개(운행 데이터 13개 + JSON 변환 3개): 전부 통과.
- 에뮬레이터 테스트 21개 중 20개 통과, 1개 건너뜀, 실패/오류 0개. 건너뛴 항목은 이전 Python PoC용 `ParentDeviceTest`이며 현재 서버 대상 `LiveGuardianIntegrationTest`는 통과했다.
- `:parent:testDebugUnitTest :parent:assembleDebug :parent:connectedDebugAndroidTest` 동시 실행 결과 `BUILD SUCCESSFUL` 확인. 디버그 APK 생성 성공.

## 발견 후 수정한 문제

서버 시각이 에뮬레이터보다 약 2초 앞서 있어, 정상 ETA도 미래 값으로 간주해 숨겼다. 최대 5초의 작은 시차만 허용하고 그 경우 ETA 기준 시각을 기기 수신 시각으로 제한한다. 30초 초과 만료, 5초 초과 미래 값, 시각 누락, 통신 실패는 계속 ETA를 숨긴다. 회귀 단위 테스트를 추가했다.

JDBC 날짜가 `2026-11-13T15:00:00.000Z`처럼 한국 날짜 자정의 UTC 시각으로 직렬화되는 것도 확인했다. 알림의 운행 날짜 표시와 일정/홈의 출결 신청 날짜 비교를 한국 날짜로 정규화했다. 기존 `YYYY-MM-DD` 형식도 계속 지원한다.

## 제한

- 서버 설정은 `FIREBASE_ENABLED=false`다. 실제 FCM 발송·수신은 검증하지 않았다. 알림 extras로 대상 화면을 여는 검증과 실제 FCM 수신은 구분한다.
- OS/알림 채널 차단 조합과 시스템 설정 왕복은 이번 실서버 자동 테스트에서 모두 재현하지 않았다.
- 출결/대리인/공유 API 통과가 모든 실제 화면 조작과 Android 공유 대상 앱 동작까지 보장하는 것은 아니다.
- 기존 Python PoC 전용 `ParentDeviceTest`는 별도 `serverBase`가 없으면 건너뛴다. 현재 Spring Boot API 검증 결과로 계산하지 않는다.

## 재실행

아래 테스트는 명시적으로 활성화할 때만 개발 DB에 테스트 데이터를 생성한다. `liveServer` 없이 실행하면 건너뛴다.

```powershell
.\gradlew.bat :parent:testDebugUnitTest :parent:assembleDebug :parent:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.liveServer=true
```

결과 파일: `parent/build/test-results/testDebugUnitTest/`, `parent/build/outputs/androidTest-results/connected/debug/`, `parent/build/reports/androidTests/connected/debug/`.
