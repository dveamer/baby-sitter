# Plan 07: Sleep-Off Input Gating

## 상태

- 완료: 2026-04-26
- 반영 상태: working tree
- 설계 선택: collect 입력은 raw toggle이 아니라 effective intent 기준으로 계산하고, preview subscriber의 `0 -> 1`, `1 -> 0` 전이는 service refresh로 collect input만 즉시 재평가한다.

## 구현 결과

- `CollectRecorderCoordinator`가 `sleepEnabled`, monitoring toggles, preview subscriber demand를 합쳐 effective collect input policy를 계산한다.
- 오디오는 `sleepEnabled && soundMonitoringEnabled`일 때만 collect가 열리고, `sleepEnabled=false`이면 `sound` 토글이 켜져 있어도 `CollectAudioSource`는 열리지 않는다.
- 카메라는 `sleepEnabled && cameraMonitoringEnabled` 또는 `webCameraEnabled && active preview subscriber`일 때만 collect가 열린다.
- preview subscriber 연결/해제는 `ForegroundServiceSleepRuntime.refresh()`를 통해 `SleepForegroundService`의 collect input만 다시 평가한다. monitoring engine 전체를 재시작하지는 않는다.
- `CollectCameraSource`는 같은 collect ownership 안에서 motion analysis 허용 여부와 preview 허용 여부만 동적으로 갱신한다.
- `LocalSettingsHttpServer`의 `cameraMemoryAvailable`는 더 이상 `webCameraEnabled`만 보지 않고, 실제 collect input policy와 최근 닫힌 collect 비디오 존재 여부를 함께 반영한다.

## 검증 결과

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest`

## 목표

`sleepEnabled=false` 상태에서는 `sound`와 `motion` 토글이 켜져 있어도 카메라/마이크 collect가 자동으로 시작되지 않게 한다.
동시에 원격 웹 서비스는 유지하되, 실제 하드웨어 입력은 필요한 경우에만 켜지도록 경계를 분명히 한다.

## 가드레일 충돌 여부

- 기본 방향 자체는 가드레일과 직접 충돌하지 않는다.
- 이유는 이 계획이 카메라 소유권을 `collect` 밖으로 빼려는 안이 아니라, `collect` 입력이 언제 켜져야 하는지 더 엄격하게 제한하는 안이기 때문이다.
- 다만 아래 두 조건을 지키지 않으면 바로 충돌할 수 있다.
  - `LocalSettingsHttpServer`나 web layer가 카메라를 직접 열지 말 것
  - manual memory를 위해 카메라를 즉석에서 켜거나, 아직 닫히지 않은 현재/미래 collect 파일을 memory 범위에 포함시키지 말 것
- 특히 `sleepEnabled=false`인데 active web preview subscriber가 있을 때 "preview만 위해 카메라를 잠깐 직접 연다" 식으로 구현하면, 카메라 단일 접근 원칙과 충돌한다.
- 따라서 preview demand를 허용하더라도 카메라를 여는 주체는 계속 `CollectCameraSource`여야 한다.

## guardrail-safe 해석

- 이 계획의 안전한 해석은 "service 생명주기와 collect 입력 활성 조건을 정리하되, 카메라 직접 접근 주체는 계속 `collect` 하나로 유지한다"이다.
- `sleep off + preview subscriber 있음` 케이스도 `collect` ownership 안에서만 처리해야 한다.
- 반대로 "preview-only 전용 새 카메라 경로"를 web service 쪽에 만들거나, 수동 memory 요청 시 카메라를 즉석에서 깨우는 방식은 이 계획 범위에서 제외한다.

## 구현 전 현재 동작

- foreground service는 `sleepEnabled || webServiceEnabled`일 때 시작된다.
- service가 시작되면 `runEngine()`에서 `sleepEnabled`를 확인하기 전에 먼저 `collectRecorderCoordinator.updateInputs(...)`와 `ensureCollectSourcesRunning()`을 호출한다.
- 현재 collect 입력 정책은 다음과 같다.
  - 카메라: `cameraMonitoringEnabled || webCameraEnabled`
  - 오디오: `soundMonitoringEnabled`
- 따라서 `sleepEnabled=false`여도 `webServiceEnabled=true`이고 `Sound` 또는 `Motion` 토글이 켜져 있으면, 실제 카메라/마이크 collect가 시작될 수 있다.
- `02`에서 해결된 것은 "idle preview JPEG 인코딩 낭비"이지, `webCameraEnabled`만으로 카메라 세션과 recorder가 열리는 문제까지는 아니다.

## 왜 문제가 되는가

- 사용자는 `Sound`, `Motion` 토글을 "sleep monitoring 준비 상태"로 이해할 가능성이 높고, `Sleep ON` 전에는 실제 녹음/녹화가 일어나지 않기를 기대할 수 있다.
- 현재는 `webServiceEnabled`가 foreground service 생명주기를 유지시키는 동안, `sleep off`에서도 collect 입력이 켜질 수 있어 발열과 저장이 발생한다.
- 이 동작은 `sleep off + web camera 미연결`에서도 약한 발열이 느껴지는 현상을 설명할 수 있다.

## 관련 파일

- `app/src/main/java/com/dveamer/babysitter/sleep/SleepRuntimeOrchestrator.kt`
- `app/src/main/java/com/dveamer/babysitter/sleep/SleepForegroundService.kt`
- `app/src/main/java/com/dveamer/babysitter/collect/CollectRecorderCoordinator.kt`
- `app/src/main/java/com/dveamer/babysitter/collect/CollectCameraSource.kt`
- `app/src/main/java/com/dveamer/babysitter/collect/CollectAudioSource.kt`
- `app/src/main/java/com/dveamer/babysitter/web/LocalSettingsHttpServer.kt`
- `app/src/main/assets/index.html`

## 목표 동작

- `sleepEnabled=false`이고 active web preview subscriber가 없으면:
  - 카메라 collect OFF
  - 마이크 collect OFF
  - 원격 웹 서비스는 필요 시 계속 ON
- `sleepEnabled=false`이고 active web preview subscriber가 있으면:
  - 카메라는 preview 목적에 한해 ON 가능
  - 단, 카메라를 여는 주체는 계속 `collect`여야 한다
  - 마이크는 OFF 유지
- `sleepEnabled=true`이면:
  - 현재 sleep monitoring + pre-roll 동작 유지
  - `sound`/`motion` collect와 `memory` 전제 유지

## 범위

- foreground service 전체를 없애는 계획은 아니다.
- `webServiceEnabled`는 원격 제어/상태 확인을 유지하는 의미로 남길 수 있다.
- 핵심은 "service 생명주기"와 "하드웨어 입력 활성화"를 분리하는 것이다.
- `memory(camera)`는 계속 닫힌 collect 파일 범위를 기준으로 유지한다.
- web preview를 위해 별도의 direct camera path를 추가하는 작업은 범위 밖이다.
- manual memory의 범위를 닫히지 않은 collect 파일까지 넓히는 작업도 범위 밖이다.

## 작업 단계

1. collect 입력 정책을 명시적인 "의도" 기준으로 재정의한다.
   - sleep monitoring capture intent
   - live web preview capture intent
   - audio collect intent
2. `CollectRecorderCoordinator.updateInputs(...)` 입력값을 raw settings가 아니라 effective demand로 바꾼다.
   - 오디오는 `sleepEnabled && soundMonitoringEnabled`
   - 모션 카메라는 `sleepEnabled && cameraMonitoringEnabled`
   - 웹 preview 카메라는 `webCameraEnabled && active preview subscriber`
3. preview subscriber 변화가 있을 때 service가 collect 상태를 즉시 재평가하도록 만든다.
   - 첫 subscriber 연결 시 카메라 시작
   - 마지막 subscriber 해제 시 카메라 정지
   - 후보는 service refresh action, coordinator callback, 또는 상태 flow다
4. `sleep off`에서 web preview demand가 없으면 `CollectCameraSource`가 아예 열리지 않도록 한다.
5. `sleep off`에서 `CollectAudioSource`는 어떤 경우에도 열리지 않도록 한다.
6. 원격 UI 상태를 실제 가능 여부에 맞춘다.
   - `cameraMemoryAvailable`가 단순 `webCameraEnabled`가 아니라 실제 recent collect availability 또는 정책상 가능 여부를 반영하게 조정할지 결정한다.
   - 수동 memory 버튼이 misleading state를 보여주지 않게 한다.
7. `sleep on` 전환과 `sleep off` 전환에서 기존 pre-roll/memory flush 동작이 깨지지 않는지 확인한다.
8. 구현 중에 web layer나 manual memory 경로에 direct camera 접근이 새로 생기지 않는지 점검한다.

## 설계 판단 포인트

- `Sound`/`Motion` 토글은 "지금 즉시 수집"이 아니라 "Sleep ON 시 사용할 감지 축"으로 해석하는 편이 사용자 기대에 맞다.
- `webServiceEnabled`는 앱을 깨워 두는 이유가 될 수 있지만, 그것만으로 카메라/마이크를 열 이유는 약하다.
- preview demand를 실제 카메라 input gating까지 연결하려면 subscriber count의 0 -> 1, 1 -> 0 전이를 service가 알아야 한다.
- preview 첫 연결 시 카메라 warm-up 지연이 생길 수 있다. 이 trade-off는 수용 가능 여부를 확인해야 한다.
- `cameraMemoryAvailable`는 현재 `webCameraEnabled`만 보고 있어 정책과 UI가 어긋날 가능성이 있다.
- "preview subscriber가 있으면 camera ON 가능"이라는 문장은 `collect` ownership 아래에서만 성립한다. 이 조건이 빠지면 가드레일과 충돌한다.
- 수동 memory는 닫힌 collect 파일 전제를 그대로 유지해야 하므로, `sleep off` idle 구간에서 collect가 없으면 unavailable로 내려가는 쪽이 guardrail-safe하다.

## 검증 계획

- `sleep off + webService on + sound on`에서 오디오 파일과 마이크 amplitude publish가 발생하지 않는지 확인한다.
- `sleep off + webService on + motion on`에서 카메라 세션과 비디오 파일이 생성되지 않는지 확인한다.
- `sleep off + webService on + webCameraEnabled on + subscriber 없음`에서 카메라가 열리지 않는지 확인한다.
- `sleep off + webService on + webCameraEnabled on + subscriber 연결`에서 연결 중에만 preview가 동작하는지 확인한다.
- `sleep on + sound/motion on`에서는 기존 collect, awake detection, memory pre-roll이 그대로 유지되는지 확인한다.
- 수동 memory 버튼 상태와 `/memory/manual` 동작이 서버 authoritative 상태와 맞는지 확인한다.
- 새 코드 어디에서도 `collect` 외 직접 카메라 접근이 생기지 않았는지 확인한다.
- 수동 memory가 현재/미래 분 파일을 요구하지 않는지 확인한다.

## 리스크

- preview demand 전이를 service에 전달하는 경로를 잘못 설계하면 첫 연결에서 카메라가 늦게 뜨거나 영영 안 뜰 수 있다.
- `sleep off -> sleep on` 전환 직후 collect 시작 순서가 바뀌면 pre-roll 시작 시점이 흔들릴 수 있다.
- manual memory 가능 여부를 같이 정리하지 않으면 UI 혼란이 남을 수 있다.
- preview warm-up 문제를 피하려고 web layer direct capture를 추가하면 가드레일을 깨게 된다.

## 완료 조건

- `sleepEnabled=false` 상태에서 원격 viewer가 없으면 카메라/마이크 collect가 시작되지 않는다.
- `sleepEnabled=false` 상태에서 audio collect는 완전히 꺼진다.
- 원격 preview는 subscriber가 있을 때만 카메라를 사용한다.
- 원격 preview와 manual memory가 계속 `collect` ownership과 닫힌 collect 파일 전제를 지킨다.
- `sleepEnabled=true` 상태의 awake detection, pre-roll 3분, memory 생성 전제는 유지된다.
