# 코딩 작업 주의사항

## 항상 적용되는 제약

- 카메라 하드웨어 직접 접근은 `collect`만 담당한다.
- `motion`, `webservice camera`, `memory(camera)`는 `collect`가 만든 영상 파일을 기준으로 동작해야 한다.
- `collect` 외 다른 기능에서 카메라를 직접 열도록 바꾸는 수정은 구조 변경으로 보고 신중하게 검토한다.

## 관련 skill

- `sleep`, `sound`, `motion`, `sooth`, 자장가, `collect`, `memory` 상호작용을 수정할 때는 `.codex/skills/baby-sitter-sleep-monitoring-guardrails/SKILL.md`를 함께 본다.
