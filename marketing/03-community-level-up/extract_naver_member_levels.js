const fs = require("fs");

const mdPath = "naver-cafe-tasks.md";
const md = fs.readFileSync(mdPath, "utf8");
const targets = md
  .split(/\n/)
  .filter((line) => /^\| \d+ \|/.test(line) && line.includes("원문 확인 필요"))
  .map((line) => {
    const cells = line.split("|").slice(1, -1).map((s) => s.trim());
    return {
      no: Number(cells[0]),
      name: cells[1],
      cafeId: cells[3].replace(/`/g, ""),
      url: cells[5],
    };
  });

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    const pending = new Map();
    let id = 0;

    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id && pending.has(msg.id)) {
        pending.get(msg.id)(msg);
        pending.delete(msg.id);
      }
    };
    ws.onerror = (err) => reject(err);
    ws.onopen = () => {
      resolve({
        ws,
        send(method, params = {}) {
          return new Promise((done) => {
            const msgId = ++id;
            pending.set(msgId, done);
            ws.send(JSON.stringify({ id: msgId, method, params }));
          });
        },
      });
    };
  });
}

async function bodyText(send) {
  const result = await send("Runtime.evaluate", {
    expression: "document.body ? document.body.innerText : ''",
    returnByValue: true,
  });
  return result.result?.result?.value || "";
}

async function structuredLevels(send) {
  const result = await send("Runtime.evaluate", {
    expression: `Array.from(document.querySelectorAll('table.tbl_role tbody tr')).map((tr) => ({
      level: (tr.querySelector('th')?.innerText || '').replace(/\\s+/g, ' ').trim(),
      description: (tr.querySelector('td p')?.innerText || '').replace(/\\s+/g, ' ').trim(),
      condition: Array.from(tr.querySelectorAll('td li')).map((li) => li.innerText.replace(/\\s+/g, ' ').trim()).join(' '),
    }))`,
    returnByValue: true,
  });
  return result.result?.result?.value || [];
}

async function waitForMemberLevel(send) {
  for (let i = 0; i < 40; i += 1) {
    const text = await bodyText(send);
    if (
      text.includes("우리카페 등급안내") ||
      text.includes("카페의 등급 목록") ||
      text.includes("카페를 찾을 수 없습니다") ||
      text.includes("페이지를 찾을 수 없습니다")
    ) {
      return text;
    }
    await wait(500);
  }
  return bodyText(send);
}

function compactCondition(text) {
  const marker = "카페의 등급 목록";
  const after = text.includes(marker) ? text.slice(text.indexOf(marker) + marker.length) : text;
  const lines = after
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !line.startsWith("자동등업 :"))
    .filter((line) => !line.startsWith("등업게시판 :"))
    .filter((line) => line !== "확인");

  const grades = [];
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (/^가입 \d+주 후 ·/.test(line)) {
      if (grades.length > 0) {
        grades[grades.length - 1].condition = line;
      }
      continue;
    }
    if (/^\d+$/.test(line)) continue;
    const prev = lines[i - 1] || "";
    const next = lines[i + 1] || "";
    const looksLikeGrade =
      !/^가입 \d+주 후 ·/.test(line) &&
      !line.includes("개 ·") &&
      (next && !/^가입 \d+주 후 ·/.test(next) || /^가입 \d+주 후 ·/.test(lines[i + 2] || ""));
    if (looksLikeGrade && !prev.includes("undefined")) {
      grades.push({ level: line, description: "", condition: "" });
      if (next && !/^가입 \d+주 후 ·/.test(next)) {
        grades[grades.length - 1].description = next;
      }
    }
  }

  return {
    grades,
    second: grades[1] || null,
    textHead: text.slice(0, 3000),
  };
}

async function main() {
  const pages = await (await fetch("http://127.0.0.1:9222/json/list")).json();
  const page = pages.find((p) => p.type === "page") || pages[0];
  if (!page) throw new Error("No Chrome page found on port 9222");
  const { ws, send } = await connect(page.webSocketDebuggerUrl);
  await send("Page.enable");
  await send("Runtime.enable");

  const results = [];
  for (const target of targets) {
    await send("Page.navigate", { url: target.url });
    const text = await waitForMemberLevel(send);
    const levels = await structuredLevels(send);
    const parsed = compactCondition(text);
    const second = levels[1] || null;
    results.push({ ...target, levels, second, textHead: parsed.textHead });
    process.stderr.write(`${target.no} ${target.name}: ${second?.level || "NO_SECOND"}\n`);
    await wait(700);
  }

  fs.writeFileSync("naver_member_levels.json", JSON.stringify(results, null, 2));
  ws.close();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
