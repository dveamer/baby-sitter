# Plan 06: Supplemental Low-Risk Cleanups

## 목표

주요 구조 변경과 별개로, 장시간 백그라운드 서비스에서 불필요한 주기 작업과 로그를 줄여 작은 발열 개선을 노린다.

## 현재 관찰

- `MicrophoneMonitor`는 1초마다 debug log를 남긴다.
- 오디오 amplitude는 monitor 소비 주기보다 빠르게 게시된다.
- 이런 항목은 주된 발열 원인은 아니지만, 장시간 실행에서는 누적 비용이 된다.

## 관련 파일

- `app/src/main/java/com/dveamer/babysitter/monitor/MicrophoneMonitor.kt`
- `app/src/main/java/com/dveamer/babysitter/collect/CollectAudioSource.kt`
- 필요 시 `app/src/main/java/com/dveamer/babysitter/collect/CollectRecorderCoordinator.kt`

## 범위

- 기능 동작을 바꾸지 않는 작은 최적화만 다룬다.
- 감지 판단 로직 자체를 바꾸기보다 운영 비용을 줄이는 방향으로 제한한다.

## 작업 단계

1. 고빈도 debug log를 정리한다.
   - `LOG_EVERY_N_POLLS` 상향
   - build type 또는 debug flag 조건부 로깅 적용 여부 검토
2. monitor 소비 주기와 발행 주기의 불균형을 줄인다.
   - audio amplitude 게시 주기 재검토
   - stale timeout과 함께 정합성 확인
3. hot loop 후보를 추가로 점검한다.
   - 짧은 sleep interval
   - 불필요한 객체 생성
   - 고빈도 예외 로그
4. 본 계획은 `03-audio-cost-reduction.md`와 겹치는 부분이 있으므로, 실제 구현 시 한 묶음으로 처리할지 결정한다.

## 설계 판단 포인트

- 로그 감소는 성능보다 디버깅 편의성과 trade-off가 있다.
- 운영 중 필요한 최소 진단 정보는 남기되, 매초 로그는 피하는 편이 낫다.
- 작은 최적화만으로는 문제 해결이 안 되므로 상위 안을 대체하면 안 된다.

## 검증 계획

- 로그 양이 유의미하게 줄었는지 확인한다.
- 모니터 active 상태 전환 디버깅이 여전히 가능한지 확인한다.
- 오디오 감지 회귀가 없는지 간단한 수동 테스트를 수행한다.

## 리스크

- 로그를 너무 줄이면 현장 디버깅이 어려워질 수 있다.
- 주기 정리가 감지 민감도에 간접 영향을 줄 수 있다.

## 완료 조건

- 장시간 실행에서 불필요한 운영 비용이 줄어든다.
- 기능 회귀 없이 적용 가능한 저위험 패치로 남는다.
