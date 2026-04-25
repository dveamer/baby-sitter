# Plan 04: Last-Resort Video Quality Tuning

## 목표

상위 최적화만으로 발열이 충분히 줄지 않을 때, `memory` 영상의 가독성을 유지하는 범위에서 녹화 비용을 낮춘다.

## 01 반영 후 상태

- `01-split-recording-and-detection-pipeline.md`가 먼저 반영되어, 모션 감지는 녹화 JPEG 경로와 분리된 저해상도 grayscale analysis frame을 사용한다.
- 따라서 이 문서는 이전보다 recorder 설정과 `memory` 품질 trade-off에 더 집중할 수 있다.
- 다만 같은 collect 카메라 세션 안에서 녹화와 analysis가 함께 도는 구조는 유지되므로, recorder tuning이 세션 안정성이나 간접적인 영상 특성에 미치는 영향은 여전히 확인해야 한다.

## 현재 관찰

- `CollectCameraSource`는 `640x480`, `20fps`, `3Mbps`, `H.264`로 녹화한다.
- `memory` 영상은 collect 비디오 파일을 그대로 이어붙이는 구조라, 이 설정 변화가 사용자 제공 영상 품질에 직접 반영된다.
- 모션 감지는 이미 별도 analysis frame 경로를 사용하므로, 녹화 품질 조정이 모션 threshold와 직접 1:1로 묶이지는 않는다.

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
- `01`이 먼저 반영된 만큼, 여기서는 남은 발열의 더 큰 비중이 실제 recorder 비용인지 확인한 뒤 들어가는 편이 좋다.

## 검증 계획

- wake 이전 3분 메모리 영상에서 아기 상태를 사람이 판단할 수 있는지 확인한다.
- 어두운 방과 실내 조명 환경 모두에서 비교한다.
- 모션 감지 경로는 이미 분리되어 있으므로 direct threshold 재튜닝 필요성은 낮지만, 녹화 설정 조정이 세션 안정성이나 노출/노이즈 특성에 간접 영향을 주지 않는지 sanity check 한다.
- 파일 크기, 생성 안정성, `MemoryAssembler` 조립 성공률을 확인한다.

## 리스크

- 품질 손실이 사용자 신뢰 하락으로 직결될 수 있다.
- bitrate만 낮춰도 어두운 환경 블록 노이즈가 커질 수 있다.
- fps 하향 시 "깨기 직전 움직임" 해석이 어려워질 수 있다.

## 완료 조건

- 명확한 품질 허용선이 문서화된다.
- 발열 개선과 품질 손실의 trade-off가 수치 또는 실물 비교로 정리된다.
- 상위 최적화가 부족할 때만 적용하는 마지막 단계로 남는다.
