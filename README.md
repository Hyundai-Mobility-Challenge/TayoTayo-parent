# 통학메이트 보호자 앱

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

## Firebase 설정

푸시를 테스트하려면 Firebase에서 위 앱 ID로 받은 `google-services.json`을 `parent/google-services.json`에 놓고 빌드합니다. 파일이 없으면 빌드와 일반 API 조회는 가능하지만 푸시는 비활성 상태입니다. 서버도 같은 Firebase 프로젝트의 서비스 계정을 사용해야 합니다. 서비스 계정 비공개 키를 앱에 넣지 않습니다. Firebase 파일은 Git에서 제외됩니다.

## 최초 연결

1. [서버](https://github.com/Hyundai-Mobility-Challenge/tonghak-server)를 실행합니다.
2. 앱의 서버 주소는 에뮬레이터에서 `http://10.0.2.2:8080`, 실제 휴대폰에서 `http://<PC의 LAN IPv4>:8080`으로 입력합니다. 휴대폰에서 `127.0.0.1`은 PC를 가리키지 않습니다. 서버의 `/api/v1`은 앱이 자동으로 붙입니다.
3. 보호자는 회원가입 시 이메일 인증을 요청하고, 개발 환경에서 응답 메시지에 표시된 인증번호를 입력합니다. 이후 로그인하고 기관 관리자가 해당 이메일에 발급한 어린이 초대 코드를 수락합니다.
4. 알림 권한을 허용하고 `푸시 등록 완료`를 확인합니다. 계정 연결과 푸시 등록에 USB는 필요하지 않습니다.
5. 기관 웹/API에서 오늘의 운행을 미리 생성한 뒤 차량 앱에서 운행과 하차 기록을 진행해 화면/알림을 확인합니다.

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

[분리 기준](docs/SOURCE_SNAPSHOT.md) / [이번 검증 결과](docs/SPLIT_VALIDATION.md) / [차량 앱](https://github.com/Hyundai-Mobility-Challenge/tonghak-vehicle)
