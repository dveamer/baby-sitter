
# 네이버 블로그 댓글 작성

promotion-tasks.md 파일에 작성되어있는 tasks 들 중 3개를 뽑아서 각 tasks 에 적힌 블로그 글에 공감을 누르고 댓글을 달아줘. 댓글에 homepage-img-1-ko.png 이미지 파일도 첨부해줘. 

공감, 댓글, 이미지 첨부는 naver-blog-comment-assist skill 을 이용하면돼.

글 작성을 위해서 로그인이 필요할거야. open-chrome을 이용하고 내가 로그인해주면 그다음에 진행하면돼.

## 중복 등록 방지

댓글을 남기기 전에 반드시 이미 등록한 블로그인지 확인해줘.

1. promotion-results.md 에 같은 블로그 글이 이미 기록되어 있는지 먼저 확인해줘.
   - wrapper URL(`https://blog.naver.com/<blogId>/<logNo>`)과 direct URL(`https://blog.naver.com/PostView.naver?blogId=<blogId>&logNo=<logNo>`)은 같은 글로 보고 비교해줘.
   - URL 문자열이 달라도 `blogId`와 `logNo`가 같으면 이미 처리한 글로 판단해줘.
2. 브라우저에서 댓글 위젯을 연 뒤, 실제 댓글 목록에도 우리 계정이 이미 남긴 댓글이 있는지 확인해줘.
   - 댓글 작성 전에 현재 로그인 계정의 닉네임/프로필과 기존 댓글 목록을 비교해줘.
   - 우리 댓글 첫 문장, `babysitter.dveamer.com`, 또는 같은 이미지 첨부가 이미 보이면 다시 등록하지 마.
3. 이미 등록된 글이면 댓글과 이미지를 다시 올리지 말고, 해당 task 는 중복으로 판단해서 promotion-tasks.md 에서 제거하고 promotion-results.md 에 "이미 등록된 글이라 재등록하지 않음"으로 기록해줘.
4. 댓글 허용 안 됨, 권한 없음, 잘못된 접근처럼 실제 등록이 되지 않은 글은 완료 처리하지 말고 promotion-tasks.md 에 남겨둬. 단, 같은 실패가 반복되면 결과 파일에는 별도 실패 사유만 기록해도 돼.

## 이미지 첨부 주의

댓글 텍스트 작성까지는 정상인데 이미지 업로드 과정에서 `잘못된 접근 방법` 문제가 날 수 있어. 이미지 첨부는 직접 API 호출이나 세션 키 업로드 우회 방식으로 처리하지 말고, 네이버 댓글 UI 의 실제 이미지 추가 흐름을 사용해줘.

- 댓글 위젯의 `파일 선택` / `이미지 추가` 버튼 또는 `input.u-cbox-browse-file-input`에 `homepage-img-1-ko.png`를 넣는 방식부터 시도해줘.
- 업로드 후 썸네일과 `업로드 취소` 컨트롤이 보이는지 확인한 뒤에만 `등록`을 눌러줘.
- 이미지 업로드 단계에서 `잘못된 접근 방법`이 나오면 텍스트만 등록하지 말고 해당 글은 완료 처리하지 마.
- API fallback 으로 이미지 업로드를 재시도하지 말고, 다른 UI 기반 업로드 방법을 찾아 적용해줘.

작업이 완료된 task 는 promotion-tasks.md 에서 제거해주고 promotion-results.md 에 기록해줘. 

작업이 완료되면 사용했던 chrome-devtools(openchrome) 은 종료해줘. 


## 주의 사항

chrome-devtools(openchrome) 사용에 문제가 있다면 아래 같은 문제 점이 예상돼.
  1. chrome-devtools(openchrome) 에서 네이버 로그인이 되어있지 않아서 
  2. 이전에 사용했던 chrome-devtools(openchrome) 리소스가 정리가 되지 않아서 
  
1번이 원인일 경우에는 대기해서 내가 로그인해주는 수밖에 없어. 
2번이 원인일 경우에는 chrome-devtools(openchrome) 리소스를 종료 시키면 다음 스캐쥴에서는 정상적으로 동작할 것으로 예상돼

문제점이 생겼을 때 Playwright 를 사용해서 해결해보려고하지마. 성공하는 것이 기록된 적이 없어.
