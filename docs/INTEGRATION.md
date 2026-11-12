# 세 저장소 연결하기

이 문서는 현재 Java Spring Boot + MySQL 서버의 `/api/v1` 계약을 기준으로 합니다. 과거 모노레포/Python 서버의 시연 문서는 이 연결 절차와 다를 수 있습니다.

## 저장소

- [서버](https://github.com/Hyundai-Mobility-Challenge/tonghak-server): Java Spring Boot API, MySQL, FCM 발송
- [차량](https://github.com/Hyundai-Mobility-Challenge/tonghak-vehicle): Pleos 차량 Android 앱
- [보호자](https://github.com/Hyundai-Mobility-Challenge/tonghak-parent): Android 보호자 앱

세 저장소는 각각 독립적으로 빌드/실행합니다. Git submodule이나 나란히 놓인 원본 저장소가 필요하지 않습니다. `common/kotlin`과 `common/web`은 각 저장소에 필요한 사본을 포함합니다. 공통 API 계약이나 렌더러를 수정할 때 관련 저장소에도 같은 변경을 반영하세요.

## 최초 연결

1. 서버 README에 따라 MySQL `tayotayo_dev`와 Spring Boot API를 실행합니다. Swagger UI의 `/api/v1` 엔드포인트 또는 기관 관리 기능으로 기관·차량·정류장·경로·어린이 배정을 준비합니다.
2. 차량 앱의 **기관 · 배정 명단**에서 에뮬레이터는 `http://10.0.2.2:8080`, 실제 차량 기기는 `http://<PC의 LAN IPv4>:8080`과 차량 페어링 코드를 입력합니다.
3. 기관 API에서 오늘의 `DROP_OFF` 운행을 생성합니다. 차량 앱은 해당 차량의 오늘 운행을 불러오므로, 앱에서 운행을 만들지는 않습니다.
4. 보호자 앱을 열어 서버 주소를 입력하고 가입/로그인합니다. 개발 서버 가입은 인증 요청 응답의 `debugCode`를 앱 메시지에서 확인해 입력할 수 있습니다.
5. 기관에서 보호자 이메일로 발급한 어린이 초대 코드를 앱에서 수락합니다.
6. FCM을 쓸 경우 앱의 Firebase 설정과 서버 서비스 계정은 같은 Firebase 프로젝트여야 합니다. 알림 권한 허용 후 푸시 기기 등록이 완료됐는지 확인합니다.
7. 차량에서 운행 준비·탑승·정차·보호자 인계·하차·운행 종료를 진행합니다. 어린이 상태와 운행 위치가 서버에 기록되고 보호자 앱은 오늘 운행/기록을 조회합니다.

## 서버 주소

| 접속 기기 | 개발 서버 주소 |
| --- | --- |
| 서버 PC의 브라우저 | `http://127.0.0.1:8080` |
| Android 에뮬레이터 | `http://10.0.2.2:8080` |
| 실제 휴대폰 | `http://<PC의 LAN IPv4>:8080` |

휴대폰이 PC에 네트워크로 접근할 수 있어야 하며 Windows 방화벽에서 개발용 포트 8080 연결을 허용해야 할 수 있습니다. 앱 입력란에는 서버 주소만 넣고 `/api/v1`은 붙이지 않습니다.

## Firebase 파일

- 보호자 앱: `tonghak-parent/parent/google-services.json` (앱 ID `com.ssafy.tonghak.parent`에 해당하는 설정)
- 서버: 저장소 밖의 서비스 계정 JSON을 `--credentials` 또는 `-CredentialPath`로 지정
- 서비스 계정 키는 APK에 넣지 않습니다. 두 설정 파일은 Git에서 제외됩니다.
- Firebase 설정 없이도 보호자 앱을 빌드하고 API 기록을 조회할 수 있지만 푸시 등록/발송은 활성화되지 않습니다.

## 검증 범위

서버 테스트는 임시 DB와 모의 푸시를 사용합니다. Android 빌드/단위 테스트는 실제 휴대폰 수신이나 실제 차량 센서 동작을 증명하지 않습니다. 기기 테스트는 일부가 계정/운행 상태를 변경하므로 별도 가상 데이터 환경에서 수행하세요. 에뮬레이터 지원 신호와 실제 통학차량 지원 여부는 구분합니다. 벨트 해제 알림은 별도 기능 개발과 좌석별 지원 확인이 필요한 기획 항목입니다.
