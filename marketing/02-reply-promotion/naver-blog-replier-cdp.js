const fs = require('fs');
const path = require('path');
const http = require('http');
const { spawn } = require('child_process');
const WebSocket = require('ws');

const ROOT = '/Users/dveamer/workspace/baby-sitter/marketing/02-reply-promotion';
const RUN_ID = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
}).format(new Date()).replace(/[^\d]/g, '');
const PROFILE_DIR = process.env.NAVER_REPLIER_PROFILE_DIR
  || path.join(ROOT, '.openchrome', `naver-replier-${RUN_ID}`);
const IMAGE_PATH = path.join(ROOT, 'homepage-img-1-ko.png');
const CHROME_PATH = process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const TASKS_PATH = path.join(ROOT, 'promotion-tasks.md');
const RESULTS_PATH = path.join(ROOT, 'promotion-results.md');
const CDP_PORT = 9223;
const TASK_LIMIT = 5;
const TASK_SCAN_LIMIT = 20;
const TASK_URLS = (process.env.NAVER_REPLIER_TASK_URLS || '')
  .split('\n')
  .map((value) => value.trim())
  .filter(Boolean);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function nowKstStamp() {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: false,
  }).format(new Date()).replace(/\s+/g, ' ');
}

function todayKstDate() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date());
}

function parseTasks(markdown) {
  const sections = markdown.split(/^### 사이트 : /m);
  sections.shift();
  return sections.map((section) => {
    const [urlLine, ...restLines] = section.split('\n');
    const url = urlLine.trim();
    const body = restLines.join('\n').trimEnd();
    const comment = body
      .split(/\n이미지 첨부\s*:/)[0]
      .trim()
      .replace(/  $/gm, '')
      .replace(/\n{3,}/g, '\n\n');
    return { originalUrl: url, url, comment, fullBlock: `### 사이트 : ${url}\n\n${body}` };
  });
}

function removeCompletedTasks(markdown, processed) {
  const [prefix] = markdown.split(/^### 사이트 : /m);
  const urls = new Set(processed.map((task) => task.originalUrl));
  const remaining = parseTasks(markdown)
    .filter((task) => !urls.has(task.originalUrl))
    .map((task) => task.fullBlock);
  return `${prefix.trimEnd()}\n\n${remaining.join('\n\n')}\n`.replace(/\n{3,}/g, '\n\n');
}

function appendResults(markdown, results) {
  const dateHeader = `## ${todayKstDate()}`;
  const lines = results.map((result) => {
    if (result.commentStatus === 'duplicate') {
      return `- \`${result.originalUrl}\` 이미 등록된 글이라 재등록하지 않음. 확인 시각: \`${result.timestamp}\``;
    }
    const likeText = result.likeStatus === 'like-button-missing'
      ? '공감 버튼 비노출'
      : result.likeStatus === 'already-liked'
        ? '공감 이미 완료 상태'
        : result.likeStatus === 'liked'
          ? '공감 완료'
          : `공감 상태 ${result.likeStatus}`;
    return `- \`${result.originalUrl}\` ${likeText}, 댓글 등록 완료, 이미지 \`homepage-img-1-ko.png\` 첨부 완료. 확인 시각: \`${result.timestamp}\``;
  });

  if (markdown.includes(dateHeader)) {
    return markdown.replace(dateHeader, `${dateHeader}\n\n${lines.join('\n')}`);
  }
  return `${dateHeader}\n\n${lines.join('\n')}\n\n${markdown.trimStart()}`.trimEnd() + '\n';
}

function parseBlogParts(url) {
  const parsed = new URL(url);
  const pathParts = parsed.pathname.split('/').filter(Boolean);
  const blogId = parsed.searchParams.get('blogId') || pathParts[0] || '';
  const logNo = parsed.searchParams.get('logNo') || parsed.pathname.match(/\/(\d+)$/)?.[1] || '';
  return { blogId, logNo, key: blogId && logNo ? `${blogId}:${logNo}` : null };
}

function directUrl(taskUrl) {
  const { blogId, logNo } = parseBlogParts(taskUrl);
  return `https://blog.naver.com/PostView.naver?blogId=${encodeURIComponent(blogId)}&logNo=${encodeURIComponent(logNo)}`;
}

function resultBlogKeys(markdown) {
  const keys = new Set();
  const lines = markdown.split('\n');
  for (const line of lines) {
    const isCompleted = line.includes('댓글 등록 완료');
    const isDuplicate = line.includes('이미 등록된 글이라 재등록하지 않음');
    if (!isCompleted && !isDuplicate) continue;
    const match = line.match(/https:\/\/blog\.naver\.com\/[^\s`)]+/);
    if (!match) continue;
    const key = parseBlogParts(match[0]).key;
    if (key) keys.add(key);
  }
  return keys;
}

function requestJson(method, pathName) {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port: CDP_PORT, method, path: pathName }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => { body += chunk; });
      res.on('end', () => {
        try {
          resolve(body ? JSON.parse(body) : null);
        } catch (error) {
          reject(new Error(`${method} ${pathName} returned non-JSON: ${body.slice(0, 120)}`));
        }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

class CdpPage {
  constructor(wsUrl) {
    this.wsUrl = wsUrl;
    this.nextId = 1;
    this.pending = new Map();
  }

  async connect() {
    this.ws = new WebSocket(this.wsUrl);
    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (!message.id) return;
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      if (message.error) pending.reject(new Error(message.error.message));
      else pending.resolve(message.result || {});
    };
    await new Promise((resolve, reject) => {
      this.ws.onopen = resolve;
      this.ws.onerror = reject;
    });
    await this.send('Page.enable');
    await this.send('Runtime.enable');
    await this.send('DOM.enable');
  }

  send(method, params = {}) {
    const id = this.nextId++;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      setTimeout(() => {
        if (this.pending.delete(id)) reject(new Error(`CDP timeout: ${method}`));
      }, 60000);
    });
  }

  async evaluate(expression, timeoutMs = 60000) {
    const result = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
      timeout: timeoutMs,
    });
    if (result.exceptionDetails) {
      throw new Error(result.exceptionDetails.text || 'Runtime evaluation failed');
    }
    return result.result?.value;
  }

  async waitFor(expression, timeoutMs = 30000, intervalMs = 500) {
    const started = Date.now();
    let lastError;
    while (Date.now() - started < timeoutMs) {
      try {
        const value = await this.evaluate(expression, Math.min(5000, timeoutMs));
        if (value) return value;
      } catch (error) {
        lastError = error;
      }
      await sleep(intervalMs);
    }
    throw lastError || new Error(`Timed out waiting for ${expression.slice(0, 80)}`);
  }

  async setInputFile(selector, filePath) {
    const search = await this.send('DOM.performSearch', { query: selector, includeUserAgentShadowDOM: true });
    if (!search.resultCount) throw new Error(`file input not found: ${selector}`);
    const nodes = await this.send('DOM.getSearchResults', {
      searchId: search.searchId,
      fromIndex: 0,
      toIndex: search.resultCount,
    });
    await this.send('DOM.setFileInputFiles', { nodeId: nodes.nodeIds[0], files: [filePath] });
    await this.send('DOM.discardSearchResults', { searchId: search.searchId }).catch(() => {});
  }

  close() {
    this.ws?.close();
  }
}

async function waitForCdp() {
  const started = Date.now();
  while (Date.now() - started < 30000) {
    try {
      await requestJson('GET', '/json/version');
      return;
    } catch {
      await sleep(500);
    }
  }
  throw new Error('Chrome CDP port did not open');
}

async function launchChrome() {
  fs.mkdirSync(PROFILE_DIR, { recursive: true });
  const chrome = spawn(CHROME_PATH, [
    `--remote-debugging-port=${CDP_PORT}`,
    `--user-data-dir=${PROFILE_DIR}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-breakpad',
    '--disable-crash-reporter',
    '--disable-crashpad',
    'about:blank',
  ], { stdio: 'ignore', detached: true });
  await waitForCdp();
  return chrome;
}

async function newCdpPage(url) {
  const target = await requestJson('PUT', `/json/new?${encodeURIComponent(url)}`);
  const page = new CdpPage(target.webSocketDebuggerUrl);
  await page.connect();
  await page.waitFor('document.readyState === "interactive" || document.readyState === "complete"', 60000);
  return page;
}

async function ensureLoggedIn(probeUrl) {
  const page = await newCdpPage(probeUrl);
  const loggedIn = await page.evaluate(`(() => {
    const text = document.body?.innerText || '';
    return text.includes('내정보 보기') || text.includes('로그아웃') || (!location.href.includes('nid.naver.com') && !text.includes('로그인'));
  })()`);
  if (loggedIn) {
    page.close();
    return;
  }
  await page.send('Page.navigate', { url: 'https://nid.naver.com/nidlogin.login' });
  console.log('LOGIN_REQUIRED 브라우저에서 네이버 로그인을 완료해주세요. 최대 10분 대기합니다.');
  const started = Date.now();
  while (Date.now() - started < 10 * 60 * 1000) {
    const ok = await page.evaluate(`(() => {
      const text = document.body?.innerText || '';
      return text.includes('내정보 보기') || text.includes('로그아웃') || (!location.href.includes('nid.naver.com') && !text.includes('로그인'));
    })()`).catch(() => false);
    if (ok) {
      page.close();
      return;
    }
    await sleep(3000);
  }
  page.close();
  throw new Error('네이버 로그인 대기 시간이 초과되었습니다.');
}

async function runTask(task) {
  const { logNo } = parseBlogParts(task.originalUrl);
  const page = await newCdpPage(directUrl(task.originalUrl));
  try {
    await page.waitFor('document.readyState === "complete"', 60000).catch(() => {});
    await page.evaluate(`(() => {
      if (window.naverCommentController && typeof window.naverCommentController._autoOpenComment === 'function') {
        window.naverCommentController._autoOpenComment({
          blogNo: '${logNo}',
          ticketNo: '201',
          targetNo: '${logNo}',
          aFormation: ['list', 'page', 'write'],
          isPostComment: true,
          nPageSize: 50,
          sPageType: 'default'
        });
      } else {
        const button = [...document.querySelectorAll('a,button')].find((el) => /댓글/.test(el.textContent || ''));
        if (button) button.click();
      }
      return true;
    })()`);

    const editorSelector = `#naverComment_201_${logNo}__write_textarea`;
    await page.waitFor(`!!document.querySelector(${JSON.stringify(editorSelector)})`, 30000);
    const duplicate = await page.evaluate(`(() => {
      const root = document.querySelector('.u_cbox') || document;
      const text = root.innerText || '';
      return text.includes('babysitter.dveamer.com') || text.includes(${JSON.stringify(task.comment.split('\n')[0])});
    })()`);
    if (duplicate) {
      return { ...task, likeStatus: 'not-retried', commentStatus: 'duplicate', timestamp: nowKstStamp() };
    }

    const likeStatus = await page.evaluate(`(() => {
      const selectors = ['a.u_likeit_button._face', 'a.u_likeit_list_button._button'];
      const el = selectors.map((selector) => document.querySelector(selector)).find(Boolean);
      if (!el) return 'like-button-missing';
      const pressed = el.getAttribute('aria-pressed');
      const className = el.getAttribute('class') || '';
      if (pressed === 'true' || /\\bon\\b/.test(className)) return 'already-liked';
      el.scrollIntoView({ block: 'center', inline: 'center' });
      ['mouseover', 'mousedown', 'mouseup', 'click'].forEach((type) => {
        el.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, view: window }));
      });
      return 'liked';
    })()`);

    await sleep(1000);
    await page.evaluate(`(() => {
      const el = document.querySelector(${JSON.stringify(editorSelector)});
      if (!el) throw new Error('editor not found');
      el.focus();
      el.innerHTML = '';
      document.execCommand('insertText', false, ${JSON.stringify(task.comment)});
      el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: ${JSON.stringify(task.comment)} }));
      return (el.textContent || '').length;
    })()`);
    await page.waitFor(`(document.querySelector(${JSON.stringify(editorSelector)})?.textContent || '').includes('babysitter.dveamer.com')`, 10000);

    await page.setInputFile('input.u-cbox-browse-file-input', IMAGE_PATH);
    await page.waitFor(`(document.body.innerText || '').includes('업로드 취소')`, 30000);

    await page.evaluate(`(() => {
      const btn = document.querySelector('.u_cbox_btn_upload') || [...document.querySelectorAll('button,a')].find((el) => (el.textContent || '').trim() === '등록');
      if (!btn) throw new Error('submit button not found');
      btn.click();
      return true;
    })()`);

    await page.waitFor(`(() => {
      const root = document.querySelector('.u_cbox') || document;
      const text = root.innerText || '';
      return text.includes('babysitter.dveamer.com') && !((document.querySelector(${JSON.stringify(editorSelector)})?.textContent || '').includes('babysitter.dveamer.com'));
    })()`, 30000);

    return { ...task, likeStatus, commentStatus: 'posted', timestamp: nowKstStamp() };
  } finally {
    page.close();
  }
}

async function main() {
  const tasksMarkdown = fs.readFileSync(TASKS_PATH, 'utf8');
  const resultsMarkdown = fs.readFileSync(RESULTS_PATH, 'utf8');
  const existingKeys = resultBlogKeys(resultsMarkdown);
  const tasks = parseTasks(tasksMarkdown);
  const selected = [];
  const duplicates = [];
  const candidateTasks = TASK_URLS.length
    ? TASK_URLS.map((url) => tasks.find((task) => task.originalUrl === url)).filter(Boolean)
    : tasks.slice(0, TASK_SCAN_LIMIT);
  for (const task of candidateTasks) {
    if (selected.length >= TASK_LIMIT && !TASK_URLS.length) break;
    const key = parseBlogParts(task.originalUrl).key;
    if (key && existingKeys.has(key)) {
      duplicates.push({ ...task, likeStatus: 'not-retried', commentStatus: 'duplicate', timestamp: nowKstStamp() });
    } else {
      selected.push(task);
    }
  }

  const chrome = await launchChrome();
  const completed = [];
  const skipped = [];
  try {
    if (!selected.length) throw new Error('처리할 신규 task가 없습니다.');
    await ensureLoggedIn(directUrl(selected[0].originalUrl));
    const results = await Promise.all(selected.map(async (task) => {
      console.log(`START ${task.originalUrl}`);
      try {
        const result = await runTask(task);
        console.log(`${result.commentStatus === 'posted' ? 'DONE' : 'DUPLICATE_LIVE'} ${task.originalUrl}`);
        return result;
      } catch (error) {
        console.log(`SKIP ${task.originalUrl} ${error.message}`);
        return { ...task, commentStatus: 'error', skipReason: error.message, timestamp: nowKstStamp() };
      }
    }));
    for (const result of results) {
      if (result.commentStatus === 'posted') completed.push(result);
      else if (result.commentStatus === 'duplicate') duplicates.push(result);
      else skipped.push(result);
    }

    const processed = completed.concat(duplicates);
    if (processed.length) {
      fs.writeFileSync(TASKS_PATH, removeCompletedTasks(tasksMarkdown, processed));
      fs.writeFileSync(RESULTS_PATH, appendResults(resultsMarkdown, processed));
    }
    console.log(JSON.stringify({ ok: true, completed, duplicates, skipped }, null, 2));
  } finally {
    try {
      chrome.kill('SIGTERM');
    } catch {}
  }
}

main().catch((error) => {
  console.error(JSON.stringify({ ok: false, error: error.message, stack: error.stack }, null, 2));
  process.exit(1);
});
