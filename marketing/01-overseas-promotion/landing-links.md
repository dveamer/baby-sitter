# Landing Links and UTM Rules

홈페이지는 이제 `?lang=` 진입과 Play referrer 전달을 지원하는 전제로 정리했습니다.

## Supported landing languages

* English: `https://babysitter.dveamer.com/index.html?lang=en`
* Indonesian: `https://babysitter.dveamer.com/index.html?lang=id`
* Portuguese (Brazil): `https://babysitter.dveamer.com/index.html?lang=pt`
* Spanish (LATAM): `https://babysitter.dveamer.com/index.html?lang=es`
* Korean: `https://babysitter.dveamer.com/index.html?lang=ko`

## Recommended UTM structure

필수:

* `utm_source`
* `utm_medium`
* `utm_campaign`

권장:

* `utm_content`
* `utm_term`

## Naming convention

### Source

* `facebook_group`
* `instagram_reels`
* `tiktok`
* `youtube_shorts`
* `mom_creator`
* `community_admin_dm`

### Medium

* `organic`
* `paid`
* `influencer`
* `community`

### Campaign

* `id_feedback_test_w1`
* `ph_feedback_test_w1`
* `br_feedback_test_w1`

### Content

* `old_phone_hook`
* `parent_voice_hook`
* `same_wifi_hook`
* `group_post_v1`
* `creator_dm_v1`

## Examples

### Indonesia TikTok

`https://babysitter.dveamer.com/index.html?lang=id&utm_source=tiktok&utm_medium=organic&utm_campaign=id_feedback_test_w1&utm_content=old_phone_hook`

### Philippines Facebook group

`https://babysitter.dveamer.com/index.html?lang=en&utm_source=facebook_group&utm_medium=community&utm_campaign=ph_feedback_test_w1&utm_content=group_post_v1`

### Brazil creator link

`https://babysitter.dveamer.com/index.html?lang=pt&utm_source=mom_creator&utm_medium=influencer&utm_campaign=br_feedback_test_w1&utm_content=parent_voice_hook`

## Notes

* 랜딩에 붙은 `utm_*` 값은 홈페이지에서 Play 링크로도 같이 넘기도록 구성하는 편이 좋습니다.
* 같은 캠페인 안에서도 영상 훅별 차이를 보려면 `utm_content`를 반드시 나누는 편이 좋습니다.
* 언어 전환 버튼을 눌러도 랜딩 URL의 원래 UTM은 유지되는 흐름이 바람직합니다.
