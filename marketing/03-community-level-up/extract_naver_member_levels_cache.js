const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const cacheRoots = [
  "/private/tmp/codex-naver-openchrome",
  "/private/tmp/naver-chrome-profile",
  "/Users/dveamer/.cache/chrome-devtools-mcp/chrome-profile",
];

function walk(dir, files = []) {
  if (!fs.existsSync(dir)) return files;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
    } else if (fullPath.includes(`${path.sep}Cache_Data${path.sep}`)) {
      files.push(fullPath);
    }
  }
  return files;
}

function normalizeCondition(level) {
  const base =
    `가입 ${level.joindtcondition || 0}주 후 · ` +
    `게시글 ${level.articlecount || 0}개 · ` +
    `댓글 ${level.commentcount || 0}개 · ` +
    `좋아요 ${level.likecount || 0}개 · ` +
    `방문 수 ${level.visitcount || 0}회`;

  if (level.leveluptype === 2) return `${base}를 만족하면 자동으로 등업 합니다.`;
  if (level.leveluptype === 3) return `${base}를 만족하면 등업 게시판에 등업 신청 이 가능합니다.`;
  return "";
}

function extractResponse(filePath) {
  const buffer = fs.readFileSync(filePath);
  const marker = Buffer.from("CafeMemberLevelInfo?cafeId=");
  const markerIndex = buffer.indexOf(marker);
  if (markerIndex < 0) return null;

  const key = buffer.subarray(markerIndex, markerIndex + 200).toString("latin1");
  const cafeId = key.match(/cafeId=(\d+)/)?.[1];
  if (!cafeId) return null;

  const gzipIndex = buffer.indexOf(Buffer.from([0x1f, 0x8b, 0x08]), markerIndex);
  const httpIndex = buffer.indexOf(Buffer.from("HTTP/1.1"), gzipIndex);
  if (gzipIndex < 0 || httpIndex < 0) return null;

  const json = zlib.inflateRawSync(buffer.subarray(gzipIndex + 10, httpIndex)).toString("utf8");
  const payload = JSON.parse(json);
  const memberLevelList = payload.message?.result?.memberLevelList || [];
  const levels = memberLevelList.map((level) => ({
    level: level.memberlevelname || "",
    description: level.memberleveldesc || "",
    condition: normalizeCondition(level),
    raw: level,
  }));

  return {
    cafeId,
    url: `https://cafe.naver.com/ca-fe/cafes/${cafeId}/member-level`,
    levels,
    second: levels[1] || null,
    sourceFile: filePath,
  };
}

const byCafeId = new Map();
for (const root of cacheRoots) {
  for (const filePath of walk(root)) {
    try {
      const response = extractResponse(filePath);
      if (response) byCafeId.set(response.cafeId, response);
    } catch {
      // Ignore unrelated cache entries and partial cache writes.
    }
  }
}

const results = [...byCafeId.values()].sort((a, b) => Number(a.cafeId) - Number(b.cafeId));
fs.writeFileSync("naver_member_levels_cache.json", JSON.stringify(results, null, 2));
for (const result of results) {
  process.stderr.write(`${result.cafeId}: ${result.second?.level || "NO_SECOND"}\n`);
}
