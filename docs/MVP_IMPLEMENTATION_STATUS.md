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

Gradle 8.10.2 / Android Studio Embedded JDK 21 기준으로 다음 네 작업을 함께 실행해 성공했다.

- `:parent:testDebugUnitTest`: 14개 통과, 실패 0개 (`JourneyDataTest` 11개, `ApiJsonTest` 3개).
- `:parent:assembleDebug`: 디버그 APK 생성 성공.
- `:parent:lintDebug`: 오류 0개, 경고 55개. 주로 의존성 버전·버전 카탈로그·기존 Compose 스타일 권고이며 경고를 숨기는 baseline은 추가하지 않았다.
- `:parent:assembleDebugAndroidTest`: 기기 테스트 APK 생성 성공.
- `git diff --check`: 통과.

`NotificationUiTest`는 권한 오류 시 이전 운행 숨김, 대상 아이·날짜 표시, 통신 실패 시 ETA 숨김을 검증한다. 기기에서의 실행 결과는 별도 확인이 필요하다.

```powershell
.\gradlew.bat :parent:testDebugUnitTest :parent:assembleDebug :parent:lintDebug :parent:assembleDebugAndroidTest
```

Windows 한글 경로로 인한 Gradle 테스트 클래스 로딩 오류는 해당 클래스패스 항목만 매 실행마다 새 임시 영문 경로에 복사하여 처리한다. 원본 소스와 프로젝트 경로는 바꾸지 않는다. Hilt 플러그인을 루트 클래스패스에 선언하여 JavaPoet 버전 충돌도 해결했다.

실제 FCM 수신은 서버가 앱 설정과 같은 Firebase 프로젝트로 실행되어야 하며 보호자·아이·운행 데이터와 알림 권한이 필요하다. Firebase 설정 추가와 빌드 성공만으로 실제 수신이 검증된 것은 아니다. 서버 데이터 변경을 수반하는 통합 테스트는 실행하지 않았다.

## 기술스택 적용

기준 문서: 안드로이드 앱 기술스택(`4125686b1943809b8ddddb1039280016`), 개발 환경 기준 및 설정(`4065686b19438051bea2ced413a23438`).

| 요구사항 | 적용 |
| --- | --- |
| Compose·Kotlin | 기존 Compose 화면과 Kotlin 유지 |
| Google 권장 계층 | `GuardianRepository` 데이터 계층과 `ParentModel` 화면 계층 분리 |
| MVI·Coroutine·Flow | `ParentIntent` 입력 → ViewModel 처리 → `ParentUiState`의 읽기 전용 StateFlow → 생명주기를 고려한 Compose 수집 |
| Hilt | Application·Activity·ViewModel·Repository·Settings 생성자 주입 |
| Kotlinx.serialization | `ApiJson`에서 API 요청/응답 JSON 직렬화. 기존 화면은 org.json 어댑터로 연결 |
| DataStore | 서버 주소와 알림 요청 설정 저장. 기존 설정 마이그레이션. 계정 세션은 별도 기존 저장소 유지 |
| Navigation Compose | 인증·홈·알림 운행 상세의 NavHost 및 로그인 상태별 이동 |
| Coil | PNG·SVG Figma 에셋을 AsyncImage와 SvgDecoder로 로딩 |
| FCM | 제공된 Android 설정과 토큰 등록·알림 이동 연동 |
| Room(필요시) | 별도 오프라인 DB가 필요한 범위가 없어 추가하지 않음 |
| WebSocket | 현재 서버에 WebSocket 엔드포인트·인증·이벤트 계약 없음. 서버를 수정하지 않는 조건으로 기존 5초 조회를 유지 |
| 개발 환경 | minSdk 28, Gradle Wrapper 8.10.2 유지. Pleos SDK·차량 에뮬레이터 연동은 차량 앱 담당 |

Windows의 한글 프로젝트 경로를 유지하기 위해 `android.overridePathCheck=true`를 적용했다.
