# 타요타요 보호자 앱

## 마중 노트 디자인 적용

확정된 3번 기본 탭 바(일정 / 홈 / 아이)를 Compose 앱에 적용했습니다. 선택된 탭은 노란색 채움 아이콘과 상단 실선으로 표시합니다. Figma 원본 크레용 SVG/PNG와 Gaegu·IBM Plex Sans KR 글꼴은 앱에 포함됩니다.

- 로그인: 손글씨 워드마크, 등하원길 그림, 이메일 가입·로그인
- 공통 상단: 아이·기관 선택, 차량 정보(서버에서 제공할 때), 알림, 초대 연결
- 홈: 등하원 공통 화면, 출발 대기·이동·도착·하차·인계 상태, 오늘 변경 및 마중 보호자 패널
- 일정: 달력 직접 표시, 선택 날짜의 등원·하원 변경과 보호자 지정
- 아이: 연결 정보, 대리 보호자 등록 신청·승인 상태, 알림·계정 설정

**서버 없이 확인:** debug 앱의 로그인 화면 아래 `디자인 미리보기 · 시연 데이터`를 누르세요. 상단 시연 버튼으로 하원 운행 → 등원 대기 → 하차 완료를 바꾸고, `호출 시연`으로 응답 패널을 확인할 수 있습니다. 이 화면은 실제 계정·일정·알림을 변경하지 않으며 release 빌드에는 포함되지 않습니다.

**연동 범위:** 기존 `/api/v1` 계정·초대·출결 요청·픽업 담당자·공유 API를 유지했습니다. 호출 응답 API, 전체 정류장 순서, 기관별 변경 마감 정책은 이 저장소에서 계약을 확인할 수 없어 운영 연동을 완료하지 않았습니다. 실제 화면에 임의의 정류장/응답 성공/마감 시간을 표시하지 않습니다. 호출 패널은 현재 시연으로 확인합니다.

**등하원 자동 전환:** 진행 중인 등원/하원 운행을 우선 표시하고, 등원이 완료되면 하원으로 전환합니다. 일정 시간 정보가 없는 대기 상태에는 한국 시간 정오를 표시 전환의 임시 기준으로 사용합니다. 기관 운행 시간 API가 연결되면 해당 기준으로 교체해야 합니다. 보호자가 시간을 직접 설정하는 화면은 없습니다.

**검증:** `JourneyDataTest`는 시간 단위·자동 전환·도착/하차/인계 구분을, `MajungUiTest`는 탭 이동·아이 선택·날짜별 변경 패널·지난 날짜 읽기 전용·호출 응답 상태를 검증합니다. 실제 서버 저장 및 FCM 수신 검증은 별도입니다.

기관에서 발급한 어린이 초대 수락, 오늘의 운행 진행·예상 시간 확인, 출발/도착/하차 푸시 알림, 임시 보호자 픽업 링크 공유를 제공하는 Android 앱입니다. 연결된 아이별 출결 변경 요청, 날짜별 등·하원 픽업 담당자 지정, 대리 픽업 인원 등록 신청도 할 수 있습니다. 아이 이름과 반 등 기관 원아 명단 정보는 기관 관리자 웹에서 관리합니다.

## 빌드

Android Studio에서 이 저장소 루트를 엽니다. Android SDK 36과 JDK 17 이상(분리 검증은 JBR 21 사용)이 필요합니다. 최소 Android API는 28입니다. 다른 저장소나 Pleos SDK 없이 빌드됩니다.

```powershell
.\gradlew.bat :parent:assembleDebug :parent:lintDebug :parent:assembleDebugAndroidTest
```

macOS/Linux에서는 `./gradlew`를 사용합니다. SDK 경로는 Android Studio의 `local.properties` 또는 `ANDROID_HOME`으로 설정합니다.

- 앱 모듈: `parent` (원본 모듈 이름 유지)
- 앱 ID: `com.ssafy.tonghak.parent`
- APK: `parent/build/outputs/apk/debug/parent-debug.apk`
- 제출용 release APK: [parent-release.apk 다운로드](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-parent/releases/download/v0.2-invitations/parent-release.apk) · [저장소 내 APK](apk/parent-release.apk)

release APK는 현재 빌드 산출물입니다. 새 소스 변경을 반영하려면 `.\gradlew.bat :parent:assembleRelease`로 다시 빌드한 뒤 `parent/build/outputs/apk/release/parent-release.apk`를 `apk/parent-release.apk`로 복사해 배포하세요. 저장소의 `*.apk` 제외 규칙 때문에 새 APK 파일은 Git에 자동 추가되지 않습니다.

## Firebase 설정

푸시를 테스트하려면 Firebase에서 위 앱 ID로 받은 `google-services.json`을 `parent/google-services.json`에 놓고 빌드합니다. 파일이 없으면 빌드와 일반 API 조회는 가능하지만 푸시는 비활성 상태입니다. 서버도 같은 Firebase 프로젝트의 서비스 계정을 사용해야 합니다. 서비스 계정 비공개 키를 앱에 넣지 않습니다. Firebase 파일은 Git에서 제외됩니다.

## 최초 연결

1. [TayoTayo 서버](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-server)를 실행하고 기관·차량·정류장·경로·어린이 배정을 준비합니다.
2. [Pleos 차량 앱](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-vehicle)에 같은 서버 주소와 차량 페어링 코드를 설정합니다. 기관 웹/API에서 오늘의 운행을 생성한 뒤 차량 앱에서 운행을 불러옵니다.
3. debug 로그인 화면 아래 `개발 서버 설정`을 열고 서버 주소를 에뮬레이터에서는 `http://10.0.2.2:8080`, 실제 휴대폰에서는 `http://<PC의 LAN IPv4>:8080`으로 입력합니다. 휴대폰에서 `127.0.0.1`은 PC를 가리키지 않습니다. 서버의 `/api/v1`은 앱이 자동으로 붙입니다.
4. 보호자는 회원가입 시 이메일 인증을 요청하고, 개발 환경에서 응답 메시지에 표시된 인증번호를 입력합니다. 이후 로그인하고 기관 관리자가 해당 이메일에 발급한 어린이 초대 코드를 수락합니다.
5. 알림 권한을 허용하고 `푸시 등록 완료`를 확인합니다. 계정 연결과 푸시 등록에 USB는 필요하지 않습니다.
6. 차량 앱에서 출발·위치·Pleos 예상 시간·정류장 도착·하차를 서버로 전송하고, 보호자 앱에서 오늘 운행의 위치·예상 시간·상태가 갱신되는지 확인합니다. 차량 앱과 보호자 앱은 서로 직접 연결하지 않고 같은 서버의 `/api/v1`을 사용합니다.

개발용 HTTP는 debug 빌드에서 사용합니다. 공개 운영 서버에는 HTTPS가 필요합니다. [세 저장소 전체 연결 순서](docs/INTEGRATION.md)를 참고하세요.

## 구조와 검증

- `parent/`: Compose UI, 계정/초대, FCM, 운행 진행·공유, 출결·픽업 관리 UI
- `common/kotlin/`: 서버 API 클라이언트 사본
- `common/web/`: 공유 렌더러 사본
- `scripts/test-*.cjs`: Node.js 18 이상으로 실행하는 렌더러 테스트

```powershell
node scripts/test-route-map.cjs
node scripts/test-stop-timeline.cjs
```

기존 기기 테스트는 가상 계정과 실행 중인 테스트 서버 등 별도 조건이 필요합니다. 빌드 성공은 실제 휴대폰 푸시 수신 증명이 아닙니다.

[분리 기준](docs/SOURCE_SNAPSHOT.md) / [이번 검증 결과](docs/SPLIT_VALIDATION.md) / [차량 앱](https://github.com/Hyundai-Mobility-Challenge/TayoTayo-vehicle)
