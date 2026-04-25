# Plan 05: Periodic Pause/Resume Risk Assessment

## 목표

장시간 동작 중 주기적으로 카메라/마이크를 쉬게 하는 안을 검토하되, 왜 현 구조와 충돌하는지 명확히 정리한다.

## 현재 관찰

- `memory`는 닫힌 분 단위 collect 파일만 사용한다.
- 최신 closed video end를 기준으로 build 가능 범위를 판단한다.
- collect 파일 중간에 gap이 생기면 `memory`에도 그대로 공백이 생긴다.
- 따라서 "1분 쉬었다 재개"는 pre-roll 요구와 직접 충돌한다.

## 관련 파일

- `app/src/main/java/com/dveamer/babysitter/collect/CollectCameraSource.kt`
- `app/src/main/java/com/dveamer/babysitter/collect/CollectAudioSource.kt`
- `app/src/main/java/com/dveamer/babysitter/sleep/MemoryBuildCoordinator.kt`
- `app/src/main/java/com/dveamer/babysitter/sleep/MemoryAssembler.kt`
- `app/src/main/java/com/dveamer/babysitter/sleep/WakeMemoryManager.kt`

## 범위

- 이 문서는 기본적으로 구현 계획이 아니라 보류안 검토 계획이다.
- 현 구조에서 바로 구현하지 않고, 필요한 구조 변경 규모를 먼저 드러내는 데 목적이 있다.

## 작업 단계

1. gap이 `memory` 결과에 미치는 영향을 명시적으로 재현한다.
   - 1개 분 파일 누락
   - 여러 분 파일 누락
   - awake 직전 구간 누락
2. awake pre-roll 요구와의 충돌을 문서화한다.
   - `WakeMemoryManager.PRE_ROLL_MS`
   - `latestClosedVideoEndMs` 전제
3. 주기 pause를 하려면 필요한 구조 변경을 정리한다.
   - pause 중에도 저전력 prebuffer 유지가 필요한지
   - gap을 메타데이터로 사용자에게 노출해야 하는지
   - detection blind window를 허용할지
4. 구현을 강행할 경우 선행 조건을 정의한다.
   - `memory`가 gap을 표현하거나 보완할 수 있어야 함
   - awake 감지 정책이 blind window를 허용해야 함
   - UI와 사용자 설명이 바뀌어야 함
5. 최종적으로 go/no-go 결론을 남긴다.

## 설계 판단 포인트

- 현재 제품 요구가 "깨기 전 3분 제공"인 이상, blind window가 생기는 설계는 기본 요구를 위반할 가능성이 높다.
- pause 중에 별도 저전력 buffer를 두면 결국 또 다른 카메라 경로를 만들게 되어 가드레일과 충돌할 수 있다.
- 감지와 기록을 둘 다 잠시 멈추는 안은 발열은 줄이지만 기능 신뢰도를 크게 해친다.

## 검증 계획

- collect gap이 생긴 상태에서 수동/자동 memory 생성이 어떻게 보이는지 재현한다.
- awake 직전 gap이 있을 때 사용자 경험이 허용 가능한지 제품 관점에서 판단한다.
- 필요 시 샘플 파일 세트를 만들어 `MemoryAssembler` 결과를 비교한다.

## 리스크

- 이 안은 구현을 시작하는 순간 구조 변경 범위가 커질 가능성이 높다.
- blind window 때문에 핵심 기능 신뢰를 잃을 수 있다.
- gap을 보완하려고 하면 사실상 새로운 buffering architecture가 필요해질 수 있다.

## 완료 조건

- "현 구조에서는 비추천" 또는 "구조 재설계 후 재검토" 같은 결론이 분명히 남는다.
- 다른 세션이 이 안을 다시 꺼냈을 때 즉시 제약을 이해할 수 있다.
