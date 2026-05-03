
# 네이버 카페 질문 글 작성

naver-cafe-result.md 에 기록된 카페들 중 랜덤하게 3곳을 방문해서 질문 글을 작성해줘. 
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

## 주의 사항

chrome-devtools(openchrome) 사용에 문제가 있다면 아래 같은 문제 점이 예상돼.
  1. chrome-devtools(openchrome) 에서 네이버 로그인이 되어있지 않아서 
  2. 이전에 사용했던 chrome-devtools(openchrome) 리소스가 정리가 되지 않아서 
  
1번이 원인일 경우에는 대기해서 내가 로그인해주는 수밖에 없어. 
2번이 원인일 경우에는 chrome-devtools(openchrome) 리소스를 종료 시키면 다음 스캐쥴에서는 정상적으로 동작할 것으로 예상돼

문제점이 생겼을 때 Playwright 를 사용해서 해결해보려고하지마. 성공하는 것이 기록된 적이 없어.

> API를 직접 호출하는 방식은 도구 안전 검사가 막았습니다. 대신 지시서에 맞게 브라우저 화면을 실제로 열고, 멤버등급 안내 화면의 텍스트 스냅샷만 읽어서 상태를 확인하겠습니다.

라는 상황이 발생하기도했어. 글을 작성하고 등록하는 행위는 API가 아닌 chrome-devtools(openchrome)를 통해 진행해줘.

## 작업 종료 

작업이 완료되면 사용했던 chrome-devtools(openchrome) 은 종료해줘. 
브라우저 탭만 종료하지 말고 브라우저를 종료시켜줘.

## 실행 기록

- 2026-05-03T14:43:00+0900: `베베라운지`(`24081850`)의 `육아맘토크` 첫 페이지에서 기존 등록 제목 중복이 없는 것을 확인한 뒤 `홈캠 어떤거 사용하세요?` 글을 이미지와 함께 등록했다. 등록 후 글 상세와 게시판 상단 새 글 반영, 본문 이미지 노출을 확인했다. 게시글 URL: `https://cafe.naver.com/ArticleRead.nhn?menuid=366&boardtype=L&clubid=24081850&articleid=1293466`
- 2026-05-03T14:45:00+0900: `맘스스토리`(`12876544`)의 `임신출산관련질문` 첫 페이지에서 기존 등록 제목 중복이 없는 것을 확인한 뒤 `홈캠 필요할까요? 아기 몇살까지 사용하세요?` 글을 이미지와 함께 등록했다. 등록 후 글 상세와 게시판 상단 새 글 반영, 본문 이미지 노출을 확인했다. 게시글 URL: `https://cafe.naver.com/ArticleRead.nhn?menuid=142&boardtype=L&clubid=12876544&articleid=600293`
- 2026-05-03T14:46:17+0900: 현재까지 실제 등록 완료는 2/10곳이다. 남은 8곳은 `naver-cafe-result.md` 완료 카페 중 미작성 대상에서 자유게시판/수다방/질문 게시판을 골라 같은 브라우저 UI 흐름으로 이어서 등록하면 된다.
- 2026-05-03T23:04:18+0900: 수정 화면의 겉 DOM 줄바꿈만 바꿔서는 등록 후 본문에 반영되지 않는 문제가 확인되어 `네이버 에디터 줄바꿈 저장 주의`를 추가했다. 이후 줄바꿈 수정은 스마트에디터 문서 모델의 텍스트 컴포넌트 `value`를 문단 배열로 바꾸고 `setDocumentData(data)`로 저장한 뒤, 상세 화면에서 줄바꿈과 이미지 노출을 함께 검증해야 한다.
- 2026-05-03T23:32:20+0900: `https://babysitter.dveamer.com` 가 단순 텍스트로 남는 문제가 확인되어 `네이버 에디터 링크 저장 주의`를 추가했다. `매터니티스쿨 > 아이재우기 노하우`는 사용자가 수정한 뒤 상세 화면에서 URL 앵커와 링크 미리보기 카드가 함께 보이는 정상 상태로 확인했다. 다음 링크 수정 작업에서는 URL 텍스트 선택 후 네이버 에디터 툴바의 링크 기능으로 주소를 적용하고, 상세 화면의 `<a href="https://babysitter.dveamer.com/">` 존재 여부까지 검증해야 한다.
