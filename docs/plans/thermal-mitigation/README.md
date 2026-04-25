# Thermal Mitigation Plans

장시간 카메라/마이크 활성화로 인한 발열 문제를 완화하기 위한 작업 계획 모음이다.
다른 세션이 바로 이어서 작업할 수 있게 제안별로 문서를 분리했다.

## 공통 제약

- 카메라 하드웨어 직접 접근은 `collect`만 담당한다.
- `motion`, `webservice camera`, `memory(camera)`는 `collect` 산출물을 재사용해야 한다.
- 수동/자동 `memory(camera)`는 닫힌 `collect` 파일 범위만 사용해야 한다.
- 웹 UI 상태는 클라이언트 로컬 플래그가 아니라 서버 authoritative 상태를 기준으로 유지한다.

## 권장 진행 순서

1. `03-audio-cost-reduction.md`
2. `06-supplemental-low-risk-cleanups.md`
3. `04-last-resort-video-quality-tuning.md`
4. `05-periodic-pause-resume-risk-assessment.md`

완료 참고:

- `01-split-recording-and-detection-pipeline.md`
- `02-on-demand-web-preview.md`
- `07-sleep-off-input-gating.md`

## 진행 상태와 영향

- `01-split-recording-and-detection-pipeline.md`: 완료. 녹화 경로와 모션/preview 파생 경로가 분리되었고, `CameraMonitor`의 JPEG decode가 제거되었다.
- `02-on-demand-web-preview.md`: 완료. `webCameraEnabled`가 켜져 있어도 active subscriber가 없으면 preview JPEG 생성은 멈추고, idle 상태의 웹 preview 비용은 이제 남은 발열의 주된 변수에서 빠진다. 다만 카메라 input 자체의 `sleep off` 게이팅은 후속 `07`에서 마무리되었다.
- `03-audio-cost-reduction.md`: 직접 영향 있음. `07`로 `sleepEnabled=false` 경로의 오디오 collect는 이미 꺼졌으므로, 이제 이 문서는 주로 `sleepEnabled=true` 감시 중 남는 오디오 encode/amplitude 비용을 다룬다.
- `04-last-resort-video-quality-tuning.md`: 직접 영향 있음. `02`, `07` 이후에도 발열이 충분히 남는 경우, 그다음 큰 후보는 주로 `sleep on` 또는 active preview subscriber 상태의 recorder 비용일 가능성이 더 높다.
- `05-periodic-pause-resume-risk-assessment.md`: 직접 영향 없음. pre-roll과 closed collect file 전제는 그대로다.
- `06-supplemental-low-risk-cleanups.md`: 완료. `MicrophoneMonitor`는 active 상태 전이 또는 `30초` summary 시점에만 debug log를 남기고, `CollectAudioSource` amplitude 게시 주기는 `500ms`로 낮춰 남는 오디오/로그 주기 비용을 줄였다. stale timeout도 같은 설정 축으로 묶어 monitor 정합성을 유지한다.
- `07-sleep-off-input-gating.md`: 완료. `sleep off + viewer 없음`에서는 카메라/마이크 collect가 열리지 않게 되었고, preview subscriber 전이에 따라 collect input만 즉시 재평가된다. manual memory availability도 서버 authoritative 상태와 닫힌 collect 파일 기준으로 더 보수적으로 맞춰졌다.

## 문서 목록

- [01-split-recording-and-detection-pipeline.md](./01-split-recording-and-detection-pipeline.md)
- [02-on-demand-web-preview.md](./02-on-demand-web-preview.md)
- [03-audio-cost-reduction.md](./03-audio-cost-reduction.md)
- [04-last-resort-video-quality-tuning.md](./04-last-resort-video-quality-tuning.md)
- [05-periodic-pause-resume-risk-assessment.md](./05-periodic-pause-resume-risk-assessment.md)
- [06-supplemental-low-risk-cleanups.md](./06-supplemental-low-risk-cleanups.md)
- [07-sleep-off-input-gating.md](./07-sleep-off-input-gating.md)

## 빠른 판단 기준

- `memory` 영상 품질을 유지하면서 발열을 낮추는 첫 단계인 `01`은 이미 반영되었다.
- 실제 웹 시청 시간보다 idle 시간이 훨씬 길다면 `02`의 효과를 이미 받았다고 보고 다음 판단으로 넘어간다.
- `sleep off`인데 viewer도 없는데 발열이 느껴진다면, 먼저 `07`의 기대 동작과 어긋난 회귀인지 확인한다. 정상 동작 기준의 다음 최적화 우선순위는 `03`, `06`이다.
- 마이크 감지 품질 손실을 최소화하면서 바로 손댈 수 있는 것은 이제 `03`과 `06`이다.
- `04`는 `01`, `02`, `03`, `06`, `07` 이후에도 발열이 여전히 높고, 특히 `sleep on` 또는 active preview subscriber 상태에서 계속 높을 때만 진행한다.
- `05`는 현 구조와 가장 충돌하므로 구현보다 위험 평가 문서로 보는 편이 맞다.
