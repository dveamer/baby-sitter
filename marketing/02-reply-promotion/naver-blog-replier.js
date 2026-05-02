const { chromium, firefox } = require('playwright');
const fs = require('fs');
const path = require('path');

const ROOT = '/Users/dveamer/workspace/baby-sitter/marketing/02-reply-promotion';
const PROFILE_DIR = '/tmp/naver-blog-replier-profile';
const IMAGE_PATH = path.join(ROOT, 'homepage-img-1-ko.png');
const CHROME_PATH = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const CDP_ENDPOINT = 'http://127.0.0.1:9222';
const TASKS_PATH = path.join(ROOT, 'promotion-tasks.md');
const RESULTS_PATH = path.join(ROOT, 'promotion-results.md');
const TASK_LIMIT = 3;
const TASK_SCAN_LIMIT = 8;

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
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
  }).format(new Date());
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
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

    return {
      originalUrl: url,
      url,
      comment,
      fullBlock: `### 사이트 : ${url}\n\n${body}`,
    };
  });
}

function removeCompletedTasks(markdown, completedBlocks) {
  const [prefix] = markdown.split(/^### 사이트 : /m);
  const completedUrls = new Set(
    completedBlocks.map((block) => block.match(/^### 사이트 : (.+)$/m)?.[1]?.trim()).filter(Boolean),
  );
  const remaining = parseTasks(markdown)
    .filter((task) => !completedUrls.has(task.originalUrl))
    .map((task) => task.fullBlock);

  const body = remaining.length ? `${remaining.join('\n\n')}\n` : '';
  return `${prefix.trimEnd()}\n\n${body}`.replace(/\n{3,}/g, '\n\n').trimEnd() + '\n';
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

function parseLogNo(url) {
  const parsed = new URL(url);
  return parsed.searchParams.get('logNo') || url.match(/\/(\d+)$/)?.[1] || null;
}

function parseBlogKey(url) {
  const parsed = new URL(url);
  const blogId = parsed.searchParams.get('blogId') || parsed.pathname.split('/').filter(Boolean)[0] || '';
  const logNo = parsed.searchParams.get('logNo') || parsed.pathname.match(/\/(\d+)$/)?.[1] || '';
  return blogId && logNo ? `${blogId}:${logNo}` : null;
}

function resultBlogKeys(markdown) {
  const keys = new Set();
  for (const match of markdown.matchAll(/https:\/\/blog\.naver\.com\/[^\s`)]+/g)) {
    const key = parseBlogKey(match[0]);
    if (key) keys.add(key);
  }
  return keys;
}

async function ensureLoggedIn(page, probeUrl) {
  await page.goto(probeUrl, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  await page.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {});

  const loggedIn = await page.evaluate(() => {
    const text = document.body.innerText || '';
    return text.includes('내정보 보기') || text.includes('로그아웃') || !text.includes('로그인');
  });
  if (loggedIn) return;

  console.log('LOGIN_REQUIRED');
  await page.goto('https://nid.naver.com/nidlogin.login', {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  console.log('브라우저에서 네이버 로그인을 완료할 때까지 대기합니다. 최대 10분.');

  const started = Date.now();
  while (Date.now() - started < 10 * 60 * 1000) {
    await sleep(3000);
    const url = page.url();
    if (url.includes('nidlogin.login')) {
      const stillLogin = await page.evaluate(() => {
        const text = document.body.innerText || '';
        return text.includes('로그인') && !text.includes('내정보 보기');
      }).catch(() => true);
      if (stillLogin) continue;
    }

    await page.goto(probeUrl, {
      waitUntil: 'domcontentloaded',
      timeout: 60000,
    }).catch(() => {});
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const ok = await page.evaluate(() => {
      const text = document.body.innerText || '';
      return text.includes('내정보 보기') || text.includes('로그아웃') || !text.includes('로그인');
    }).catch(() => false);
    if (ok) return;
  }

  throw new Error('네이버 로그인 대기 시간이 초과되었습니다.');
}

async function resolveBlogFrame(page, logNo) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const frames = page.frames();
    const preferred = frames.find((candidate) => {
      const url = candidate.url() || '';
      return candidate !== page.mainFrame()
        && (
          candidate.name() === 'mainFrame'
          || url.includes(`logNo=${logNo}`)
          || url.endsWith(`/${logNo}`)
        );
    });

    if (preferred) {
      await preferred.waitForLoadState('domcontentloaded').catch(() => {});
      return preferred;
    }

    const direct = frames.find((candidate) => {
      const url = candidate.url() || '';
      return url.includes(`logNo=${logNo}`) || url.endsWith(`/${logNo}`);
    });
    if (direct) {
      await direct.waitForLoadState('domcontentloaded').catch(() => {});
      return direct;
    }
    await sleep(500);
  }

  return page.mainFrame();
}

async function findFrameWithSelector(page, selectors) {
  const frames = page.frames();
  for (const frame of frames) {
    for (const selector of selectors) {
      const count = await frame.locator(selector).count().catch(() => 0);
      if (count) return { frame, selector };
    }
  }
  return null;
}

async function clickLike(page) {
  const selectors = [
    'a.u_likeit_button._face',
    'a.u_likeit_list_button._button',
  ];

  const found = await findFrameWithSelector(page, selectors);
  if (!found) return 'like-button-missing';

  const { frame, selector } = found;
  const locator = frame.locator(selector).first();
  const pressed = await locator.getAttribute('aria-pressed').catch(() => null);
  const className = await locator.getAttribute('class').catch(() => '');
  if (pressed === 'true' || /\bon\b/.test(className || '')) return 'already-liked';

  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: 10000 }).catch(async () => {
    await frame.evaluate((sel) => {
      const el = document.querySelector(sel);
      if (!el) return false;
      ['mouseover', 'mousedown', 'mouseup', 'click'].forEach((type) => {
        el.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, view: window }));
      });
      return true;
    }, selector);
  });
  await sleep(1500);
  return 'liked';
}

async function openCommentWidget(frame, logNo) {
  await frame.evaluate((targetLogNo) => {
    if (window.naverCommentController && typeof window.naverCommentController._autoOpenComment === 'function') {
      window.naverCommentController._autoOpenComment({
        blogNo: targetLogNo,
        ticketNo: '201',
        targetNo: targetLogNo,
        aFormation: ['list', 'page', 'write'],
        isPostComment: true,
        nPageSize: 50,
        sPageType: 'default',
      });
      return;
    }

    const button = [...document.querySelectorAll('a, button')].find((el) => /댓글/.test(el.textContent || ''));
    if (button) button.click();
  }, logNo);

  const editor = frame.locator(`#naverComment_201_${logNo}__write_textarea`);
  await editor.waitFor({ state: 'visible', timeout: 20000 });
  return editor;
}

function parseUploadXml(xmlText) {
  const cleaned = xmlText.replace('<![CDATA[', '').replace(']]>', '');
  const matches = cleaned.match(/<([^>]+)>([^<]*)<\/[^>]+>/g) || [];
  const parsed = {};
  for (const token of matches) {
    const match = token.match(/<([^>]+)>([^<]*)<\/[^>]+>/);
    if (!match) continue;
    parsed[match[1]] = match[2];
  }
  return parsed;
}

async function getCommentApiConfig(frame, logNo) {
  const fallbackId = `naverComment_201_${logNo}`;
  return frame.evaluate((commentId) => {
    const config = window.cbox?.Core?.components?.[commentId]?.config;
    const ticketConfig = config?.getTicketConfig?.() || {};
    return {
      sId: config?.option('sId') || commentId,
      sObjectId: config?.option('sObjectId') || '',
      sGroupId: config?.option('sGroupId') || '',
      sObjectUrl: config?.option('sObjectUrl') || window.location.href,
      sLanguage: config?.option('sLanguage') || 'ko',
      sCountry: config?.option('sCountry') || '',
      nPageSize: config?.option('nPageSize') || 50,
      nIndexSize: config?.option('nIndexSize') || 10,
      sListType: config?.option('sListType') || 'OBJECT',
      sPageType: config?.option('sPageType') || 'default',
      sLikeItId: config?.option('sLikeItId') || '',
      version: (window.cbox?.Core?.version || '/20260422104643').replace(/^\//, ''),
      photoInfraUploadDomain: ticketConfig.photoInfraUploadDomain || 'g-public2.upphoto.naver.com',
      imageAutoRotate: ticketConfig.imageAutoRotate !== false,
    };
  }, fallbackId);
}

async function submitCommentViaApi(frame, request, logNo, comment) {
  const api = await getCommentApiConfig(frame, logNo);
  if (!api.sObjectId || !api.sGroupId) {
    throw new Error(`댓글 API 설정 조회 실패: ${logNo}`);
  }

  const commonParams = {
    lang: api.sLanguage,
    country: api.sCountry,
    objectId: api.sObjectId,
    categoryId: '',
    pageSize: String(api.nPageSize),
    indexSize: String(api.nIndexSize),
    groupId: api.sGroupId,
    listType: api.sListType,
    pageType: api.sPageType,
    clientType: 'web-pc',
    objectUrl: api.sObjectUrl,
    userType: '',
  };

  const sessionUrl = `https://apis.naver.com/commentBox/cbox/web_naver_session_key_upload_image_json.json?ticket=blog&templateId=default&pool=blogid&_cv=${api.version}`;
  const sessionResponse = await request.post(sessionUrl, {
    form: {
      ...commonParams,
      target: api.sId,
    },
  });
  const sessionJson = await sessionResponse.json();
  if (!sessionJson.success) {
    throw new Error(`이미지 세션 요청 실패: ${sessionJson.code} ${sessionJson.message}`);
  }

  const uploadUrl = `https://${api.photoInfraUploadDomain}/${sessionJson.result.sessionKey}/simpleUpload/0?userId=${encodeURIComponent(sessionJson.result.userId)}&extractExif=false&autorotate=${api.imageAutoRotate}`;
  const uploadResponse = await request.post(uploadUrl, {
    multipart: {
      image: {
        name: path.basename(IMAGE_PATH),
        mimeType: 'image/png',
        buffer: fs.readFileSync(IMAGE_PATH),
      },
    },
  });
  const uploadText = await uploadResponse.text();
  const upload = parseUploadXml(uploadText);
  if (!upload.path) {
    throw new Error(`이미지 업로드 실패: ${uploadText.slice(0, 300)}`);
  }

  const createUrl = `https://apis.naver.com/commentBox/cbox/web_naver_create_json.json?ticket=blog&templateId=default&pool=blogid&_cv=${api.version}`;
  const createResponse = await request.post(createUrl, {
    form: {
      ...commonParams,
      contents: comment,
      pick: 'false',
      manager: 'false',
      likeItId: api.sLikeItId,
      secret: 'false',
      refresh: 'true',
      sort: 'NEW',
      commentType: 'img+txt',
      imagePathList: upload.path,
      imageWidthList: upload.width || '',
      imageHeightList: upload.height || '',
      imageCount: '1',
      'attachmentList[0].type': 'image',
      'attachmentList[0].imagePath': upload.path,
      'attachmentList[0].width': upload.width || '',
      'attachmentList[0].height': upload.height || '',
    },
  });
  const createJson = await createResponse.json();
  if (!createJson.success) {
    const error = new Error(`댓글 등록 실패: ${createJson.code} ${createJson.message}`);
    error.commentCode = String(createJson.code || '');
    error.commentMessage = createJson.message || '';
    error.commentDisabled = String(createJson.code || '') === '3300';
    throw error;
  }

  const verifyUrl = `https://apis.naver.com/commentBox/cbox/web_naver_list_jsonp.json?ticket=blog&templateId=default&pool=blogid&_cv=${api.version}&_callback=cb&lang=${encodeURIComponent(api.sLanguage)}&country=${encodeURIComponent(api.sCountry)}&objectId=${encodeURIComponent(api.sObjectId)}&categoryId=&pageSize=${api.nPageSize}&indexSize=${api.nIndexSize}&groupId=${encodeURIComponent(api.sGroupId)}&listType=${encodeURIComponent(api.sListType)}&pageType=${encodeURIComponent(api.sPageType)}&page=1&initialize=true&followSize=5&userType=&useAltSort=true&showReply=true&_=${Date.now()}`;
  const verifyResponse = await request.get(verifyUrl);
  const verifyText = await verifyResponse.text();
  const match = verifyText.match(/^\/\*\*\/cb\((.*)\)$/s);
  const verifyJson = match ? JSON.parse(match[1]) : null;
  const commentList = verifyJson?.result?.commentList || [];
  const firstLine = comment.split('\n')[0];
  const verified = commentList.some((item) => (item.contents || '').includes(firstLine));
  if (!verified) {
    throw new Error('댓글 등록 후 검증 실패');
  }
}

async function attachImage(frame) {
  const input = frame.locator('input.u-cbox-browse-file-input._uploadSelectToggleArea').first();
  await input.setInputFiles(IMAGE_PATH);
  await frame.locator('text=업로드 취소').first().waitFor({ state: 'visible', timeout: 30000 });
}

async function fillCommentEditor(frame, logNo, comment) {
  const selector = `#naverComment_201_${logNo}__write_textarea`;
  const editor = frame.locator(selector);
  await editor.click({ force: true }).catch(async () => {
    await frame.evaluate((editorSelector) => {
      const el = document.querySelector(editorSelector);
      el?.focus();
    }, selector);
  });

  await editor.fill(comment).catch(async () => {
    await frame.page().keyboard.press('Control+A');
    await frame.page().keyboard.type(comment, { delay: 1 });
  });

  await frame.waitForFunction(({ editorSelector, expectedLength }) => {
    const el = document.querySelector(editorSelector);
    return !!el && (el.textContent || '').trim().length >= expectedLength;
  }, {
    editorSelector: selector,
    expectedLength: comment.split('\n').join('').length,
  });
}

async function hasExistingPromotionComment(frame, comment) {
  const firstLine = comment.split('\n')[0].trim();
  return frame.evaluate((needle) => {
    const root = document.querySelector('.u_cbox') || document;
    const text = root.innerText || '';
    return text.includes('babysitter.dveamer.com') || (needle && text.includes(needle));
  }, firstLine);
}

async function submitComment(frame, logNo, comment) {
  await fillCommentEditor(frame, logNo, comment);
  await attachImage(frame);
  await frame.page().waitForTimeout(1500);

  const submit = frame.locator('.u_cbox_btn_upload').first();
  const createResponsePromise = frame.page().waitForResponse(
    (response) => response.url().includes('web_naver_create_json.json'),
    { timeout: 30000 },
  );

  await submit.click({ timeout: 15000, force: true }).catch(async () => {
    await submit.evaluate((el) => el.click());
  });
  const createResponse = await createResponsePromise;
  const createJson = await createResponse.json();
  if (!createJson.success) {
    const error = new Error(`댓글 등록 실패: ${createJson.code} ${createJson.message}`);
    error.commentCode = String(createJson.code || '');
    error.commentMessage = createJson.message || '';
    error.commentDisabled = String(createJson.code || '') === '3300';
    throw error;
  }
}

async function runTask(page, task) {
  const logNo = parseLogNo(task.url);
  if (!logNo) throw new Error(`logNo 파싱 실패: ${task.url}`);

  await page.goto(task.url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {});
  const frame = await resolveBlogFrame(page, logNo);

  await openCommentWidget(frame, logNo);
  if (await hasExistingPromotionComment(frame, task.comment)) {
    return {
      ...task,
      likeStatus: 'not-retried',
      commentStatus: 'duplicate',
      timestamp: nowKstStamp(),
    };
  }

  const likeStatus = await clickLike(page);
  try {
    await submitComment(frame, logNo, task.comment);
  } catch (error) {
    if (error.commentDisabled) {
      return {
        ...task,
        likeStatus,
        commentStatus: 'comments-disabled',
        skipReason: error.commentMessage || error.message,
        timestamp: nowKstStamp(),
      };
    }
    throw error;
  }

  return {
    ...task,
    likeStatus,
    commentStatus: 'posted',
    timestamp: nowKstStamp(),
  };
}

function classifyTaskError(error) {
  const message = error?.message || String(error);
  const commentCode = String(error?.commentCode || '');
  const skipByCode = new Set(['3300', '3999']);

  if (error?.commentDisabled || skipByCode.has(commentCode)) {
    return {
      commentStatus: 'comments-disabled',
      skipReason: error?.commentMessage || message,
    };
  }

  if (/잘못된 접근입니다/.test(message)) {
    return {
      commentStatus: 'comments-disabled',
      skipReason: message,
    };
  }

  return {
    commentStatus: 'error',
    skipReason: message,
  };
}

async function openBrowserContext() {
  try {
    const browser = await chromium.connectOverCDP(CDP_ENDPOINT);
    return {
      browser,
      context: browser.contexts()[0],
      close: async () => {
        await browser.close();
      },
    };
  } catch {
    fs.mkdirSync(PROFILE_DIR, { recursive: true });
    const launchOptions = {
      headless: false,
      viewport: { width: 1440, height: 1200 },
      locale: 'ko-KR',
    };

    let context;
    try {
      context = await chromium.launchPersistentContext(PROFILE_DIR, {
        ...launchOptions,
        executablePath: CHROME_PATH,
      });
    } catch {
      try {
        context = await chromium.launchPersistentContext(PROFILE_DIR, launchOptions);
      } catch {
        context = await firefox.launchPersistentContext(PROFILE_DIR, launchOptions);
      }
    }
    return {
      browser: null,
      context,
      close: async () => {
        await context.close();
      },
    };
  }
}

async function main() {
  const tasksMarkdown = fs.readFileSync(TASKS_PATH, 'utf8');
  const resultsMarkdown = fs.readFileSync(RESULTS_PATH, 'utf8');
  const existingKeys = resultBlogKeys(resultsMarkdown);
  const tasks = parseTasks(tasksMarkdown);
  if (!tasks.length) throw new Error('처리할 task 가 없습니다.');

  const runtime = await openBrowserContext();
  const page = runtime.context.pages()[0] || await runtime.context.newPage();

  try {
    await ensureLoggedIn(page, tasks[0].url);

    const completed = [];
    const duplicates = [];
    const skipped = [];
    let scanned = 0;
    for (const task of tasks) {
      if (completed.length >= TASK_LIMIT) break;
      if (scanned >= TASK_SCAN_LIMIT) break;
      scanned += 1;
      const key = parseBlogKey(task.originalUrl);
      if (key && existingKeys.has(key)) {
        const duplicate = {
          ...task,
          likeStatus: 'not-retried',
          commentStatus: 'duplicate',
          timestamp: nowKstStamp(),
        };
        duplicates.push(duplicate);
        console.log(`DUPLICATE_RESULT ${task.originalUrl}`);
        continue;
      }

      console.log(`START ${task.originalUrl}`);
      try {
        const result = await runTask(page, task);
        if (result.commentStatus === 'duplicate') {
          duplicates.push(result);
          console.log(`DUPLICATE_LIVE ${task.originalUrl}`);
          continue;
        }
        if (result.commentStatus === 'comments-disabled') {
          skipped.push(result);
          console.log(`SKIP ${task.originalUrl} ${result.skipReason}`);
          continue;
        }
        completed.push(result);
        console.log(`DONE ${task.originalUrl} ${result.likeStatus}`);
      } catch (error) {
        const classified = classifyTaskError(error);
        skipped.push({
          ...task,
          likeStatus: 'unknown',
          timestamp: nowKstStamp(),
          ...classified,
        });
        console.log(`SKIP ${task.originalUrl} ${classified.skipReason}`);
      }
    }

    if (completed.length || duplicates.length) {
      const processed = completed.concat(duplicates);
      const updatedTasks = removeCompletedTasks(tasksMarkdown, processed.map((result) => result.fullBlock));
      const updatedResults = appendResults(resultsMarkdown, processed);

      fs.writeFileSync(TASKS_PATH, updatedTasks);
      fs.writeFileSync(RESULTS_PATH, updatedResults);
    }

    console.log(JSON.stringify({ ok: true, completed, duplicates, skipped }, null, 2));
  } finally {
    await runtime.close();
  }
}

main().catch((error) => {
  console.error(JSON.stringify({ ok: false, error: error.message, stack: error.stack }, null, 2));
  process.exit(1);
});
