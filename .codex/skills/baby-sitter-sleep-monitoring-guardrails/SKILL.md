---
name: baby-sitter-sleep-monitoring-guardrails
description: 이 저장소에서 `sleep`, `sound`, `motion`, `sooth`, 자장가 재생, `collect`, `memory` 상호작용을 수정하거나 검토할 때 사용하는 가드레일이다. `SleepForegroundService`, 모니터 구현, soothing 흐름, collect 입력 조건, memory 생성 범위를 건드리는 작업에서 사용한다. 카메라 단일 접근 원칙, sound와 motion의 상호 영향, 자장가-마이크 자기 피드백 방지를 함께 점검해야 할 때 트리거한다.
---

# 수면 모니터링 가드레일

수면 감지 파이프라인을 수정할 때 같이 봐야 하는 제약과 점검 순서를 정리한다. 한 축만 수정해도 다른 감지 축이나 자장가 재생 흐름이 깨지기 쉬운 작업에서 사용한다.

## 핵심 원칙

- 카메라 하드웨어 직접 접근은 `collect`만 맡기기.
- `motion`, `webservice camera`, `memory(camera)`는 `collect` 산출물을 재사용하기.
- `sound`와 `motion` 중 한쪽을 수정해도 다른 쪽의 감지, awake 판정, `sooth`, 자장가 흐름이 유지되는지 확인하기.
- 자장가 소리가 다시 마이크에 잡혀 `sooth -> 재생 -> 재감지 -> 재sooth` 루프가 생기지 않게 하기.

## 먼저 볼 파일

- `app/src/main/java/com/dveamer/babysitter/sleep/SleepForegroundService.kt`를 먼저 읽기. 모니터 병합, 마이크 억제, `sooth`, 자장가 정지, memory 트리거가 모두 여기서 연결된다.
- `app/src/main/java/com/dveamer/babysitter/collect/CollectRecorderCoordinator.kt`를 읽기. 카메라와 오디오 collect 입력 활성 조건의 기준점이다.
- `app/src/main/java/com/dveamer/babysitter/sleep/MicrophoneMusicController.kt`를 읽기. 같은 active 구간에서 자장가 시작이 반복되지 않아야 한다.
- `app/src/main/java/com/dveamer/babysitter/sleep/WakeMemoryManager.kt`를 읽기. 자장가가 켜져 있으면 sleep stable 타이머가 초기화된다.
- `app/src/main/java/com/dveamer/babysitter/collect/CollectClosedFileBus.kt`와 `app/src/main/java/com/dveamer/babysitter/sleep/MemoryAssembler.kt`를 읽기. memory 생성 범위가 닫힌 collect 파일 기준인지 확인한다.

## 작업 순서

1. 수정 대상이 카메라 소유권 변경인지, 감지 임계값 변경인지, 상태 전이 변경인지 먼저 구분하기.
2. 카메라 입력이 필요하면 새 직접 접근 경로를 추가하지 말고 `collect` 결과를 재사용하는 설계부터 검토하기.
3. `sound`, `motion`, `sooth`, 자장가 중 하나를 수정하면 `SleepForegroundService`의 마이크 분기와 카메라 분기를 둘 다 다시 읽기.
4. 자장가나 `sooth` 로직을 수정하면 마이크 억제 조건과 억제 시간 상수를 같이 점검하기.
5. collect 또는 memory 범위를 수정하면 닫히지 않은 파일을 memory에 포함하지 않는지 확인하기.

## 반드시 확인할 체크포인트

- `sound`만 수정했더라도 `motion` 경로의 awake 판정과 후속 `sooth` 흐름이 유지되는지 확인하기.
- `motion`만 수정했더라도 `sound` 경로의 마이크 억제와 자장가 시작 조건이 유지되는지 확인하기.
- 자장가 재생 중에는 마이크 신호가 직접 재트리거를 만들지 않는지 확인하기.
- 자장가 종료 직후에도 재감지 방어 시간이 필요한지 확인하기.
- 외부 소리 없이 자장가만으로 반복 재생 루프가 생기지 않는지 확인하기.
- 자장가가 active일 때 memory용 sleep stable 타이머가 초기화되는지 확인하기.
- memory 생성 범위가 최근에 닫힌 collect 비디오 파일 범위를 넘지 않는지 확인하기.

## 같이 볼 테스트

- `app/src/test/java/com/dveamer/babysitter/sleep/MicrophoneMusicControllerTest.kt`로 자장가 시작 중복 방지 조건을 확인하기.
- `app/src/test/java/com/dveamer/babysitter/sleep/PlaybackInactivityControllerTest.kt`로 자장가 정지 grace 동작을 확인하기.
- `app/src/test/java/com/dveamer/babysitter/sleep/WakeMemoryManagerTest.kt`로 lullaby active 시 memory 타이머 초기화를 확인하기.
- `app/src/test/java/com/dveamer/babysitter/collect/CollectClosedFileBusTest.kt`로 collect 종료 파일 기준 동작을 확인하기.

## 완료 전 최소 검증

- 가능하면 `./gradlew :app:testDebugUnitTest`를 실행하기.
- 시간이 제한되면 관련 테스트만이라도 우선 실행하기.
- 테스트가 없다면 `SleepForegroundService`의 마이크 억제, awake 진입, lullaby 종료, memory 트리거 경로를 수동으로 다시 추적하기.
