# Plan 03: Audio Cost Reduction

## 목표

아기 깨움 감지에 필요한 오디오 정보는 유지하면서, 마이크 녹음과 amplitude 샘플링 비용을 낮춘다.

## 현재 관찰

- `CollectAudioSource`는 AAC `96kbps / 16kHz`로 저장한다.
- amplitude는 250ms마다 `CollectAudioBus`에 게시된다.
- `MicrophoneMonitor`는 1초마다 latest snapshot을 읽어 active 여부를 계산한다.
- 즉, amplitude 발행 주기는 실제 소비 주기보다 빠르다.

## 관련 파일

- `app/src/main/java/com/dveamer/babysitter/collect/CollectAudioSource.kt`
- `app/src/main/java/com/dveamer/babysitter/collect/CollectAudioBus.kt`
- `app/src/main/java/com/dveamer/babysitter/monitor/MicrophoneMonitor.kt`
- 필요 시 `app/src/main/java/com/dveamer/babysitter/sleep/SleepForegroundService.kt`

## 범위

- 오디오 수집 주체는 계속 `collect`로 유지한다.
- 깨움 감지 로직은 `CollectAudioBus` latest snapshot을 계속 사용한다.
- `memory`용 오디오를 완전히 제거하지 않고 비용을 완화하는 수준만 다룬다.

## 작업 단계

1. 오디오 설정을 상수로 정리해 실험 가능한 구조로 바꾼다.
   - bitrate
   - sampling rate
   - amplitude publish interval
2. 1차 시도는 저장 품질보다 샘플링 주기 최적화에 둔다.
   - amplitude publish interval을 `500ms` 또는 `1000ms`로 늘린다.
   - `MicrophoneMonitor`의 stale timeout, hold poll과의 관계를 재검토한다.
3. 2차 시도로 인코딩 bitrate를 낮춘다.
   - 우선 후보는 `48kbps`
   - 필요 시 `32kbps`
4. 샘플링 rate는 `16kHz` 유지부터 시작한다.
   - 더 내리는 것은 음성/울음 대역 손실을 보고 결정한다.
5. 감지 민감도 회귀를 확인한다.
   - 조용한 울음
   - 짧은 울음 burst
   - lullaby 재생 직후

## 설계 판단 포인트

- amplitude 계산은 이미 `MediaRecorder.maxAmplitude` 기반이므로 인코딩 bitrate 변경 영향이 제한적일 수 있다.
- 실제 발열 개선 폭이 크지 않을 수 있으므로, 주기 최적화와 로그 최적화를 함께 보는 편이 낫다.
- 소리 감지 민감도 저하는 motion 경로와 함께 awake 판정에 영향을 줄 수 있다.

## 검증 계획

- 울음 감지가 기존과 유사하게 동작하는지 수동 재생 또는 실제 샘플로 확인한다.
- `AwakeDetector` 진입 시간이 크게 늘어나지 않는지 확인한다.
- `lullabyActive` 시 마이크 억제 흐름이 그대로 유지되는지 확인한다.
- 생성된 `memory` 영상의 오디오 품질이 사용 가능 수준인지 확인한다.

## 리스크

- 너무 공격적으로 bitrate를 낮추면 사용자 제공 오디오 품질이 떨어질 수 있다.
- amplitude 발행 주기를 과하게 늘리면 짧은 active burst를 놓칠 수 있다.
- 감도 보정 없이 설정만 낮추면 false negative가 증가할 수 있다.

## 완료 조건

- 마이크 경로의 백그라운드 비용이 줄어든다.
- 깨움 감지와 lullaby 억제 흐름에 회귀가 없다.
- `memory` 오디오가 여전히 사용자 제공 용도로 허용 가능한 수준이다.
