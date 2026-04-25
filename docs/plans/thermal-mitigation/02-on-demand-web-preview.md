# Plan 02: On-Demand Web Preview

## 상태

- 완료: 2026-04-26
- 반영 상태: working tree
- 설계 선택: preview demand는 active subscriber 수 기준으로 즉시 on/off 하고, 마지막 disconnect 시각만 기록한다. 별도 grace delay는 이번 범위에 넣지 않았다.

## 구현 결과

- `CollectRecorderCoordinator`가 web preview subscriber 수와 마지막 disconnect 시각을 관리한다.
- `LocalSettingsHttpServer`의 `/camera/stream` 연결 lifecycle이 subscriber 증감 처리를 맡는다.
- `CollectCameraSource`는 `webCameraEnabled`가 켜져 있어도 active subscriber가 있을 때만 preview JPEG를 인코딩한다.
- motion gray frame 경로, `collect`의 카메라 소유권, 닫힌 collect 파일 기반 `memory` 경로는 그대로 유지했다.
- preview subscriber underflow를 막는 단위 테스트 `CollectRecorderCoordinatorTest`를 추가했다.

## 검증 결과

- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`

## 목표

웹 카메라를 실제로 보고 있는 클라이언트가 없을 때는 미리보기 비용을 최소화한다.

## 01 반영 후 상태

- `01-split-recording-and-detection-pipeline.md`가 먼저 반영되어, 모션 감지와 웹 미리보기는 이미 서로 다른 파생 경로를 사용한다.
- 현재 남은 낭비는 "감지와 preview가 한 버스에 묶여 있다"가 아니라, `webCameraEnabled`만 켜져 있어도 실제 시청자 없이 preview JPEG 인코딩이 계속 돌 수 있다는 점이다.
- 따라서 이 문서의 범위는 frame bus 재설계가 아니라, preview JPEG 생성 시점을 실제 subscriber demand에 맞춰 동적으로 끄고 켜는 쪽으로 좁혀진다.

## 현재 관찰

- `CollectRecorderCoordinator`는 `cameraMonitoringEnabled || webCameraEnabled`일 때 카메라 입력을 켠다.
- `LocalSettingsHttpServer`의 `/camera/stream`은 `CameraFrameBus`에서 프레임을 꺼내 MJPEG로 전송한다.
- 현재 구조에서는 `webCameraEnabled`가 켜져 있으면 실제 시청 여부와 무관하게 preview JPEG 생성 경로가 계속 활성 상태가 될 수 있다.

## 관련 파일

- `app/src/main/java/com/dveamer/babysitter/collect/CollectRecorderCoordinator.kt`
- `app/src/main/java/com/dveamer/babysitter/collect/CollectCameraSource.kt`
- `app/src/main/java/com/dveamer/babysitter/web/LocalSettingsHttpServer.kt`
- `app/src/main/java/com/dveamer/babysitter/sleep/SleepForegroundService.kt`

## 범위

- 카메라 소유권 이동 없이, `collect` 내부에서 preview 필요 여부를 관리한다.
- `webCameraEnabled`는 기능 허용 플래그로 두고, 실제 프레임 생성은 active subscriber 기준으로 제어한다.
- `01`에서 분리한 motion gray frame 경로는 유지하고, 이 문서에서 다시 합치거나 재설계하지 않는다.
- `memory` 조립 경로는 건드리지 않는다.

## 작업 단계

1. preview subscriber 개념을 정의한다.
   - 현재 연결 수
   - 마지막 연결 종료 시각
   - 정리 지연 시간 필요 여부
2. `/camera/stream` 연결 lifecycle에 subscriber 증감 처리를 넣는다.
   - 연결 성공 시 증가
   - 정상 종료, 예외 종료, client disconnect 시 감소
3. `CollectCameraSource` 또는 coordinator에 preview demand 상태를 전달한다.
   - 현재의 정적 `webPreviewEnabled` 대신 동적 `previewDemandActive` 또는 동등한 상태를 전달한다.
   - demand가 없으면 preview JPEG 인코딩만 중단한다.
   - demand가 생기면 같은 collect 세션 안에서 preview JPEG 인코딩을 재개한다.
4. sleep monitoring만 켜져 있고 웹 시청자가 없는 경우를 최적화한다.
   - motion 감지용 gray frame 경로만 유지한다.
   - 웹 미리보기용 JPEG 생성만 중단한다.
5. 다중 클라이언트 연결 동작을 정리한다.
   - 첫 번째 연결만 preview 활성화
   - 마지막 연결 해제 시 preview 비활성화
6. 초기 프레임 지연과 reconnect 동작을 점검한다.

## 설계 판단 포인트

- `webCameraEnabled`를 "스트리밍 허용"으로 볼지 "카메라 상시 활성화"로 볼지 명확히 정해야 한다.
- `01`이 이미 analysis reader를 유지하는 구조를 만들었으므로, 가능하면 카메라 세션 전체 재구성보다 preview 인코딩 분기만 동적으로 토글하는 쪽이 안전하다.
- 첫 프레임 지연이 너무 커지면 사용자 경험이 나빠질 수 있으므로 warm-up 전략이 필요한지 검토한다.

## 검증 계획

- `webCameraEnabled=true`지만 연결이 없을 때 preview frame 생성이 멈추는지 확인한다.
- 첫 연결 시 스트림이 정상 시작되는지 확인한다.
- 연결 해제 후 preview 관련 자원이 정리되는지 확인한다.
- 여러 브라우저 탭이 동시에 붙어도 카운트 누수가 없는지 확인한다.
- motion 감지와 `memory` 생성에는 영향이 없는지 확인한다.

## 리스크

- 연결 수 관리가 틀리면 preview가 영구적으로 켜지거나 안 켜질 수 있다.
- client disconnect 예외 처리 누락 시 subscriber leak가 생길 수 있다.
- 불필요하게 카메라 세션 재구성을 선택하면 오히려 `01`에서 줄인 안정성 리스크를 다시 키울 수 있다.

## 완료 조건

- 실제 웹 시청자가 없을 때 preview 비용이 제거되거나 크게 낮아진다.
- 웹 시청이 필요할 때는 기존 기능 수준을 유지한다.
- 카메라 사용 주체와 `memory` 전제는 그대로 유지된다.
