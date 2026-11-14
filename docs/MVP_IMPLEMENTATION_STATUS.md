# 학부모 앱 MVP 보완 현황

## 앱에서 반영한 내용

- `parent/google-services.json`에 제공받은 Firebase Android 설정 추가. 앱 ID 일치 확인 및 Git 제외 유지.
- FCM foreground 메시지의 아이·운행 ID 전달. background 알림의 Activity extras도 같은 경로로 처리.
- 알림 진입 시 로그인 및 서버의 현재 아이 연결 권한 확인 후 해당 운행의 최신 기록 조회.
- 지난 운행 알림에는 다른 날짜·운행의 ETA를 섞지 않음. 조회 실패·연결 철회·기록 없음 안내와 재시도 제공.
- OS 알림 권한, 앱 알림 차단, `dropoff` 채널 차단을 구분. 시스템 설정 이동과 복귀 후 FCM 등록 상태 갱신.
- ETA 시각이 없거나 미래·만료된 값이면 숨김. GPS 업데이트 시각으로 오래된 ETA의 유효기간을 연장하지 않음.
- 서버 통신 실패 시 기존 ETA 제거, 진행 정지 및 재시도 안내. 연결 복구 시 현재 상태 재조회.
- 정류장 `COMPLETED`만으로 아이의 도착이나 인계 완료를 추론하지 않음.

## 서버 변경 없이는 완료할 수 없는 항목

서버 저장소는 읽기만 했으며 변경하지 않았다. 현재 구현된 Controller 기준:

1. 초대 수락 전 조회: `/guardian-invitations/accept`만 존재한다. 아이·기관을 먼저 조회하는 API가 없어 해당 확인 화면은 보류했다.
2. 전체 정류장·버스 진행: 보호자의 `/children/{id}/today`에는 내 아이 정류장 정보만 있다. 전체 익명 정류장 목록·현재 구간·구간 진행률은 제공되지 않는다. 차량 전용 API를 보호자 인증으로 우회 호출하지 않는다.
3. ETA 유효기간·재탐색 사유: `etaReceivedAt`은 제공되나 서버의 명시적 TTL·유효 여부·재탐색 사유는 없다. 앱의 보수적인 30초 기준을 유지하고 제공되지 않은 사유는 추측하지 않는다.
4. 출발 푸시: 확인한 `PushNotificationService`는 도착·하차 발송을 구현한다. 출발 발송은 서버 지원이 필요하다.

## 검증과 남은 확인

- ETA 및 알림 ID 검증 등 11개 JVM 테스트 통과: 로컬 Kotlin 2.2.10/JUnit과 Android SDK의 실제 org.json 소스를 사용한 보조 실행. 프로젝트의 정식 Gradle 테스트를 대체하지 않는다.
- `NotificationUiTest` 추가: 권한 오류 시 이전 운행 숨김, 대상 아이·날짜 표시, 통신 실패 시 ETA 숨김. Android 기기에서 아직 실행하지 않음.
- `git diff --check` 통과.
- Gradle Wrapper 8.10.2가 로컬에 없고 다운로드 호스트 DNS 연결이 실패하여 APK 빌드·Lint·기기 테스트는 미완료.
- 인터넷 가능한 Android Studio에서 Embedded JDK/JDK 17 이상을 선택하고 Sync 후 아래 실행:

```powershell
.\gradlew.bat :parent:testDebugUnitTest :parent:assembleDebug :parent:lintDebug :parent:assembleDebugAndroidTest
```

실제 FCM 수신은 서버가 앱 설정과 같은 Firebase 프로젝트로 실행되어야 하며 보호자·아이·운행 데이터와 알림 권한이 필요하다. Firebase 설정 추가만으로 실제 수신이 검증된 것은 아니다.
