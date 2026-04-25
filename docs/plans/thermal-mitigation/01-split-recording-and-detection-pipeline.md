# Plan 01: Record Path And Detection Path Split

## 상태

- 완료: 2026-04-26
- 반영 커밋: `57c2755` (`Split collect recording and motion analysis paths`)
- 후속 연계: preview subscriber demand 기반 최적화는 `02-on-demand-web-preview.md`에서 이어서 진행한다.

## 구현 결과

- `CollectCameraSource`는 녹화용 `MediaRecorder` surface와 별도 `YUV_420_888` analysis reader를 함께 사용하도록 바뀌었다.
- 모션 감지 프레임은 `CollectFrameBus`의 저해상도 grayscale snapshot으로 분리되었고, `CameraMonitor`의 JPEG decode 경로는 제거되었다.
- 웹 미리보기는 모션 감지와 분리된 preview JPEG 버스를 사용하게 바뀌었다.
- `CollectClosedFileBus`, `MemoryBuildCoordinator`, `MemoryAssembler` 경로는 그대로 유지되어 닫힌 collect 파일 기반 `memory` 전제는 바뀌지 않았다.

## 검증 결과

- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:testDebugUnitTest`

## 목표

`memory`용 원본 녹화 품질은 유지하면서, 모션 감지와 웹 미리보기 때문에 발생하는 추가 카메라 부하를 줄인다.

## 핵심 전제

- 이 계획은 awake가 감지된 뒤에 녹화를 시작하는 안이 아니다.
- pre-roll 3분을 보장하려면 녹화용 비디오는 awake 감지 이전부터 계속 기록되고 있어야 한다.
- 따라서 이 계획에서 분리 대상은 `녹화 자체`가 아니라 `녹화 위에 추가로 얹힌 감지/미리보기 비용`이다.
- `memory(camera)`는 닫힌 collect 파일만 사용하므로, 중간에 실제 녹화 gap이 생기면 바로 pre-roll 보장과 충돌한다.

## 구현 전 관찰

- `CollectCameraSource`는 한 카메라 세션에서 MP4 녹화와 JPEG 프레임 추출을 동시에 수행한다.
- `CameraMonitor`는 최신 프레임만 1초 간격으로 읽어 모션을 판정한다.
- 웹 카메라 스트림도 같은 `CameraFrameBus`의 JPEG 프레임을 사용한다.
- 즉, 녹화 품질과 감지/미리보기 품질이 같은 프레임 경로에 묶여 있다.
- 현재도 사실상 녹화용과 감지용이 함께 계속 돌고 있고, 감지 경로는 실제 소비 빈도보다 더 비싼 포맷과 처리량을 쓰고 있을 가능성이 높다.

## 관련 파일

- `app/src/main/java/com/dveamer/babysitter/collect/CollectCameraSource.kt`
- `app/src/main/java/com/dveamer/babysitter/monitor/CameraMonitor.kt`
- `app/src/main/java/com/dveamer/babysitter/monitor/CameraFrameBus.kt`
- `app/src/main/java/com/dveamer/babysitter/web/LocalSettingsHttpServer.kt`
- 필요 시 `app/src/main/java/com/dveamer/babysitter/sleep/SleepForegroundService.kt`

## 범위

- 카메라 소유권은 계속 `collect`에 둔다.
- `memory`용 MP4 녹화 설정은 초기 단계에서 유지한다.
- 감지용 프레임 경로를 녹화용 경로와 분리하되, 외부 기능은 여전히 `collect`가 만든 버스를 사용하게 유지한다.
- awake 감지 전에도 녹화는 계속 유지한다.
- 녹화 on/off 스케줄링이나 pause/resume 기반 절전은 이 문서 범위에 넣지 않는다.

## 기대 효과와 상한

- 기대 효과는 `연속 녹화 비용 제거`가 아니라 `추가 JPEG 생성, 복사, 디코드, 웹 미리보기용 변환 비용 제거 또는 축소`다.
- 따라서 발열 개선은 현실적으로 의미가 있을 수 있지만, 카메라 센서와 H.264 연속 녹화가 남아 있으므로 상한이 분명하다.
- 큰 폭의 개선을 기대하기보다, 현재 구조에서 낭비되는 부가 파이프라인을 줄이는 최적화로 보는 편이 맞다.
- 실제 체감 효과는 `02-on-demand-web-preview.md`와 결합될 때 더 커질 가능성이 높다.

## 작업 단계

1. 현재 `CollectCameraSource`의 출력 책임을 분리한다.
   - 녹화용 surface
   - 감지용 analysis frame surface
   - 필요 시 웹 미리보기용 preview frame surface
2. 감지용 프레임 형식을 재설계한다.
   - 후보는 저해상도 `YUV_420_888` 또는 grayscale에 가까운 경량 포맷이다.
   - `CameraMonitor`가 JPEG decode 없이 바로 비교할 수 있는 구조를 우선 검토한다.
3. `CameraFrameBus` 계약을 정리한다.
   - 감지용 버스와 웹 미리보기용 버스를 분리할지
   - 공용 버스에 frame kind를 추가할지 결정한다.
4. `CameraMonitor`를 새 경로에 맞게 수정한다.
   - 현재의 `BitmapFactory.decodeByteArray` 제거 또는 최소화
   - 저해상도 프레임 기준의 movement threshold 재조정
5. 웹 미리보기는 별도 버스 또는 별도 변환 경로로 연결한다.
   - 감지용 프레임을 그대로 웹에 쓰지 말고, 필요 시에만 JPEG 인코딩한다.
6. `memory` 조립 경로는 변경하지 않는다.
   - `CollectClosedFileBus`
   - `MemoryBuildCoordinator`
   - `MemoryAssembler`
7. 구현 후에도 awake 이전 3분 pre-roll이 녹화 gap 없이 유지되는지 별도 확인한다.

## 설계 판단 포인트

- awake 시점에 녹화를 시작하는 방식은 이 계획의 대안이 아니다. 그렇게 하면 pre-roll 3분 요구를 만족할 수 없다.
- 감지용 프레임과 웹 미리보기용 프레임을 완전히 분리할지, collect 내부에서 파생 생성할지 먼저 결정한다.
- 카메라 세션 output 조합이 기기별로 달라질 수 있으므로, 최소 조합으로 시작하는 편이 안전하다.
- 감지용 FPS는 1fps 안팎을 기본값으로 검토한다.

## ring buffer에 대한 판단

- ring buffer 자체는 저장 방식 최적화일 수는 있어도, 이 문제의 근본 해법은 아니다.
- pre-roll 3분을 보장하려면 ring buffer도 결국 평소에 계속 인코딩하거나 최소한 동일한 수준의 연속 캡처를 유지해야 한다.
- 즉, `파일을 계속 남기느냐`가 `메모리/임시 버퍼에 잠시 보관하느냐`로 바뀌는 면은 있지만, 발열의 큰 축인 센서 구동과 인코딩 비용은 대부분 남는다.
- ring buffer는 저장 공간 정책이나 파일 관리 정책에는 도움이 될 수 있어도, 현 문서의 목표인 발열 저감에서는 보조 수단에 가깝다.
- 더 나아가 닫힌 collect 파일 기반 `memory` 조립 전제와 바로 맞지 않으므로, 도입하려면 `memory` 범위 결정과 flush 시점 설계까지 함께 바뀌어야 한다.

## 검증 계획

- 모션 감지 ON 상태에서 awake 판정이 기존과 크게 달라지지 않는지 확인한다.
- 웹 카메라 OFF 상태에서 감지용 경량 프레임만 생성되는지 로그 또는 계측으로 확인한다.
- 웹 카메라 ON 상태에서 스트림이 정상 동작하는지 확인한다.
- `memory` 영상 품질이 변경되지 않았는지 실제 생성 파일로 확인한다.
- awake가 발생했을 때 깨기 3분 전 구간이 실제 collect 파일에서 끊기지 않고 조립되는지 확인한다.
- 가능하면 관련 단위 테스트 추가 또는 기존 수면 관련 테스트 재실행.

## 리스크

- Camera2 output 조합 변경으로 특정 기기에서 세션 구성이 실패할 수 있다.
- 모션 threshold가 저해상도 프레임에서 달라져 민감도 재튜닝이 필요할 수 있다.
- 웹 스트림과 감지 경로를 섞어 설계하면 다시 결합도가 높아질 수 있다.
- 기대 효과를 과대평가하면, 연속 녹화가 남아 있는 한계 때문에 실제 발열 개선 폭이 기대보다 작을 수 있다.

## 완료 조건

- 녹화 품질 설정을 유지한 채 감지 경로의 디코드/인코드 비용이 줄어든다.
- `memory` 생성 범위와 품질이 유지된다.
- awake 이전 3분 pre-roll 보장이 유지된다.
- 카메라 직접 접근 주체가 `collect` 하나로 유지된다.
