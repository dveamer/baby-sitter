const fs = require("fs");

const md = fs.readFileSync("naver-cafe-tasks.md", "utf8");
const targetNos = new Set([1, 2, 3, 27, 31]);
const targets = md
  .split(/\n/)
  .filter((line) => /^\| \d+ \|/.test(line))
  .map((line) => {
    const cells = line.split("|").slice(1, -1).map((s) => s.trim());
    return { no: Number(cells[0]), name: cells[1], cafeId: cells[3].replace(/`/g, ""), url: cells[5] };
  })
  .filter((target) => targetNos.has(target.no));

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
    ws.onerror = reject;
    ws.onopen = () => resolve({
      ws,
      send(method, params = {}) {
        return new Promise((done) => {
          const msgId = ++id;
          pending.set(msgId, done);
          ws.send(JSON.stringify({ id: msgId, method, params }));
        });
      },
    });
  });
}

async function bodyText(send) {
  const result = await send("Runtime.evaluate", {
    expression: "document.body ? document.body.innerText : ''",
    returnByValue: true,
  });
  return result.result?.result?.value || "";
}

async function waitForMemberLevel(send) {
  for (let i = 0; i < 40; i += 1) {
    const text = await bodyText(send);
    if (text.includes("우리카페 등급안내") || text.includes("카페의 등급 목록")) return text;
    await wait(500);
  }
  return bodyText(send);
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

async function main() {
  const pages = await (await fetch("http://127.0.0.1:9222/json/list")).json();
  const page = pages.find((p) => p.type === "page") || pages[0];
  const { ws, send } = await connect(page.webSocketDebuggerUrl);
  await send("Page.enable");
  await send("Runtime.enable");
  const results = [];
  for (const target of targets) {
    await send("Page.navigate", { url: target.url });
    await waitForMemberLevel(send);
    const levels = await structuredLevels(send);
    const second = levels[1] || null;
    results.push({ ...target, levels, second });
    process.stderr.write(`${target.no} ${target.name}: ${second?.level || "NO_SECOND"}\n`);
    await wait(700);
  }
  fs.writeFileSync("naver_member_levels_extra.json", JSON.stringify(results, null, 2));
  ws.close();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
