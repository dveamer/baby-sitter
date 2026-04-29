---
name: naver-blog-comment-assist
description: Assist with leaving reactions and comments on Naver Blog posts through a logged-in Chrome session. Use when Codex needs to open a `blog.naver.com` post, verify that Chrome is logged in, click the `공감` button, write a supplied comment, optionally attach an image, and reuse stable DOM patterns for similar Naver Blog posts.
---

# Naver Blog Comment Assist

Use this skill for repeatable Naver Blog interaction work, not for generating comment copy. The main value is turning one successful browser run into a reusable DOM procedure.

## Workflow

1. Open the target post in Chrome DevTools.
2. Verify whether the active Chrome session is logged in to Naver.
3. Add `공감`.
4. Open the Naver comment widget.
5. Write the requested comment.
6. Attach an image if requested.
7. Submit and verify the rendered comment.

## Login Check

- If the page shows a visible `로그인` link or redirects to `https://nid.naver.com/nidlogin.login`, stop and ask the user to log in.
- If the page shows the logged-in account menu such as `내정보 보기`, proceed.
- Do not attempt to automate credential entry unless the user explicitly asks for that and provides the credentials.

## Stable Page Choice

- If the user gives a wrapper URL such as `https://blog.naver.com/<blogId>/<logNo>`, start there.
- If clicks on `공감` or `댓글` time out on the wrapper page, open the direct article URL instead:
  `https://blog.naver.com/PostView.naver?blogId=<blogId>&logNo=<logNo>`
- Prefer the direct `PostView.naver` page for DOM work because the wrapper page may add an extra iframe layer.

## Add Sympathy

- First try the direct page and inspect these selectors:
  - `a.u_likeit_button._face`
  - `a.u_likeit_list_button._button`
- If a normal a11y-tree click times out, dispatch mouse events with `evaluate_script`:
  - `mouseover`
  - `mousedown`
  - `mouseup`
  - `click`
- Treat the action as successful only if at least one of these changes is visible:
  - class changes from `off` to `on`
  - `aria-pressed` changes from `false` to `true`
  - the count increases

## Open The Comment Widget

- Prefer the built-in controller over brittle button clicks:
  - `window.naverCommentController._autoOpenComment()`
- The observed auto-open payload shape is:

```js
{
  blogNo,
  ticketNo: "201",
  targetNo: paramLogNo,
  aFormation: ["list", "page", "write"],
  isPostComment: true,
  nPageSize: 50,
  sPageType: "default"
}
```

- After bootstrapping, verify that `.u_cbox` content appears and that the write area is present.

## Write The Comment

- Expect the editor id pattern:
  - `naverComment_201_<logNo>__write_textarea`
- In the DevTools a11y snapshot this may appear as a generic focused element rather than a textbox.
- Click the editor, then type the supplied comment text.
- Verify that the visible character count increases before attaching files or submitting.

## Attach An Image

- The upload control may appear as either:
  - `input.u-cbox-browse-file-input`
  - an a11y button labeled `파일 선택` with description `이미지 추가`
- Wait until the uploaded thumbnail appears before submitting.
- Treat the attachment as successful only if the comment box renders a thumbnail preview with an `업로드 취소` control.

## DOM Simplification Fallback

- If the a11y snapshot is too noisy to expose the upload control or submit button, isolate the original comment widget node:
  - find `#naverComment_201_<logNo>_ct`
  - move that original node under `document.body`
  - then snapshot again
- Move the original node, not a clone. Cloning can drop event wiring.

## Submit And Verify

- Click `등록`.
- Confirm that the newly rendered comment appears in the list with:
  - the typed text
  - the attached thumbnail, if any
  - a fresh timestamp
- If the text stays in the composer and no new comment renders, inspect for spam or write-restriction notices before retrying.

## Reusable Selectors

- Sympathy:
  - `a.u_likeit_button._face`
  - `a.u_likeit_list_button._button`
- Comment container:
  - `#naverComment_201_<logNo>_ct`
- Comment editor:
  - `#naverComment_201_<logNo>__write_textarea`
- Upload input:
  - `input.u-cbox-browse-file-input`
- Submit button:
  - a11y button labeled `등록`
