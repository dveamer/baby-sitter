# 안드로이드 앱 Bundle Release 후 Google Play 배포 자동화 가이드

네, 가능합니다. 가장 일반적인 방식은 CI/CD에서 **Google Play Developer Publishing API**를 사용하는 것입니다. Google Play Console에서 하던 앱 배포 작업을 자동화할 수 있고, 기본 흐름은 보통 아래와 같습니다.

- edit 생성
- AAB 업로드
- track/release 설정
- commit

참고 문서:
- [Google Play Developer API](https://developer.android.com/google/play/developer-api)

---

## 어떤 방식으로 자동화하면 좋을까?

실무에서는 보통 아래 두 가지를 많이 씁니다.

### 1) Gradle Play Publisher(GPP)

Android/Gradle 중심 프로젝트라면 가장 자연스럽습니다.

가능한 작업:
- App Bundle/APK 업로드
- track 지정
- staged rollout
- release notes 업로드
- 메타데이터 업로드

Gradle task 예시:
- `publishReleaseBundle`
- `publishBundle`
- `publishPaidReleaseBundle`

관련 설정 예시:
- `track`
- `releaseStatus`
- `userFraction`
- `useApplicationDefaultCredentials`

참고:
- [Gradle Plugin Portal - com.github.triplet.play](https://plugins.gradle.org/plugin/com.github.triplet.play)
- [Gradle Play Publisher README](https://github.com/Triple-T/gradle-play-publisher/blob/master/README.md)

### 2) fastlane supply

iOS/Android를 같이 운영하거나, 이미 fastlane을 사용 중이라면 좋은 선택입니다.

예시:

```bash
fastlane supply --aab app-release.aab --track internal --rollout 0.1
```

참고:
- [fastlane supply 문서](https://github.com/fastlane/fastlane/blob/master/fastlane/lib/fastlane/actions/docs/upload_to_play_store.md)

---

## 추천 구성

질문처럼 **`bundleRelease` 다음에 바로 Play 배포를 붙이고 싶다면**, 보통은 **GPP + CI** 조합을 추천합니다.

이유:
- Gradle task 체계에 바로 붙일 수 있음
- Android 프로젝트 흐름과 자연스럽게 연결됨
- variant 기준 task가 자동으로 생김

---

## 전체 구성 순서

### 1. 첫 업로드는 수동으로 1번 필요

신규 앱은 최초 등록과 첫 업로드를 Play Console에서 직접 해야 합니다. 이후부터 자동 배포를 붙이는 방식입니다.

참고:
- [GPP README](https://github.com/Triple-T/gradle-play-publisher/blob/master/README.md)

### 2. Google Cloud Project 및 API 설정

아래 작업이 필요합니다.

- Google Cloud Project 생성
- Google Play Developer API 활성화
- Service Account 생성

참고:
- [Google Play Developer API Getting Started](https://developers.google.com/android-publisher/getting_started)

### 3. Play Console에 Service Account 권한 부여

생성한 **Service Account 이메일**을 Play Console의 **Users & Permissions**에 초대하고 필요한 권한을 부여합니다.

배포 시 필요한 권한 예시:
- Release to production
- Release apps to testing tracks
- Manage testing tracks

참고:
- [Google Play Developer API Getting Started](https://developers.google.com/android-publisher/getting_started)

### 4. CI에서 Gradle task 실행

설정이 끝나면 CI에서 아래처럼 실행하면 됩니다.

```bash
./gradlew publishReleaseBundle --stacktrace
```

---

## `app/build.gradle.kts` 예시

```kotlin
import com.github.triplet.gradle.androidpublisher.ReleaseStatus

plugins {
    id("com.android.application")
    id("com.github.triplet.play") version "4.0.0"
}

android {
    // ...
}

play {
    // GitHub Actions + Workload Identity Federation/ADC 사용 시
    useApplicationDefaultCredentials = true

    // 배포 대상 트랙
    track.set("internal")

    // 즉시 완료 배포
    releaseStatus.set(ReleaseStatus.COMPLETED)

    // staged rollout 예시
    // releaseStatus.set(ReleaseStatus.IN_PROGRESS)
    // userFraction.set(0.1)
}
```

release notes는 보통 아래와 같은 경로에 둡니다.

- `src/main/play/release-notes/en-US/default.txt`
- 또는 track별 `[track].txt`

---

## GitHub Actions 예시

```yaml
name: android-play-deploy

on:
  push:
    tags:
      - "android-v*"

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write

    steps:
      - uses: actions/checkout@v4

      - uses: google-github-actions/auth@v3
        with:
          workload_identity_provider: ${{ secrets.GCP_WIF_PROVIDER }}
          service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - uses: gradle/actions/setup-gradle@v4

      - name: Publish to Google Play
        run: ./gradlew publishReleaseBundle --stacktrace
```

이 구성이 잘 맞는 이유:
- `google-github-actions/auth`는 **Workload Identity Federation(WIF)** 사용을 지원함
- 기본적으로 credentials file 및 `GOOGLE_APPLICATION_CREDENTIALS` 환경 구성을 도와줌
- GPP는 **Application Default Credentials**를 지원하므로 `useApplicationDefaultCredentials = true`와 잘 맞음
- GitHub Actions에서는 `id-token: write` 권한이 필요함

참고:
- [google-github-actions/auth](https://github.com/google-github-actions/auth)

---

## JSON Key 방식도 가능

WIF 구성이 당장 번거롭다면, 임시로는 **Service Account JSON을 GitHub Secret에 넣고** 사용하는 방식도 가능합니다.

예를 들면:
- Secret에 JSON 저장
- `ANDROID_PUBLISHER_CREDENTIALS` 환경변수로 주입

다만 장기적으로는 **long-lived key를 남기지 않는 WIF 방식이 더 안전**합니다.

참고:
- [Gradle Play Publisher README](https://github.com/Triple-T/gradle-play-publisher/blob/master/README.md)

---

## 주의할 점

### 1. 자동화해도 즉시 공개가 보장되지는 않음

자동 배포를 구성해도 Google Play 심사나 검토가 필요한 경우가 있어 바로 반영되지 않을 수 있습니다.

참고:
- [Google Play review time 관련 도움말](https://support.google.com/googleplay/android-developer/answer/9859751?hl=en)

### 2. 민감 권한이 있으면 Console에서 사전 설정 필요 가능

앱이 **high-risk / sensitive permission**을 새로 요구하는 경우, Play Console UI에서 Permissions Declaration을 먼저 처리해야 API 배포가 정상 동작할 수 있습니다.

### 3. targetSdk 요구사항 확인 필요

정책 변경에 따라 **업데이트 가능한 targetSdk 최소 요구사항**이 달라질 수 있으므로 배포 전에 확인이 필요합니다.

---

## 실무 추천 흐름

가장 무난한 구조는 아래입니다.

1. `bundleRelease` 수행
2. CI에서 인증 처리
3. `publishReleaseBundle` 실행
4. 내부 테스트 트랙(`internal`)에 먼저 배포
5. 검증 후 `beta` 또는 `production`으로 승격

예를 들어:
- 개발 브랜치 태그 → `internal`
- 릴리스 태그 → `production`

---

## 한 줄 결론

**가능합니다.**
가장 추천하는 방식은 **Gradle Play Publisher + GitHub Actions + Workload Identity Federation** 조합입니다.

Android 프로젝트에 가장 자연스럽게 붙고, `bundleRelease` 이후 Google Play 배포를 거의 그대로 자동화할 수 있습니다.

---

## 다음에 바로 이어서 할 수 있는 것

원하면 아래 중 하나로 바로 확장할 수 있습니다.

- **GitHub Actions용 실전 배포 워크플로우** 작성
- **GitLab CI용 `.gitlab-ci.yml`** 작성
- **internal / alpha / production 트랙 분기 전략** 정리
- **릴리즈 노트 자동 생성 방식** 추가
- **버전코드/버전명 자동 증가 전략** 추가
