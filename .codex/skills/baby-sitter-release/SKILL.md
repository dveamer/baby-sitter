---
name: baby-sitter-release
description: 이 저장소에서 안드로이드 앱 릴리즈와 Google Play 배포를 준비할 때 사용하는 skill이다. `app/build.gradle.kts`의 `versionCode`, `versionName`을 날짜 기반 `yyyymmddNN` 규칙으로 올리고, `bundleRelease`를 실행하고, 결과 폴더를 열고, `app/build.gradle.kts` 수정만 별도 git commit 하고 release tag 를 만들고, 같은 날짜의 여러 배포는 하나의 묶음으로 보고 당일 이전 마지막 release tag 이후 변경을 기준으로 스토어용 릴리즈 노트를 한글과 영문으로 작성하고, 그 노트를 함께 Google Play track 업로드와 일반적인 경우 개시 요청까지 진행할 때 사용한다.
---

# 베이비시터 릴리즈

이 skill은 저장소 전용 안드로이드 릴리즈 절차를 정리한다. 버전 갱신, AAB 생성, 버전 파일 commit, release tag 생성, 당일 누적 기준 릴리즈 범위 확인, 스토어용 릴리즈 노트 작성, Google Play 업로드까지 한 흐름으로 다룬다.

## 기본 명령

- 저장소 루트에서 `bash scripts/release_bundle.sh`를 실행하기.
- 이 스크립트는 오늘 날짜 기준 다음 버전을 계산하고 `app/build.gradle.kts`의 `versionCode`, `versionName`을 함께 갱신한다.
- 같은 날짜에 첫 배포면 `yyyymmdd00`, 같은 날짜 재배포면 마지막 두 자리를 1씩 증가시킨다.
- 빌드 성공 후 `app/build.gradle.kts`만 별도 commit 하고 annotated tag 를 생성한다.
- 같은 날짜에 여러 번 배포하더라도 릴리즈 노트 기준점은 당일 첫 배포 기준으로 유지한다.
- 빌드가 끝나면 `app/build/outputs/bundle/release/`를 열고 `release-info.txt`를 남긴다.
- Google Play 업로드는 별도로 `bash scripts/publish_play_release.sh`를 실행한다.

## 버전 규칙

- `versionCode`와 `versionName`은 항상 같은 값으로 유지하기.
- 형식은 `yyyymmddNN`으로 유지하기.
- 현재 버전이 오늘 날짜보다 이전이면 `NN`은 `00`으로 시작하기.
- 현재 버전이 이미 오늘 날짜라면 `NN`만 증가시키기.

## commit / tag 규칙

- 기본 commit 메시지는 `릴리즈 <version>` 형식을 사용하기.
- 기본 tag 이름은 `release-<version>` 형식을 사용하기.
- commit 은 `app/build.gradle.kts`만 포함하기.
- 직전 릴리즈 정보는 먼저 가장 최근 `release-*` tag 를 찾고, 태그가 없을 때만 `app/build.gradle.kts`의 마지막 버전 변경 커밋으로 fallback 하기.

## 당일 누적 릴리즈 노트 규칙

- `2026030700`, `2026030701`, `2026030702`처럼 같은 날짜에 여러 번 배포하면 릴리즈 노트는 모두 같은 기준점에서 작성하기.
- 기준점은 `즉시 이전 tag`가 아니라 `당일 이전 마지막 release tag`로 잡기.
- 예를 들어 `release-2026030200` 다음에 `release-2026030700`, `release-2026030701`이 나가면 `2026030701`의 릴리즈 노트도 `release-2026030200`과의 차이로 작성하기.
- 스크립트는 같은 날짜 재배포 시 이전 같은 날짜 tag에 저장된 `release_notes_base_*` 정보를 우선 재사용한다.

## 릴리즈 노트 작성 절차

1. `app/build/outputs/bundle/release/release-info.txt`에서 `release_notes_base_tag`, `release_notes_base_ref`, `release_notes_end`를 확인하기.
2. `git log --oneline <release_notes_base_ref>..<release_notes_end>`와 `git diff --stat <release_notes_base_ref>..<release_notes_end>`로 변경 범위를 확인하기.
3. 스토어 릴리즈 노트에는 사용자 관점 변화만 남기기.
4. 버전 변경 commit, release script, skill, 문서 추가 같은 저장소 운영 변경은 제외하기.
5. 아래 형식으로 영문과 한글을 함께 제공하기.

```text
<en-US>
English release notes
</en-US>
<ko-KR>
한국어 릴리즈 노트
</ko-KR>
```

- 완성한 노트는 기본적으로 `app/build/outputs/bundle/release/release-notes.txt`에 저장하기.
- `bash scripts/write_play_release_notes.sh` 또는 `bash scripts/publish_play_release.sh`가 위 파일을 읽어 Google Play metadata 형식으로 변환하기.

## Google Play 배포 절차

- 먼저 [google-play-publish.md](./references/google-play-publish.md)를 읽고 Service Account 권한과 로컬 인증이 준비됐는지 확인하기.
- 프로젝트에는 `com.github.triplet.play` 플러그인이 연결되어 있으므로 `:app:publishReleaseBundle`로 업로드하기.
- 전체 배포 기본 명령은 `PLAY_TRACK=production PLAY_RELEASE_STATUS=completed bash scripts/publish_play_release.sh` 이다.
- staged rollout 은 `PLAY_RELEASE_STATUS=inProgress`, `PLAY_USER_FRACTION=<0~1>`을 함께 설정하기.
- 내부 테스트나 다른 track 으로 올릴 때는 `PLAY_TRACK`만 바꾸기.
- `scripts/publish_play_release.sh`는 release-info, release-notes, Service Account JSON 을 확인하고, Google Play release notes 파일을 만들고, 업로드 뒤 `app/build/outputs/bundle/release/play-publish-info.txt`에 결과를 남긴다.
- 같은 track 의 기존 메타데이터 파일이 있으면 업로드 후 원복한다.

## 검토 제출 제한

- 일반적인 상태에서는 Play API commit 으로 업로드와 개시 요청이 같이 진행된다.
- 현재 저장소에서 검증한 GPP 3.12.1 task 옵션에는 `changesNotSentForReview` 가 없으므로, Google Play 가 자동 검토 제출 불가 상태를 요구하면 Console UI 수동 단계가 남을 수 있다.
- 사용자가 "개시 요청까지 자동화"를 요구하더라도, 이 예외는 Google Play 정책/상태 제약과 현재 플러그인 옵션 범위로 설명하기.

## 이 저장소에서 우선 볼 변경 축

- 웹 서비스의 백그라운드 동작과 카메라/모션 연동
- sound 민감도 조정
- collect 기반 수면 기록 수집과 memory 생성
- 마이크 감지와 자장가 시작/중복 방지
- 비활성 상태에서 자장가를 자연스럽게 종료하는 제어

## 완료 전 확인

- `app/build.gradle.kts`의 버전이 기대값으로 바뀌었는지 확인하기.
- `app/build/outputs/bundle/release/`에 `.aab`가 생성됐는지 확인하기.
- release commit 과 release tag 가 생성됐는지 확인하기.
- 릴리즈 노트는 `release_notes_base_ref` 이후 앱 기능 변경만 기준으로 작성하기.
- Google Play 배포를 진행했다면 `app/build/outputs/bundle/release/play-publish-info.txt`와 `app/build/outputs/bundle/release/play-release-notes/`도 확인하기.
