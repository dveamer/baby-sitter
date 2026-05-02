const fs = require("fs");

const mdPath = "naver-cafe-tasks.md";
const main = JSON.parse(fs.readFileSync("naver_member_levels.json", "utf8"));
const extra = JSON.parse(fs.readFileSync("naver_member_levels_extra.json", "utf8"));
const results = [...main, ...extra];
let md = fs.readFileSync(mdPath, "utf8");

function finishSentence(text) {
  if (!text) return "";
  return /[.!?。！？]$/.test(text) ? `${text} ` : `${text}. `;
}

function summarize(second) {
  if (!second) return "원문 확인 결과 두 번째 등급 행을 찾지 못함.";

  const desc = second.description ? `원문 설명: ${finishSentence(second.description)}` : "";
  if (!second.condition) {
    return `\`${second.level}\`: ${desc}원문상 별도 자동/신청 수치 조건은 없음.`;
  }

  const condition = second.condition
    .replace(/\s+/g, " ")
    .replace("를 만족하면 자동으로 등업 합니다.", " 충족 시 자동 등업.")
    .replace("를 만족하면 등업 게시판에 등업 신청 이 가능합니다.", " 충족 시 등업게시판 신청 가능.")
    .trim();
  return `\`${second.level}\`: ${desc}${condition}`;
}

const byNo = new Map(results.map((result) => [result.no, result]));
const lines = md.split("\n").map((line) => {
  if (!/^\| \d+ \|/.test(line)) return line;
  const cells = line.split("|").slice(1, -1).map((s) => s.trim());
  const no = Number(cells[0]);
  const result = byNo.get(no);
  if (!result) return line;
  cells[6] = summarize(result.second);
  cells[7] = `확인됨. 네이버 멤버등급 안내 원문(openChrome) 출처: ${cells[5]}`;
  return `| ${cells.join(" | ")} |`;
});

md = lines.join("\n");
md = md.replace(
  /- 2026-04-30: 총 40개 수집, 40개는 두 번째 등급 기준 확인\.\n(?:  - .+\n){2}/,
  "- 2026-04-30: 총 40개 수집, 40개는 두 번째 등급 기준 원문 확인.\n" +
    "  - 이번 실행(openChrome 원문 재확인): 기존 `원문 확인 필요` 35개와 기존 확인 5개를 모두 네이버 `member-level` 원문 DOM 테이블에서 직접 확인해 두 번째 등급 기준으로 갱신.\n" +
    "  - 이전 수집: `sanmoschool`, `15668981`, `1msanbu`, `appletreecafe`, `momsblog79`, `bebelink`, `cryingbebe`, `remonterrace`, `mpkuru`, `28140611`, `23267139`, `21410422`, `13365688`, `17523807`, `12448054`, `24000254`, `29516772`, `baby8`, `byungs94`, `miznett`, `22626702`, `dongtanmom`, `10250319`, `18077961`, `20289827`, `11306253`, `22126441`, `29968910`, `18571788`, `29377898`, `29533738`.\n"
);

fs.writeFileSync(mdPath, md);
