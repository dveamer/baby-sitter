---
name: naver-blog-comment-assist
description: Leave verified reactions and comments on Naver Blog posts through the user's logged-in Chrome extension session. Use when Codex must open `blog.naver.com` or direct `PostView.naver` posts in the existing Chrome profile, check duplicate history and live comment lists, click `공감`, write a supplied comment, attach a local image through the real Naver file chooser UI, verify that the comment actually persisted after reload, and classify known failures such as login loss, extension bridge outages, missing visible comment buttons, hidden write links without an editor, or submit alerts.
---

# Naver Blog Comment Assist

Use this skill for repeatable Naver Blog interaction work, not for generating comment copy.

## Runtime

- Use the Chrome extension session only.
- Bootstrap the browser through `node_repl` and the bundled `browser-client.mjs` path from the installed Chrome plugin.
- Read `browser.documentation()` in full before driving tabs.
- Do not use `mcp__chrome_devtools`, OpenChrome, raw CDP scripts, Playwright-launched browsers, or direct comment/image APIs for posting.

Use this bootstrap once per fresh browser session:

```js
const { setupBrowserRuntime } = await import("/Users/dveamer/.codex/plugins/cache/openai-bundled/chrome/latest/scripts/browser-client.mjs");
await setupBrowserRuntime({ globals: globalThis });
globalThis.browser = await agent.browsers.get("extension");
nodeRepl.write(await browser.documentation());
```

If the extension browser is unavailable, read `await agent.documentation.get("chrome-troubleshooting")`, repair the extension path, and retry. Do not fall back to another browser surface.

## Tab Strategy

- Prefer one new Chrome extension tab per target post.
- Prefer the direct article URL for DOM work:
  `https://blog.naver.com/PostView.naver?blogId=<blogId>&logNo=<logNo>`
- Reuse the same tab for open, type, upload, submit, reload, and verification.
- Finish with `browser.tabs.finalize({ keep: [] })` unless a live login tab must remain for user handoff.

## Duplicate Protection

Check duplicates in this order:

1. Normalize wrapper and direct URLs to the same `blogId:logNo` key and compare against the local results ledger first.
2. Open the live comment widget and inspect the actual rendered comment list.
3. Treat any of these as an existing self-comment:
   - current account nickname already appears on the matching comment
   - the first line of the planned comment appears
   - `babysitter.dveamer.com` appears
   - the same attached image is already visible

If the post is a verified duplicate, do not post again.

## Login Check

- Proceed only when the page clearly shows the logged-in account state such as `내정보 보기`.
- If the page shows `로그인` or redirects to `https://nid.naver.com/nidlogin.login`, stop and ask the user to log in.
- Do not automate credential entry unless the user explicitly provides credentials and asks for that.

## Add Sympathy

- Inspect the direct page first.
- Prefer these like controls:
  - `a.u_likeit_button._face`
  - `a.u_likeit_list_button._button`
- Treat already-liked state separately when `aria-pressed="true"` or the class already contains the active state.
- If the like button is visible in the DOM snapshot but Playwright targeting is ambiguous, use `tab.dom_cua.get_visible_dom()` plus `tab.dom_cua.click(...)`.
- Record `like-button-missing`, `already-liked`, or `liked` distinctly.

## Open The Comment Widget

- Scroll toward the comment area before concluding the widget is missing.
- Prefer the visible `댓글` button exposed in `tab.dom_cua.get_visible_dom()`.
- DOM CUA clicks on the visible `댓글 1` style button are more reliable here than guessed Playwright role locators.
- After opening, expect the live widget to expose existing comments plus a composer.

When the visible button never appears:

- Check whether the page only exposes hidden comment structures such as:
  - `#naverComment_201_<logNo>_ct`
  - `a._naverCommentWriteBtn`
- If the hidden write link exists but force click still reports no clickable bounding box and no editor appears, classify the post as `hidden-link-no-editor`.
- Do not inject submission logic or switch to API fallback to bypass this state.

## Write The Comment

- Expect the composer to appear as a `contenteditable="true"` node, often with title `댓글`.
- Do not rely on the old `#naverComment_201_<logNo>__write_textarea` id being interactable in the extension flow.
- Click the visible composer node, then type through DOM CUA.
- Verify that the editor text now contains the first line of the supplied comment or `babysitter.dveamer.com` before uploading an image or submitting.

## Attach An Image

- Read `await agent.documentation.get("file-management")` before implementing upload behavior.
- Use the real file chooser flow against the visible Naver upload control.
- Prefer a visible `input[type="file"]` when it exists.
- Wait for the chooser before clicking the control, then call `chooser.setFiles([...])` with an absolute path.
- Treat the upload as successful only when the widget shows `업로드 취소` or the thumbnail preview.
- If file upload errors indicate blocked file URL access, instruct the user to enable `Allow access to file URLs` for the Codex extension in `chrome://extensions`.

Do not use:

- `DOM.setFileInputFiles`
- upload session APIs
- comment create/list APIs
- any direct HTTP fallback for image submission

## Submit And Verify

- Prefer the visible `등록` button from the live widget.
- After clicking submit, immediately check `tab.getJsDialog()`.
- If a JavaScript `alert` appears, dismiss it and treat the attempt as unverified unless a later reload proves the comment persisted.
- A composer that still contains the text after submit is not a success signal.

Verify success only after a reload or fresh widget reopen:

- the new comment is present in the rendered list
- the first line or `babysitter.dveamer.com` is present
- the new timestamp is visible
- the attached thumbnail appears when an image was requested

If the comment briefly appears only inside the composer DOM and disappears after reload, classify it as `post-not-verified` or `submit-alert`, not success.

## Failure Labels

Use these labels consistently:

- `login-required`: Chrome session is not logged in.
- `browser-bridge-unavailable`: extension browser could not be acquired or the runtime dropped.
- `comment-button-missing`: no visible comment path opened after scroll and retry.
- `hidden-link-no-editor`: hidden comment link exists but never creates a live editor.
- `image-upload-failed`: chooser or preview step failed.
- `submit-alert`: submit opened a JavaScript alert and the comment did not persist after reload.
- `post-not-verified`: submit completed but the real comment list still does not contain the new comment.
- `comments-disabled`: the post blocks comments, spam filtering, or `잘못된 접근 방법` prevents live posting.
- `duplicate`: live or ledger check proves the post already has our comment.

## Recovery Notes

- If the browser bridge dies during setup, reconnect the extension session first, then continue.
- Avoid mixing reconnection with extra parallel browser documentation calls; this environment has previously hit transport-closed failures during that pattern.
- If a prior run left tabs behind, reconnect and clean them up with `browser.tabs.finalize({ keep: [] })` before starting the next batch.
