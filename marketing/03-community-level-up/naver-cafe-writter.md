
# 네이버 카페 질문 글 작성

naver-cafe-result.md 에 기록된 카페들 중 랜덤하게 3곳을 방문해서 질문 글을 작성해줘. 매번 지후맘 카페만 등록되는 경향이 있어. 제대로 된 랜덤 선택하도록 신경써줘.
사용자들이 어떤 기능을 원하는지 궁금하고 babysitter 앱에 대한 사용자들의 의견을 수렵하기 위함이야. 

`homepage-img-1-ko.png` 파일도 업로드해줘.  

제목은 200byte(한글 100자) 제한이 있으니까 제목은 짧게 적어줘. 
그리고 본문은 보기 좋게 `</br>` 입력 된 곳들은 엔터값 입력해서 반드시 줄바꿈해줘.

## 홈캠 관련 질문

육아용품, 육아팁, 자유게시판, 수다방 등의 게시판에 질문하면돼.


```
<title>홈캠 어떤거 사용하세요?</title>

<content>
홈캠 어떤거 사용하세요? 개인정보 노출이 신경쓰여요 ㅠ </br></br>

이건 앱 설치하면 홈캠처럼 사용 가능한데 외부에서 접근이 불가능하다고 하더라고요. </br>
이앱은 어떠려나요? https://babysitter.dveamer.com </br></br>

아기가 깨서 울거나 움직이면 자장가도 틀어준다고 해서 좋을 것 같긴해요. </br>
</content>
```


```
<title>홈캠 필요할까요? 아기 몇살까지 사용하세요?</title>

<content>
개인정보 노출될 까봐 두려워서 구매해도 되나 싶어요. </br>
보통 아기 몇살때까지 홈캠 사용하세요? </br></br>

마침 안쓰고 있는 폰이 하나 있는데 이 앱 설치해서 홈캠처럼 사용해볼 수 있다고는 하네요. </br>
홈캠 구매 전에 한번 써볼까 하는데 어떠려나요? </br>
https://babysitter.dveamer.com </br></br>
</content>
```

## 분리수면 관련 질문

육아팁, 자유게시판, 수다방 등의 게시판에 질문하면돼.

```
<title>분리수면할 때 아기 울면 얼마나 기다려야해요?</title>

<content>
분리수면 다들 성공하셨나요? 아기가 울 때 너무 초조한데 얼마나 기다려야할까요? </br>
기다리고 문 열고 들어가지 말라고들 하던데 그게 굉장히 어렵네요. </br></br>

이 앱은 설치해두면 아기가 깨면 자장가를 틀어준다고하는데 효과가 있으려나요? </br>
https://babysitter.dveamer.com </br></br>
</content>
```

```
<title>분리수면 6개월부터 시작해도 될까요?</title>

<content>
잠을 제대로 못자서 분리수면 하고 시퍼요 ㅠ 언제부터 시작해도 될까요? </br>

이 앱은 설치해두면 아기가 깨면 자장가를 틀어준다고하는데 효과가 있으려나요? </br>
https://babysitter.dveamer.com </br></br>
</content>
```

```
<title>12개월 이전에도 분리 수면 가능한가요?</title>

<content>
잠을 제대로 못자서 분리수면 하고 시퍼요 ㅠ 언제부터 시작해도 될까요? </br>

아직 아기가 어린대 언제부터 분리수면을 시작 해야할지 고민이예요. 다들 어떻게 시작하셨나요?

이 앱은 설치해두면 아기가 깨면 자장가를 틀어준다고하는데 효과가 있으려나요? </br>
https://babysitter.dveamer.com </br></br>
</content>
```

```
<title>수면 보조 기능 의견 주세요</title>

<content>
안녕하세요. 아기 수면/홈캠 보조 앱을 만들고 있는 팀입니다. </br>
아기가 깨면 자장가를 들려줘서 아기가 스스로 다시 잠들수 있게 도와주는 앱을 만들고 있습니다.</br>

분리수면 혹은 수면보조를 위한 앱으로써 도움이 될지 혹은 필요한 기능이 있을지 질문드립니다.</br></br>
https://babysitter.dveamer.com </br></br>
</content>
```


## 중복 등록 방지

게시판 첫 페이지에 이전에 등록한 게시물이 있다면 다른 게시판에 등록하는 것으로 작업을 변경해줘. 

## 이미지 첨부 주의

댓글 텍스트 작성까지는 정상인데 이미지 업로드 과정에서 `잘못된 접근 방법` 문제가 날 수 있어. 이미지 첨부는 직접 API 호출이나 세션 키 업로드 우회 방식으로 처리하지 말고, 네이버 댓글 UI 의 실제 이미지 추가 흐름을 사용해줘.

- 댓글 위젯의 `파일 선택` / `이미지 추가` 버튼 또는 `input.u-cbox-browse-file-input`에 `homepage-img-1-ko.png`를 넣는 방식부터 시도해줘.
- 업로드 후 썸네일과 `업로드 취소` 컨트롤이 보이는지 확인한 뒤에만 `등록`을 눌러줘.
- 이미지 업로드 단계에서 `잘못된 접근 방법`이 나오면 텍스트만 등록하지 말고 해당 글은 완료 처리하지 마.
- API fallback 으로 이미지 업로드를 재시도하지 말고, 다른 UI 기반 업로드 방법을 찾아 적용해줘.

## 네이버 에디터 줄바꿈 저장 주의

네이버 카페 스마트에디터는 화면에 보이는 contenteditable DOM 과 실제 저장되는 문서 모델이 분리되어 있을 수 있어. 수정 화면에서 겉 DOM 에 줄바꿈이 보이도록 바뀌었더라도, 실제 에디터 데이터에 반영하지 않으면 등록 후 저장 데이터에는 줄바꿈이 반영되지 않는다.

- 줄바꿈을 수정하거나 본문을 다시 구성할 때 DOM 만 직접 고친 상태를 완료로 보지 마.
- 실제 에디터 데이터는 브라우저 콘솔에서 `SE.launcher.getEditor('cafepc001').getDocumentData()` 형태로 확인한다. 에디터 id 가 다르면 `SE.launcher` 안의 editor 목록에서 현재 글쓰기 에디터를 먼저 찾아라.
- 이미지가 이미 업로드된 상태라면 이미지 컴포넌트는 그대로 보존하고, 텍스트 컴포넌트(`@ctype === 'text'`)의 `value` 만 문단 배열로 교체한다.
- 변경한 문서 모델은 `SE.launcher.getEditor('cafepc001').setDocumentData(data)` 로 다시 주입한 뒤 `등록`을 눌러 저장한다.
- 등록 후 글 상세 화면에서 `innerText` 기준으로 문단 줄바꿈이 실제로 남아 있는지, `homepage-img-1-ko.png` 이미지가 그대로 노출되는지 둘 다 확인해야 완료 처리한다.

예시 흐름:

```js
const editor = SE.launcher.getEditor('cafepc001');
const data = editor.getDocumentData();
const textComponent = data.document.components.find(
  (component) => component['@ctype'] === 'text'
);

textComponent.value = [
  { id: String(Date.now()), nodes: [{ id: String(Date.now() + 1), value: '안녕하세요. ...' }] },
  { id: String(Date.now() + 2), nodes: [{ id: String(Date.now() + 3), value: '' }] },
  { id: String(Date.now() + 4), nodes: [{ id: String(Date.now() + 5), value: '다음 문단입니다.' }] },
];

editor.setDocumentData(data);
```

## 네이버 에디터 링크 저장 주의

`https://babysitter.dveamer.com` 는 단순 텍스트가 아니라 네이버 에디터의 링크 기능으로 저장해야 한다. 본문에 URL 문자열만 넣으면 상세 화면에서 클릭 가능한 링크가 되지 않을 수 있으므로 완료 처리하지 마.

- 본문 줄바꿈과 이미지 보존을 `setDocumentData(data)` 로 먼저 반영한 뒤, 에디터 본문에서 `https://babysitter.dveamer.com` 텍스트만 정확히 선택한다.
- 선택은 본문 contenteditable 영역에서 해야 한다. 필요하면 브라우저 콘솔에서 현재 에디터 iframe/contenteditable 안의 텍스트 노드를 찾아 `Range` 와 `Selection` 으로 URL 문자열 범위만 선택해도 된다. 이 단계는 선택만 하는 것이고, DOM 을 직접 `<a>` 로 바꾸는 방식은 완료로 보지 않는다.
- URL 텍스트가 선택된 상태에서 네이버 에디터 툴바의 `링크` / `링크 입력 열기` 기능을 누르고, 주소 입력란에 `https://babysitter.dveamer.com` 를 넣은 뒤 `적용` 또는 `확인`으로 링크를 건다.
- 링크 적용 후 에디터가 링크 미리보기 카드를 생성하면 삭제하지 말고 그대로 둔다. `매터니티스쿨 > 아이재우기 노하우` 수정 완료 글에서는 상세 화면에 URL 링크와 `Baby Sitter ... babysitter.dveamer.com` 링크 미리보기 카드가 함께 보이는 것이 정상 상태로 확인됐다.
- `등록` 또는 `수정` 저장 후 상세 화면에서 URL이 `StaticText` 가 아니라 `link "https://babysitter.dveamer.com" url="https://babysitter.dveamer.com/"` 형태로 잡히는지 확인해야 한다.

상세 화면 검증 예시:

```js
const cafeFrame = [...document.querySelectorAll('iframe')].find(
  (iframe) => iframe.title === '카페 메인'
);
const doc = cafeFrame?.contentDocument ?? document;
const babysitterLinks = [...doc.querySelectorAll('a')]
  .map((link) => ({
    text: link.innerText.trim(),
    href: link.href,
  }))
  .filter((link) => (
    link.text.includes('babysitter.dveamer.com') ||
    link.href.includes('babysitter.dveamer.com')
  ));

console.log(babysitterLinks);
```

`babysitterLinks` 에 `href: "https://babysitter.dveamer.com/"` 가 포함되어야 링크 수정 완료로 본다. 본문에 URL 문자열이 보여도 이 목록이 비어 있으면 단순 텍스트 상태다.

## 작업 종료 
 
### 리소스 해제

작업이 모두 완료되었다면 사용했던 chrome-devtools(openchrome) 은 종료시켜줘.
브라우저 탭만 종료하지 말고 브라우저를 종료시켜줘.

### 시간 엄수

25분 이상 작업이 지연되면 안돼. 20분이 지난 시점 부터는 작업 중단하고 그 시점까지 진행사항을 기록하고 openchrome 브라우저 종료하는데 사용해줘.

## 주의 사항

### openchrome 브라우저 사용

chrome-devtools(openchrome) 사용에 문제가 있다면 아래 같은 문제 점이 예상돼.
  1. chrome-devtools(openchrome) 에서 네이버 로그인이 되어있지 않아서 
  2. 이전에 사용했던 chrome-devtools(openchrome) 리소스가 정리가 되지 않아서 
  
1번이 원인일 경우에는 대기해서 내가 로그인해주는 수밖에 없어. 
2번이 원인일 경우에는 chrome-devtools(openchrome) 리소스를 종료 시키면 다음 스캐쥴에서는 정상적으로 동작할 것으로 예상돼

문제점이 생겼을 때 Playwright 를 사용해서 해결해보려고하지마. 성공하는 것이 기록된 적이 없어.

> API를 직접 호출하는 방식은 도구 안전 검사가 막았습니다. 대신 지시서에 맞게 브라우저 화면을 실제로 열고, 멤버등급 안내 화면의 텍스트 스냅샷만 읽어서 상태를 확인하겠습니다.

라는 상황이 발생하기도했어. 글을 작성하고 등록하는 행위는 API가 아닌 chrome-devtools(openchrome)를 통해 진행해줘.

### 권한 없는 게시판 쓰기 실패

> 마지막 글쓰기 화면에서 접근성 스냅샷이 120초 타임아웃됐습니다. 페이지 자체가 죽은 것은 아닌지 DOM 상태를 먼저 확인하고, 스냅샷 대신 이미 알고 있는 선택자/문서 모델로 진행 가능한지 보겠습니다.

위와 같은 메시지가 발생될 때 브라우저에는 권한 부족하다는 토스트 팝업이 띄워져있고 그 이후로 진행이 안되는 경우가 종종 있어. 

> 스마일맘산모교실은 결과 목록에는 완료 등급으로 기록돼 있지만, 해당 자유게시판은 현재 계정 일반멤버로는 읽기/쓰기가 막혀 있습니다. 이 후보는 완료 처리하지 않고 대체 카페를 무작위로 다시 뽑겠습니다.

내가 브라우저에 노출된 권한 부족 토스트 팝업을 종료시키면 그 다음에 위에 같은 답변을 주고 있어. 
그렇다보니 특정 카페(예: 스마일맘산모교실)은 게시글 등록이 거의 불가능한 상황이야. 

120초 타임 아웃이 발생하면 해당 탭을 종료하고 새로운 탭으로 접속해서 진척 사항을 파악해봐. 만약에 진행이 안되어있다면 같은 카페의 다른 게시판에 글 쓰는 것을 시도해줘. 그리고 등록이 불가능했던 게시판에 대해서는 naver-cafe-result.md 에 기록해줘. 

## 실행 기록

| 등록 일시 | 카페 이름 | 게시판 이름 | 게시글 URL |
| --- | --- | --- | --- |
| 2026-05-03T14:43:00+0900 | 베베라운지 | 육아맘토크 | https://cafe.naver.com/ArticleRead.nhn?menuid=366&boardtype=L&clubid=24081850&articleid=1293466 |
| 2026-05-03T14:45:00+0900 | 맘스스토리 | 임신출산관련질문 | https://cafe.naver.com/ArticleRead.nhn?menuid=142&boardtype=L&clubid=12876544&articleid=600293 |
| 2026-05-04T15:21:00+0900 | 엄마는 마법사 | [문제]함께고민방 | https://cafe.naver.com/ArticleRead.nhn?clubid=20981877&articleid=930263&menuid=311&boardtype=L |
| 2026-05-04T15:26:00+0900 | 육아친구인천 | ♡육아맘 수다 | https://cafe.naver.com/ArticleRead.nhn?clubid=18177992&articleid=705400&menuid=284&boardtype=L |
| 2026-05-04T15:36:00+0900 | 두드림 산모교실 | 친목수다방 | https://cafe.naver.com/ArticleRead.nhn?clubid=13365688&articleid=879995&menuid=328&boardtype=L |
| 2026-05-04T16:35:00+0900 | 베이비템 | ♡임신/이유식/육아♡ | https://cafe.naver.com/ArticleRead.nhn?menuid=191&boardtype=L&clubid=18851490&articleid=607567 |
| 2026-05-04T16:38:00+0900 | 알잠을 만나면 육아가 쉬워집니다 | 자유게시판- 아무이야기 | https://cafe.naver.com/ArticleRead.nhn?menuid=208&boardtype=L&clubid=28443114&articleid=74860 |
| 2026-05-04T16:49:00+0900 | 모비맘카페 | 자유게시판 | https://cafe.naver.com/ArticleRead.nhn?menuid=1&boardtype=L&clubid=28140611&articleid=173394 |
| 2026-05-04T11:15:00+0900 | 아가방앤컴퍼니 공식카페 아가베베 | 일상수다방 | https://cafe.naver.com/ArticleRead.nhn?clubid=20170537&articleid=604372&menuid=631&boardtype=L |
| 2026-05-04T17:20:00+0900 | 사과나무맘스홀릭 | ●자유 수다방 | https://cafe.naver.com/ArticleRead.nhn?clubid=21451316&articleid=803033&menuid=75&boardtype=L |
| 2026-05-04T20:27:00+0900 | 맘스블로그 | 결혼/임신/출산/육아 | https://cafe.naver.com/ArticleRead.nhn?clubid=22741115&articleid=1095025&menuid=38&boardtype=L |
| 2026-05-04T21:28:00+0900 | 육아친구 광주.전남 | 이 야 기 방 | https://cafe.naver.com/ArticleRead.nhn?menuid=224&boardtype=L&clubid=20268063&articleid=376699 |
| 2026-05-04T21:38:00+0900 | 맘스홀릭 | ●자 유 로 운 수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=2&boardtype=L&clubid=15240589&articleid=1461111 |
| 2026-05-04T21:45:00+0900 | 육아친구인천 | 육아방법 Q&A | https://cafe.naver.com/ArticleRead.nhn?menuid=425&boardtype=L&clubid=18177992&articleid=705412 |
| 2026-05-05T00:17:00+0900 | 베이비템 | ♡친 목 수 다 방♡ | https://cafe.naver.com/ArticleRead.nhn?menuid=141&boardtype=L&clubid=18851490&articleid=607608 |
| 2026-05-05T00:24:00+0900 | 엄마는 마법사 | [워킹맘]수다공간 | https://cafe.naver.com/ArticleRead.nhn?menuid=427&boardtype=L&clubid=20981877&articleid=930292 |
| 2026-05-05T00:31:00+0900 | 육아친구인천 | ♡자유로운수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=442&boardtype=L&clubid=18177992&articleid=705417 |
| 2026-05-05T16:23:37+0900 | 1프로육아 | 미취학 부모방 | https://cafe.naver.com/ArticleRead.nhn?menuid=847&boardtype=L&clubid=22022532&articleid=531895 |
| 2026-05-05T16:23:37+0900 | 육아친구 대구경북 | 맘들 대화방 | https://cafe.naver.com/ArticleRead.nhn?menuid=27&boardtype=L&clubid=19972973&articleid=845135 |
| 2026-05-05T16:23:37+0900 | 맘스스토리 | 맘스왕수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=58&boardtype=L&clubid=12876544&articleid=600317 |
| 2026-05-05T19:32:37+0900 | 1프로육아 | 엄마 수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=18&boardtype=L&clubid=22022532&articleid=531898 |
| 2026-05-05T19:32:37+0900 | 엄마는 마법사 | [마법사]수다공간 | https://cafe.naver.com/ArticleRead.nhn?clubid=20981877&articleid=930318&menuid=21&boardtype=L |
| 2026-05-05T19:32:37+0900 | 맘스스토리 | 신생아 및 육아정보 | https://cafe.naver.com/ArticleRead.nhn?clubid=12876544&articleid=600319&menuid=404&boardtype=L |
| 2026-05-05T23:23:09+0900 | 두드림 산모교실 | 예비맘수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=369&boardtype=L&clubid=13365688&articleid=880237 |
| 2026-05-06T01:23:46+0900 | 맘스스토리 | 예비맘수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=251&boardtype=L&clubid=12876544&articleid=600324 |
| 2026-05-06T01:23:46+0900 | 알잠을 만나면 육아가 쉬워집니다 | A or B (고민해결) | https://cafe.naver.com/ArticleRead.nhn?menuid=209&boardtype=L&clubid=28443114&articleid=74862 |
| 2026-05-06T01:23:46+0900 | 두드림 산모교실 | 출산맘수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=370&boardtype=L&clubid=13365688&articleid=880240 |
| 2026-05-06T04:24:00+0900 | 알잠을 만나면 육아가 쉬워집니다 | 큰 아기 모여라! | https://cafe.naver.com/ArticleRead.nhn?menuid=228&boardtype=L&clubid=28443114&articleid=74863 |
| 2026-05-06T04:24:00+0900 | 육아친구 대구경북 | 우리들의 일상 | https://cafe.naver.com/ArticleRead.nhn?menuid=4&boardtype=L&clubid=19972973&articleid=845136 |
| 2026-05-06T04:24:00+0900 | 1프로육아 | 육아 고민 상담 | https://cafe.naver.com/ArticleRead.nhn?menuid=197&boardtype=L&clubid=22022532&articleid=531901 |
| 2026-05-07T02:33:46+0900 | 일등맘 수다방 | 오늘 토크라운지 | https://cafe.naver.com/ArticleRead.nhn?menuid=159&boardtype=L&clubid=24081850&articleid=1293786 |
| 2026-05-07T02:33:46+0900 | 1프로육아 | 임신·출산 부모방 | https://cafe.naver.com/ArticleRead.nhn?menuid=838&boardtype=L&clubid=22022532&articleid=531913 |
| 2026-05-07T02:33:46+0900 | 맘살림회관 | 13~24개월 자유수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=376&boardtype=L&clubid=10278718&articleid=635774 |
| 2026-05-08T08:16:00+0900 | 베이비템 | ♡아이용품 & 장난감♡ | https://cafe.naver.com/ArticleRead.nhn?menuid=184&boardtype=L&clubid=18851490&articleid=607970 |
| 2026-05-09T02:12:00+0900 | 투데이맘스 | 육아질문방 | https://cafe.naver.com/ArticleRead.nhn?menuid=25&boardtype=L&clubid=29602531&articleid=27415 |
| 2026-05-09T02:16:00+0900 | 맘스페셜 | ☞ 육아고민/질문 | https://cafe.naver.com/ArticleRead.nhn?menuid=417&boardtype=L&clubid=16075457&articleid=243092 |
| 2026-05-09T08:13:00+0900 | 모비맘카페 | 자유게시판 | https://cafe.naver.com/ArticleRead.nhn?menuid=1&boardtype=L&clubid=28140611&articleid=173521 |
| 2026-05-09T08:16:00+0900 | 베베라운지 | 육아맘토크 | https://cafe.naver.com/ArticleRead.nhn?menuid=366&boardtype=L&clubid=24081850&articleid=1294051 |
| 2026-05-09T14:11:00+0900 | 아가방앤컴퍼니 공식카페 아가베베 | mom's 육아노하우 | https://cafe.naver.com/ArticleRead.nhn?menuid=111&boardtype=L&clubid=20170537&articleid=604413 |
| 2026-05-09T14:13:00+0900 | 사과나무맘스홀릭 | ●두살↑↓(12~48개월) | https://cafe.naver.com/ArticleRead.nhn?menuid=112&boardtype=L&clubid=21451316&articleid=803189 |
| 2026-05-09T14:15:00+0900 | 맘스블로그 | 임신/출산/육아 | https://cafe.naver.com/ArticleRead.nhn?menuid=65&boardtype=L&clubid=22741115&articleid=1095080 |
| 2026-05-10T14:11:00+0900 | 나는엄마다 맘카페 | ✿ 육아맘 질문방 | https://cafe.naver.com/ArticleRead.nhn?menuid=243&boardtype=L&clubid=25139350&articleid=1037577 |
| 2026-05-10T14:14:00+0900 | 알잠을 만나면 육아가 쉬워집니다 | 교육관련 | https://cafe.naver.com/ArticleRead.nhn?menuid=213&boardtype=L&clubid=28443114&articleid=74869 |
| 2026-05-10T14:17:00+0900 | 엄마는 마법사 | [정보]임신/출산/육아 | https://cafe.naver.com/ArticleRead.nhn?menuid=16&boardtype=L&clubid=20981877&articleid=930561 |
| 2026-05-10T20:12:00+0900 | 육아친구 광주.전남 | 이 야 기 방 | https://cafe.naver.com/ArticleRead.nhn?menuid=224&boardtype=L&clubid=20268063&articleid=376835 |
| 2026-05-10T20:13:00+0900 | 육아친구 대구경북 | 우리들의 일상 | https://cafe.naver.com/ArticleRead.nhn?menuid=4&boardtype=L&clubid=19972973&articleid=845176 |
| 2026-05-10T20:15:00+0900 | 베베라운지 | 오늘 토크라운지 | https://cafe.naver.com/ArticleRead.nhn?menuid=159&boardtype=L&clubid=24081850&articleid=1294094 |
| 2026-05-11T02:13:00+0900 | 투데이맘스 | 아무거나질문방 | https://cafe.naver.com/ArticleRead.nhn?menuid=26&boardtype=L&clubid=29602531&articleid=27424 |
| 2026-05-11T02:16:00+0900 | 1프로육아 | [질문]구입할까요? | https://cafe.naver.com/ArticleRead.nhn?menuid=890&boardtype=L&clubid=22022532&articleid=531945 |
| 2026-05-11T02:19:00+0900 | 베이비템 | ♡유아용품♡ | https://cafe.naver.com/ArticleRead.nhn?menuid=147&boardtype=L&clubid=18851490&articleid=608215 |
| 2026-05-13T02:31:00+0900 | 울산맘 - 맘앤파파 보물섬 | ● 쫑알쫑알수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=1084&boardtype=L&clubid=21712077&articleid=845716 |
| 2026-05-13T04:26:00+0900 | 맘스1번지 | 일상게시판 | https://cafe.naver.com/ArticleRead.nhn?menuid=63&boardtype=L&clubid=19116800&articleid=388359 |
| 2026-05-13T04:29:00+0900 | 육아친구부산 | 수다·고민 말하기 | https://cafe.naver.com/ArticleRead.nhn?menuid=269&boardtype=L&clubid=18599406&articleid=1197376 |
| 2026-05-13T05:21:00+0900 | 베베라운지 | 오늘 토크라운지 | https://cafe.naver.com/ArticleRead.nhn?menuid=159&boardtype=L&clubid=24081850&articleid=1294469 |
| 2026-05-13T05:24:00+0900 | 육아친구인천 | ♡자유로운수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=442&boardtype=L&clubid=18177992&articleid=706006 |
| 2026-05-13T05:27:00+0900 | 두드림 산모교실 | 친목수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=328&boardtype=L&clubid=13365688&articleid=881491 |
| 2026-05-13T15:23:00+0900 | 아가방앤컴퍼니 공식카페 아가베베 | 일상수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=631&boardtype=L&clubid=20170537&articleid=604441 |
| 2026-05-13T15:31:00+0900 | 나는엄마다 맘카페 | ✿ 육아맘 수다방 ✿ | https://cafe.naver.com/ArticleRead.nhn?menuid=241&boardtype=L&clubid=25139350&articleid=1037693 |
| 2026-05-13T20:29:00+0900 | 육아친구 광주.전남 | 이 야 기 방 | https://cafe.naver.com/ArticleRead.nhn?menuid=224&boardtype=L&clubid=20268063&articleid=376912 |
| 2026-05-13T20:32:00+0900 | 엄마는 마법사 | [수다]0~4세엄마 | https://cafe.naver.com/ArticleRead.nhn?menuid=289&boardtype=L&clubid=20981877&articleid=930682 |
| 2026-05-13T21:31:00+0900 | 베이비템 | ♡친 목 수 다 방♡ | https://cafe.naver.com/ArticleRead.nhn?menuid=141&boardtype=L&clubid=18851490&articleid=608553 |
| 2026-05-13T22:20:00+0900 | 육아친구인천 | ♡육아맘 수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=284&boardtype=L&clubid=18177992&articleid=706069 |
| 2026-05-13T22:23:00+0900 | 육아친구 대구경북 | 우리들의 일상 | https://cafe.naver.com/ArticleRead.nhn?menuid=4&boardtype=L&clubid=19972973&articleid=845233 |
| 2026-05-14T02:09:00+0900 | 베이비템 | ♡아이교육&놀이♡ | https://cafe.naver.com/ArticleRead.nhn?menuid=148&boardtype=L&clubid=18851490&articleid=608580 |
| 2026-05-14T02:13:00+0900 | 아가방앤컴퍼니 공식카페 아가베베 | 임신/출산 축하방 | https://cafe.naver.com/ArticleRead.nhn?menuid=742&boardtype=L&clubid=20170537&articleid=604449 |
| 2026-05-14T02:14:00+0900 | 엄마는 마법사 | [문제]함께고민방 | https://cafe.naver.com/ArticleRead.nhn?menuid=311&boardtype=L&clubid=20981877&articleid=930692 |
| 2026-05-14T06:08:00+0900 | 맘스스토리 | 신생아 및 육아정보 | https://cafe.naver.com/ArticleRead.nhn?menuid=404&boardtype=L&clubid=12876544&articleid=600374 |
| 2026-05-14T06:12:00+0900 | 맘살림회관 | 밥안먹는 아이 자유수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=351&boardtype=L&clubid=10278718&articleid=636214 |
| 2026-05-14T06:14:00+0900 | 투데이맘스 | 자유로운수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=6&boardtype=L&clubid=29602531&articleid=27459 |
| 2026-05-14T08:13:00+0900 | 베베라운지 | 오늘 토크라운지 | https://cafe.naver.com/ArticleRead.nhn?menuid=159&boardtype=L&clubid=24081850&articleid=1294657 |
| 2026-05-14T08:15:00+0900 | 사과나무맘스홀릭 | ●자유 수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=75&boardtype=L&clubid=21451316&articleid=803402 |
| 2026-05-14T08:18:00+0900 | 엄마는 마법사 | [워킹맘]수다공간 | https://cafe.naver.com/ArticleRead.nhn?menuid=427&boardtype=L&clubid=20981877&articleid=930693 |
| 2026-05-14T10:14:00+0900 | 투데이맘스 | 육아질문방 | https://cafe.naver.com/ArticleRead.nhn?menuid=25&boardtype=L&clubid=29602531&articleid=27461 |
| 2026-05-14T10:20:00+0900 | 매터니티스쿨 | 유아용품사용후기 | https://cafe.naver.com/ArticleRead.nhn?menuid=271&boardtype=L&clubid=17523807&articleid=396930 |
| 2026-05-14T10:24:00+0900 | 육아친구부산 | 질문·답변하기 | https://cafe.naver.com/ArticleRead.nhn?menuid=333&boardtype=L&clubid=18599406&articleid=1197399 |
| 2026-05-14T14:14:00+0900 | 베베라운지 | 육아템.생활용품.돌잔치 | https://cafe.naver.com/ArticleRead.nhn?menuid=430&boardtype=L&clubid=24081850&articleid=1294726 |
| 2026-05-14T18:11:00+0900 | 베베라운지 | 오늘 토크라운지 | https://cafe.naver.com/ArticleRead.nhn?menuid=159&boardtype=L&clubid=24081850&articleid=1294775 |
| 2026-05-15T10:11:00+0900 | 울산맘 - 맘앤파파 보물섬 | ● 30대이상수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=1560&boardtype=L&clubid=21712077&articleid=845774 |
| 2026-05-15T12:19:00+0900 | 베이비템 | ♡친 목 수 다 방♡ | https://cafe.naver.com/ArticleRead.nhn?menuid=141&boardtype=L&clubid=18851490&articleid=608736 |
| 2026-05-15T14:12:00+0900 | 육아친구인천 | ♡임신/출산/육아 | https://cafe.naver.com/ArticleRead.nhn?menuid=451&boardtype=L&clubid=18177992&articleid=706242 |
| 2026-05-15T14:14:00+0900 | 베베라운지 | 육아맘토크 | https://cafe.naver.com/ArticleRead.nhn?menuid=366&boardtype=L&clubid=24081850&articleid=1294875 |
| 2026-05-15T14:16:00+0900 | 사과나무맘스홀릭 | ●자유 수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=75&boardtype=L&clubid=21451316&articleid=803500 |
| 2026-05-15T16:16:00+0900 | 육아친구 광주.전남 | 이 야 기 방 | https://cafe.naver.com/ArticleRead.nhn?menuid=224&boardtype=L&clubid=20268063&articleid=376951 |
| 2026-05-15T16:17:00+0900 | 육아친구 대구경북 | 맘들 대화방 | https://cafe.naver.com/ArticleRead.nhn?menuid=27&boardtype=L&clubid=19972973&articleid=845271 |
| 2026-05-15T16:20:00+0900 | 매터니티스쿨 | 도란도란! 수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=91&boardtype=L&clubid=17523807&articleid=396931 |
| 2026-05-15T18:18:00+0900 | 맘살림회관 | 5살 아이 자유수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=341&boardtype=L&clubid=10278718&articleid=636305 |
| 2026-05-15T18:20:00+0900 | 모비맘카페 | 자유게시판 | https://cafe.naver.com/ArticleRead.nhn?menuid=1&boardtype=L&clubid=28140611&articleid=173735 |
| 2026-05-15T18:21:00+0900 | 엄마는 마법사 | [마법사]수다공간 | https://cafe.naver.com/ArticleRead.nhn?menuid=21&boardtype=L&clubid=20981877&articleid=930777 |
| 2026-05-15T20:11:00+0900 | 지후맘 | ▦···자유게시판···▦ | https://cafe.naver.com/ArticleRead.nhn?menuid=309&boardtype=L&clubid=15240504&articleid=3302079 |
| 2026-05-15T20:13:00+0900 | 베이비템 | ♡임신/이유식/육아♡ | https://cafe.naver.com/ArticleRead.nhn?menuid=191&boardtype=L&clubid=18851490&articleid=608772 |
| 2026-05-15T20:18:00+0900 | 육아친구부산 | 임신·출산·육아 | https://cafe.naver.com/ArticleRead.nhn?menuid=227&boardtype=L&clubid=18599406&articleid=1197422 |
| 2026-05-15T22:13:00+0900 | 나는엄마다 맘카페 | ✿ 자유 수다방 ✿ | https://cafe.naver.com/ArticleRead.nhn?menuid=228&boardtype=L&clubid=25139350&articleid=1037792 |
| 2026-05-15T22:14:00+0900 | 1프로육아 | [정보]육아 | https://cafe.naver.com/ArticleRead.nhn?menuid=90&boardtype=L&clubid=22022532&articleid=532002 |
| 2026-05-15T22:14:00+0900 | 맘스블로그 | 꽁시랑꽁시랑★ | https://cafe.naver.com/ArticleRead.nhn?menuid=13&boardtype=L&clubid=22741115&articleid=1095135 |
| 2026-05-16T00:13:00+0900 | 육아친구 대구경북 | 우리들의 일상 | https://cafe.naver.com/ArticleRead.nhn?menuid=4&boardtype=L&clubid=19972973&articleid=845283 |
| 2026-05-16T00:15:00+0900 | 1프로육아 | 미취학 부모방 | https://cafe.naver.com/ArticleRead.nhn?menuid=847&boardtype=L&clubid=22022532&articleid=532004 |
| 2026-05-16T00:17:00+0900 | 투데이맘스 | 자유로운수다 | https://cafe.naver.com/ArticleRead.nhn?menuid=6&boardtype=L&clubid=29602531&articleid=27490 |
| 2026-05-16T04:13:00+0900 | 아가방앤컴퍼니 공식카페 아가베베 | 추천! 출산필수선물템 | https://cafe.naver.com/ArticleRead.nhn?menuid=718&boardtype=L&clubid=20170537&articleid=604465 |
| 2026-05-16T04:16:00+0900 | 육아친구부산 | 오늘의 한마디 | https://cafe.naver.com/ArticleRead.nhn?menuid=324&boardtype=L&clubid=18599406&articleid=1197425 |
| 2026-05-16T04:21:00+0900 | 맘스스토리 | 유아용품 사용후기 | https://cafe.naver.com/ArticleRead.nhn?menuid=419&boardtype=L&clubid=12876544&articleid=600385 |
| 2026-05-16T18:10:00+0900 | 아가방앤컴퍼니 공식카페 아가베베 | 일상수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=631&boardtype=L&clubid=20170537&articleid=604473 |
| 2026-05-16T18:13:00+0900 | 베베라운지 | 오늘 토크라운지 | https://cafe.naver.com/ArticleRead.nhn?menuid=159&boardtype=L&clubid=24081850&articleid=1294957 |
| 2026-05-16T18:15:00+0900 | 사과나무맘스홀릭 | ●자유 수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=75&boardtype=L&clubid=21451316&articleid=803546 |
| 2026-05-16T20:17:00+0900 | 모비맘카페 | 자유게시판 | https://cafe.naver.com/ArticleRead.nhn?menuid=1&boardtype=L&clubid=28140611&articleid=173758 |
| 2026-05-16T20:21:00+0900 | 육아친구인천 | ♡임신/출산/육아 | https://cafe.naver.com/ArticleRead.nhn?menuid=451&boardtype=L&clubid=18177992&articleid=706325 |
| 2026-05-16T20:26:00+0900 | 나는엄마다 맘카페 | ✿ 임신맘 수다방 ✿ | https://cafe.naver.com/ArticleRead.nhn?menuid=240&boardtype=L&clubid=25139350&articleid=1037808 |
| 2026-05-16T22:12:00+0900 | 매터니티스쿨 | 도란도란! 수다방 | https://cafe.naver.com/ArticleRead.nhn?menuid=91&boardtype=L&clubid=17523807&articleid=396932 |
| 2026-05-16T22:14:00+0900 | 베베라운지 | 오늘 토크라운지 | https://cafe.naver.com/ArticleRead.nhn?menuid=159&boardtype=L&clubid=24081850&articleid=1294965 |
| 2026-05-16T22:15:00+0900 | 육아친구 광주.전남 | 이 야 기 방 | https://cafe.naver.com/ArticleRead.nhn?menuid=224&boardtype=L&clubid=20268063&articleid=376961 |
