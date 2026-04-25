# Thermal Mitigation Plans

장시간 카메라/마이크 활성화로 인한 발열 문제를 완화하기 위한 작업 계획 모음이다.
다른 세션이 바로 이어서 작업할 수 있게 제안별로 문서를 분리했다.

## 공통 제약

- 카메라 하드웨어 직접 접근은 `collect`만 담당한다.
- `motion`, `webservice camera`, `memory(camera)`는 `collect` 산출물을 재사용해야 한다.
- 수동/자동 `memory(camera)`는 닫힌 `collect` 파일 범위만 사용해야 한다.
- 웹 UI 상태는 클라이언트 로컬 플래그가 아니라 서버 authoritative 상태를 기준으로 유지한다.

## 권장 진행 순서

1. `01-split-recording-and-detection-pipeline.md`
2. `02-on-demand-web-preview.md`
3. `03-audio-cost-reduction.md`
4. `06-supplemental-low-risk-cleanups.md`
5. `04-last-resort-video-quality-tuning.md`
6. `05-periodic-pause-resume-risk-assessment.md`

## 문서 목록

- [01-split-recording-and-detection-pipeline.md](./01-split-recording-and-detection-pipeline.md)
- [02-on-demand-web-preview.md](./02-on-demand-web-preview.md)
- [03-audio-cost-reduction.md](./03-audio-cost-reduction.md)
- [04-last-resort-video-quality-tuning.md](./04-last-resort-video-quality-tuning.md)
- [05-periodic-pause-resume-risk-assessment.md](./05-periodic-pause-resume-risk-assessment.md)
- [06-supplemental-low-risk-cleanups.md](./06-supplemental-low-risk-cleanups.md)

## 빠른 판단 기준

- `memory` 영상 품질을 유지하면서 발열을 낮추고 싶다면 `01`이 1순위다.
- 웹 카메라를 자주 켜두지만 실제 시청 시간은 짧다면 `02` 효과가 크다.
- 마이크 감지 품질 손실을 최소화하면서 바로 손댈 수 있는 것은 `03`과 `06`이다.
- `04`는 상위 안으로 부족할 때만 진행한다.
- `05`는 현 구조와 가장 충돌하므로 구현보다 위험 평가 문서로 보는 편이 맞다.
