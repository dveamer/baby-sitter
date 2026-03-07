# 코딩 작업 주의사항

## 항상 적용되는 제약

- 카메라 하드웨어 직접 접근은 `collect`만 담당한다.
- `motion`, `webservice camera`, `memory(camera)`는 `collect`가 만든 영상 파일을 기준으로 동작해야 한다.
- `collect` 외 다른 기능에서 카메라를 직접 열도록 바꾸는 수정은 구조 변경으로 보고 신중하게 검토한다.
- 수동 `memory(camera)` 저장도 닫힌 `collect` 파일 범위만 사용해야 하며, 아직 닫히지 않은 현재/미래 분 파일을 억지로 포함하면 안 된다.
- 웹이나 원격 UI에서 수동 `memory` 저장 버튼 상태를 다룰 때는 클라이언트 로컬 플래그를 믿지 말고 서버가 내려주는 진행 상태와 가능 여부를 기준으로 동작해야 한다.

## 관련 skill

- `sleep`, `sound`, `motion`, `sooth`, 자장가, `collect`, `memory` 상호작용을 수정할 때는 `.codex/skills/baby-sitter-sleep-monitoring-guardrails/SKILL.md`를 함께 본다.
