# Thermal Mitigation Plans

장시간 카메라/마이크 활성화로 인한 발열 문제를 완화하기 위한 작업 계획 모음이다.
다른 세션이 바로 이어서 작업할 수 있게 제안별로 문서를 분리했다.

## 공통 제약

- 카메라 하드웨어 직접 접근은 `collect`만 담당한다.
- `motion`, `webservice camera`, `memory(camera)`는 `collect` 산출물을 재사용해야 한다.
- 수동/자동 `memory(camera)`는 닫힌 `collect` 파일 범위만 사용해야 한다.
- 웹 UI 상태는 클라이언트 로컬 플래그가 아니라 서버 authoritative 상태를 기준으로 유지한다.

## 권장 진행 순서

1. `02-on-demand-web-preview.md`
2. `03-audio-cost-reduction.md`
3. `06-supplemental-low-risk-cleanups.md`
4. `04-last-resort-video-quality-tuning.md`
5. `05-periodic-pause-resume-risk-assessment.md`

## 진행 상태와 영향

- `01-split-recording-and-detection-pipeline.md`: 완료. 녹화 경로와 모션/preview 파생 경로가 분리되었고, `CameraMonitor`의 JPEG decode가 제거되었다.
- `02-on-demand-web-preview.md`: 직접 영향 있음. 이제 목표는 frame bus 분리가 아니라, 이미 분리된 preview JPEG 경로를 subscriber demand 기준으로 켜고 끄는 것이다.
- `03-audio-cost-reduction.md`: 직접 영향 없음. 계획 내용 유지.
- `04-last-resort-video-quality-tuning.md`: 직접 영향 있음. 모션 감지가 녹화 품질과 덜 결합되었으므로 recorder 품질 trade-off를 더 독립적으로 평가할 수 있다.
- `05-periodic-pause-resume-risk-assessment.md`: 직접 영향 없음. pre-roll과 closed collect file 전제는 그대로다.
- `06-supplemental-low-risk-cleanups.md`: 직접 영향 있음. 카메라 hot path 정리는 `01`에서 처리되었으므로, 남은 범위는 주로 오디오/로그/주기 작업 정리다.

## 문서 목록

- [01-split-recording-and-detection-pipeline.md](./01-split-recording-and-detection-pipeline.md)
- [02-on-demand-web-preview.md](./02-on-demand-web-preview.md)
- [03-audio-cost-reduction.md](./03-audio-cost-reduction.md)
- [04-last-resort-video-quality-tuning.md](./04-last-resort-video-quality-tuning.md)
- [05-periodic-pause-resume-risk-assessment.md](./05-periodic-pause-resume-risk-assessment.md)
- [06-supplemental-low-risk-cleanups.md](./06-supplemental-low-risk-cleanups.md)

## 빠른 판단 기준

- `memory` 영상 품질을 유지하면서 발열을 낮추는 첫 단계인 `01`은 이미 반영되었다.
- 웹 카메라를 자주 켜두지만 실제 시청 시간은 짧다면 `02` 효과가 크다.
- 마이크 감지 품질 손실을 최소화하면서 바로 손댈 수 있는 것은 `03`과 `06`이다.
- `04`는 `01`, `02`, `03`, `06` 이후에도 recorder 비용이 주요 원인일 때만 진행한다.
- `05`는 현 구조와 가장 충돌하므로 구현보다 위험 평가 문서로 보는 편이 맞다.
