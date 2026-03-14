# baby-sitter

이 저장소의 안드로이드 릴리즈와 Google Play 배포는 로컬 스크립트 기준으로 진행한다. 핵심 흐름은 아래 두 단계다.

1. `bash scripts/release_bundle.sh`
2. `bash scripts/publish_play_release.sh`

첫 번째 스크립트는 버전 증가, AAB 생성, `app/build.gradle.kts` 커밋, release tag 생성까지 처리한다. 두 번째 스크립트는 생성된 AAB와 한영 출시 노트를 이용해 Google Play track 업로드를 수행한다.

## 배포 전제 조건

- Play Console에 앱이 이미 등록되어 있어야 한다.
- 신규 앱이면 첫 업로드는 Play Console에서 수동으로 한 번 끝내야 한다.
- Google Cloud Project에서 `Google Play Developer API`를 활성화해야 한다.
- 로컬 빌드용 Android release keystore가 준비되어 있어야 한다.
- Google Play 업로드용 Service Account JSON이 준비되어 있어야 한다.

## 릴리즈 서명 설정

release 빌드는 아래 환경변수를 읽는다.

| 변수 | 필수 여부 | 설명 |
| --- | --- | --- |
| `ANDROID_KEYSTORE_PATH` | 필수 | release keystore 파일 경로 |
| `ANDROID_BABYSITTER_KEY_ALIAS` | 필수 | keystore alias |
| `ANDROID_KEYSTORE_PSWD` | 필수 | keystore store password |
| `ANDROID_BABYSITTER_KEY_PSWD` | 필수 | key password |

예시:

```bash
export ANDROID_KEYSTORE_PATH="$HOME/.config/baby-sitter/babysitter-release.jks"
export ANDROID_BABYSITTER_KEY_ALIAS="babysitter"
export ANDROID_KEYSTORE_PSWD="..."
export ANDROID_BABYSITTER_KEY_PSWD="..."
```

## Google Play Service Account 확보

`PLAY_SERVICE_ACCOUNT_JSON_PATH`는 Google Play 업로드용 Service Account JSON 파일 경로다. 현재 저장소 스크립트는 이 경로를 읽어 내부에서 `ANDROID_PUBLISHER_CREDENTIALS`로 변환한다.

### 1. Google Cloud에서 Service Account 만들기

1. Google Cloud Console에서 배포용 프로젝트를 선택한다.
2. `Google Play Developer API`를 활성화한다.
3. `IAM & Admin > Service Accounts`에서 새 Service Account를 만든다.
4. `Keys > Add key > Create new key > JSON`으로 JSON 키를 발급받는다.

조직 정책 때문에 키 생성이 막히면 관리자에게 Service Account key creation 예외가 필요한지 확인한다.

### 2. Play Console에 권한 부여하기

1. Play Console `Users and permissions`에서 방금 만든 Service Account 이메일을 초대한다.
2. 실제 배포할 앱에 접근 권한을 준다.
3. 대상 track에 배포할 수 있는 release 권한과 필요한 스토어 수정 권한을 함께 부여한다.

### 3. 로컬에 안전하게 저장하기

JSON 키는 저장소 밖에 두고 git에 포함하지 않는다.

```bash
mkdir -p "$HOME/.config/baby-sitter"
mv ~/Downloads/google-play-publisher.json "$HOME/.config/baby-sitter/google-play-publisher.json"
chmod 600 "$HOME/.config/baby-sitter/google-play-publisher.json"
```

### 4. 환경변수로 세팅하기

현재 shell 세션에서:

```bash
export PLAY_SERVICE_ACCOUNT_JSON_PATH="$HOME/.config/baby-sitter/google-play-publisher.json"
```

매번 입력하기 싫으면 `~/.zshrc` 또는 `~/.zprofile`에 같은 `export`를 추가한다.

대안으로 `ANDROID_PUBLISHER_CREDENTIALS`에 JSON 본문 전체를 직접 넣어도 되지만, 운영은 `PLAY_SERVICE_ACCOUNT_JSON_PATH`를 권장한다.

```bash
export ANDROID_PUBLISHER_CREDENTIALS="$(cat "$HOME/.config/baby-sitter/google-play-publisher.json")"
```

## Google Play 배포 관련 환경변수

`scripts/publish_play_release.sh`는 아래 값을 읽는다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `PLAY_SERVICE_ACCOUNT_JSON_PATH` | 없음 | Google Play Service Account JSON 파일 경로 |
| `ANDROID_PUBLISHER_CREDENTIALS` | 없음 | JSON 본문 전체. 위 경로 변수 대신 사용 가능 |
| `PLAY_TRACK` | `production` | 업로드 대상 track. 예: `production`, `internal` |
| `PLAY_RELEASE_STATUS` | `completed` | 릴리즈 상태. staged rollout이면 `inProgress` 사용 |
| `PLAY_USER_FRACTION` | 없음 | staged rollout 비율. 예: `0.1` |
| `PLAY_UPDATE_PRIORITY` | 없음 | optional, 정수 0-5 |
| `PLAY_COMMIT` | `true` | `false`면 `--no-commit`로 실행 |
| `PLAY_METADATA_FILE` | `app/build/outputs/bundle/release/release-info.txt` | `release_bundle.sh` 산출 메타데이터 파일 |
| `PLAY_RELEASE_NOTES_FILE` | `app/build/outputs/bundle/release/release-notes.txt` | 한영 출시 노트 원본 파일 |
| `PLAY_PUBLISH_INFO_FILE` | `app/build/outputs/bundle/release/play-publish-info.txt` | Play 업로드 결과 요약 파일 |
| `PLAY_METADATA_DIR` | `app/src/main/play` | GPP metadata 작업 디렉터리 |
| `PLAY_RELEASE_NOTES_ARCHIVE_DIR` | `app/build/outputs/bundle/release/play-release-notes` | track별 출시 노트 보관 디렉터리 |

## 출시 노트 형식

출시 노트는 아래 형식으로 작성한다.

```text
<en-US>
English release notes
</en-US>
<ko-KR>
한국어 릴리즈 노트
</ko-KR>
```

기본 파일 경로는 `app/build/outputs/bundle/release/release-notes.txt`다.

`bash scripts/write_play_release_notes.sh`는 위 파일을 읽어 다음 파일을 만든다.

- `app/src/main/play/release-notes/en-US/<track>.txt`
- `app/src/main/play/release-notes/ko-KR/<track>.txt`
- `app/build/outputs/bundle/release/play-release-notes/...`

## 권장 배포 순서

### 1. 번들 릴리즈 만들기

```bash
bash scripts/release_bundle.sh
```

산출물:

- `app/build/outputs/bundle/release/*.aab`
- `app/build/outputs/bundle/release/release-info.txt`
- `app/build/outputs/bundle/release/release-notes.txt` 에 쓸 기준 정보

### 2. 출시 노트 작성하기

`release-info.txt`의 `release_notes_base_ref`, `release_notes_end`를 기준으로 사용자 관점의 변경만 한영으로 정리해 `app/build/outputs/bundle/release/release-notes.txt`에 저장한다.

### 3. Google Play 업로드하기

production 전체 배포:

```bash
PLAY_TRACK=production \
PLAY_RELEASE_STATUS=completed \
bash scripts/publish_play_release.sh
```

production staged rollout:

```bash
PLAY_TRACK=production \
PLAY_RELEASE_STATUS=inProgress \
PLAY_USER_FRACTION=0.1 \
bash scripts/publish_play_release.sh
```

internal 테스트 배포:

```bash
PLAY_TRACK=internal \
PLAY_RELEASE_STATUS=completed \
bash scripts/publish_play_release.sh
```

## 배포 후 확인할 파일

- `app/build/outputs/bundle/release/release-info.txt`
- `app/build/outputs/bundle/release/play-publish-info.txt`
- `app/build/outputs/bundle/release/play-release-notes/`

## 주의 사항

- `app/build.gradle.kts`에 미커밋 변경이 있으면 `release_bundle.sh`는 중단된다.
- `PLAY_SERVICE_ACCOUNT_JSON_PATH`에 지정한 JSON은 절대 저장소에 커밋하지 않는다.
- 현재 저장소에서 검증한 `Gradle Play Publisher 3.12.1` 기준으로 일반적인 업로드와 개시 요청은 가능하다.
- 다만 Google Play가 `Changes are not sent for review automatically` 상태를 요구하면 마지막 `Send for review` 단계는 Play Console UI에서 수동으로 마무리해야 할 수 있다.
- 빌드 환경에서 JDK 설정이 필요하면 `JAVA_HOME`을 맞춘다. 현재 release script는 macOS Homebrew 경로 `/opt/homebrew/opt/openjdk@21`가 있으면 자동으로 사용한다.

## 참고 문서

- Google Play Developer API getting started: <https://developers.google.com/android-publisher/getting_started>
- Google Cloud Service Account key 생성: <https://cloud.google.com/iam/docs/keys-create-delete>
- Gradle Play Publisher: <https://github.com/Triple-T/gradle-play-publisher#publishing-an-app-bundle>
