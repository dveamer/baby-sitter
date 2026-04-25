# Plan 04: Last-Resort Video Quality Tuning

## 목표

상위 최적화만으로 발열이 충분히 줄지 않을 때, `memory` 영상의 가독성을 유지하는 범위에서 녹화 비용을 낮춘다.

## 현재 관찰

- `CollectCameraSource`는 `640x480`, `20fps`, `3Mbps`, `H.264`로 녹화한다.
- `memory` 영상은 collect 비디오 파일을 그대로 이어붙이는 구조라, 이 설정 변화가 사용자 제공 영상 품질에 직접 반영된다.

## 관련 파일

- `app/src/main/java/com/dveamer/babysitter/collect/CollectCameraSource.kt`
- `app/src/main/java/com/dveamer/babysitter/sleep/MemoryAssembler.kt`
- 필요 시 `app/src/main/java/com/dveamer/babysitter/sleep/MemoryBuildCoordinator.kt`

## 범위

- 해상도보다 bitrate, fps 조정부터 먼저 검토한다.
- `memory` 생성 범위와 파일 회전 구조는 바꾸지 않는다.
- 런타임에서 쉽게 비교 가능한 상수 또는 profile 구조를 우선 고려한다.

## 작업 단계

1. 현재 녹화 설정을 상수 묶음 또는 profile로 정리한다.
   - baseline
   - candidate A
   - candidate B
2. 1차 후보를 정의한다.
   - `3Mbps / 20fps` baseline
   - `2Mbps / 15fps`
   - `1.5Mbps / 15fps`
3. 해상도는 첫 단계에서 유지한다.
   - `640x480`보다 먼저 bitrate/fps 영향부터 확인한다.
4. 각 후보로 실제 `memory` 생성 파일을 비교한다.
   - 얼굴/몸 움직임 식별 가능성
   - 어두운 환경 노이즈
   - 짧은 움직임 blur
5. 품질 허용선이 확인되면 기본값 또는 기기별 fallback 전략을 결정한다.

## 설계 판단 포인트

- `fps`를 너무 낮추면 움직임 확인이 둔해질 수 있다.
- 해상도는 이미 높지 않으므로 추가 하향 시 체감 손실이 빠르게 커질 수 있다.
- 발열 개선 폭이 불확실하므로, 상위 안이 먼저 적용된 뒤 측정하는 편이 좋다.

## 검증 계획

- wake 이전 3분 메모리 영상에서 아기 상태를 사람이 판단할 수 있는지 확인한다.
- 어두운 방과 실내 조명 환경 모두에서 비교한다.
- 모션 감지가 같은 녹화 설정을 참조한다면 threshold 변화가 필요한지 확인한다.
- 파일 크기, 생성 안정성, `MemoryAssembler` 조립 성공률을 확인한다.

## 리스크

- 품질 손실이 사용자 신뢰 하락으로 직결될 수 있다.
- bitrate만 낮춰도 어두운 환경 블록 노이즈가 커질 수 있다.
- fps 하향 시 "깨기 직전 움직임" 해석이 어려워질 수 있다.

## 완료 조건

- 명확한 품질 허용선이 문서화된다.
- 발열 개선과 품질 손실의 trade-off가 수치 또는 실물 비교로 정리된다.
- 상위 최적화가 부족할 때만 적용하는 마지막 단계로 남는다.
