// functions/index.js (ESM, Node 20)
// Firebase Functions v2 + OpenAI(선택) + 맞춤형 배치 추천 API

import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import OpenAI from "openai";

/* ================== 초기화 ================== */
initializeApp();
const db = getFirestore();

const REGION = "asia-northeast3";
const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY"); // v2 방식 시크릿

/* ================== 유틸 ================== */
const toInt = (v) => {
  const n =
    typeof v === "number" ? v :
    typeof v === "string" ? Number(v) :
    NaN;
  return Number.isFinite(n) ? Math.trunc(n) : undefined; // ❗ NaN 방지
};
const toNum = (v) => {
  const n =
    typeof v === "number" ? v :
    typeof v === "string" ? Number(v) :
    NaN;
  return Number.isFinite(n) ? n : undefined; // ❗ NaN 방지
};
const toList = (v) =>
  Array.isArray(v) ? v.map((x) => String(x)) : v ? [String(v)] : [];

function bmiOf(h, w) {
  if (!h || !w || h <= 0) return undefined;
  const m = h / 100;
  const b = w / (m * m);
  return Number.isFinite(b) ? b : undefined; // ❗ NaN 방지
}
function catBmi(b) {
  if (!Number.isFinite(b)) return "N";
  if (b < 18.5) return "L";
  if (b < 25) return "N";
  return "H";
}
function calorieWindow(cat) {
  return cat === "H" ? [200, 500] : cat === "L" ? [400, 900] : [300, 700];
}
function profileTags(cat, diseases) {
  const tags = new Set();
  if (cat === "H") tags.add("calorie_deficit").add("balanced");
  else if (cat === "L") tags.add("high_protein").add("balanced");
  else tags.add("balanced");

  const d = (diseases || []).join(" ").toLowerCase();
  if (d.includes("당뇨") || d.includes("diabetes")) tags.add("low_sugar");
  if (d.includes("고혈압") || d.includes("hypertension")) tags.add("low_sodium");
  return Array.from(tags);
}
function scoreByRules(m, wantedTags, kcalWin, allergies) {
  const flags = new Set((m.allergyFlags || []).map((x) => x.toLowerCase()));
  for (const a of allergies) if (flags.has(a)) return -1e9; // 알레르기 즉시 탈락

  const tagHit = (m.tags || []).filter((t) => wantedTags.includes(t)).length;

  let kcalScore = 0.5;
  if (kcalWin) {
    const k = Number.isFinite(m.kcal) ? m.kcal : undefined; // ❗ NaN 차단
    const mid = (kcalWin[0] + kcalWin[1]) / 2;
    kcalScore = k === undefined ? 0.5 : 1 - Math.min(1, Math.abs(k - mid) / (mid || 1));
  }
  return tagHit * 2 + kcalScore;
}
async function fetchByTags(tags, limit = 80) {
  if (tags.length) {
    const qs = await db
      .collection("menu")
      .where("tags", "array-contains-any", tags)
      .limit(limit)
      .get();
    return qs.docs.map((d) => ({ id: d.id, ...d.data() }));
  } else {
    const qs = await db.collection("menu").limit(limit).get();
    return qs.docs.map((d) => ({ id: d.id, ...d.data() }));
  }
}

function mapMenu(x) {
  if (!x) return null;
  const id = String(x.id);
  const name = String(x.name || "");
  if (!id || !name) return null;
  const kcal = toInt(x.kcal); // ❗ 안전 변환 (NaN 방지)
  return {
    id,
    name,
    kcal, // undefined면 JSON에서 자동 생략
    imageUrl: x.imageUrl || x.image || "",
    ingredients: toList(x.ingredients),
    steps: toList(x.steps),
    tags: toList(x.tags),
    allergyFlags: toList(x.allergy_flags),
  };
}
function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}
function dedupMerge(a, b) {
  const seen = new Set(a.map((x) => x.id));
  for (const m of b) if (!seen.has(m.id)) a.push(m);
  return a;
}

async function rerankWithLLM(openai, profile, menus) {
  if (!openai) return { reason: "", order: menus.map((m) => m.id) };

  const resp = await openai.chat.completions.create({
    model: "gpt-4o-mini",
    temperature: 0.2,
    response_format: { type: "json_object" },
    messages: [
      { role: "system", content: "You are a nutrition coach. Return JSON strictly." },
      {
        role: "user",
        content: `User profile:
${JSON.stringify(profile, null, 2)}

Menus (<=24):
${JSON.stringify(
  menus.map((m) => ({
    id: m.id,
    name: m.name,
    kcal: m.kcal,
    tags: m.tags,
    allergyFlags: m.allergyFlags,
  })),
  null,
  2
)}

Task:
1) Rank menu IDs best→worst considering BMI, diseases, allergies, calories, tags.
2) Write a short Korean analysis (<=120 chars).
Return JSON: { "order": [ids...], "reason": "..." }`,
      },
    ],
  });

  try {
    const parsed = JSON.parse(resp.choices[0].message.content || "{}");
    return {
      reason: String(parsed.reason || ""),
      order: Array.isArray(parsed.order) ? parsed.order.map(String) : menus.map((m) => m.id),
    };
  } catch {
    return { reason: "", order: menus.map((m) => m.id) };
  }
}

/* ================== v2 onCall 트리거 ================== */
export const recommendTodayMenuBatch = onCall(
  {
    region: REGION,
    timeoutSeconds: 10,
    memory: "512MiB",
    secrets: [OPENAI_API_KEY], // v2 시크릿 주입
  },
  async (req) => {
    const data = req.data || {};
    const email = (data.email || "").toString().trim();
    const exclude = Array.isArray(data.exclude) ? data.exclude.map((x) => String(x)) : [];
    const batchSize = Math.max(1, Math.min(10, Number(data.batchSize) || 4));

    // ✅ 시크릿 안전 접근 (로그에서 value() 직접 호출 금지)
    let apiKey;
    let hasLLM = false;
    try {
      apiKey = OPENAI_API_KEY.value();
      hasLLM = !!apiKey;
    } catch (_) {
      hasLLM = false;
    }

    // 상태 로그
    console.log("[AI] recommendTodayMenuBatch", {
      llm: hasLLM,
      email,
      excludeCount: exclude.length,
      batchSize,
      region: REGION,
      ts: new Date().toISOString(),
      authed: !!req.auth,
    });

    if (!email) throw new HttpsError("invalid-argument", "email is required");

    // 1) 프로필
    const userSnap = await db.collection("usercode").doc(email).get();
    if (!userSnap.exists) throw new HttpsError("not-found", "user profile not found");

    const up = {
      email,
      height_cm: toInt(userSnap.get("height_cm")),
      weight_kg: toNum(userSnap.get("weight_kg")),
      fat_percent: toNum(userSnap.get("fat_percent")),
      age_years: toInt(userSnap.get("age_years")),
      age_man_years: toInt(userSnap.get("age_man_years")),
      allergies: toList(userSnap.get("allergies")),
      diseases: toList(userSnap.get("diseases")),
    };

    const bmi = bmiOf(up.height_cm, up.weight_kg);
    const cat = catBmi(bmi);
    const kcalWin = calorieWindow(cat);
    const wanted = profileTags(cat, up.diseases || []);
    const allergySet = new Set((up.allergies || []).map((a) => a.toLowerCase()));
    const excludeSet = new Set(exclude.map((x) => x.toLowerCase()));

    // 2) 태그 기반 후보 수집
    const raw = await fetchByTags(wanted, 100);
    let cand = raw.map(mapMenu).filter(Boolean);

    // 3) 규칙 스코어링
    let scored = cand
      .map((m) => ({ m, s: scoreByRules(m, wanted, [kcalWin[0], kcalWin[1]], allergySet) }))
      .filter((x) => x.s > -1e9)
      .sort((a, b) => b.s - a.s)
      .map((x) => x.m);

    // 4) 후보 보강(최소 8개 보장)
    const ensureAtLeast = async (min = 8) => {
      if (scored.length >= min) return;

      // 4-1 칼로리 완화
      const noKcal = cand
        .map((m) => ({ m, s: scoreByRules(m, wanted, null, allergySet) }))
        .filter((x) => x.s > -1e9)
        .sort((a, b) => b.s - a.s)
        .map((x) => x.m);
      scored = dedupMerge(scored, noKcal);
      if (scored.length >= min) return;

      // 4-2 태그 완화(알레르기만 배제)
      const anyRaw = await fetchByTags([], 120);
      const any = anyRaw.map(mapMenu).filter(Boolean);
      const anyNoAllergy = any.filter((m) => {
        const flags = new Set((m.allergyFlags || []).map((z) => z.toLowerCase()));
        for (const a of allergySet) if (flags.has(a)) return false;
        return true;
      });
      scored = dedupMerge(scored, anyNoAllergy);
    };
    await ensureAtLeast(8);

    // 5) (선택) LLM 재랭킹
    const openai = hasLLM ? new OpenAI({ apiKey }) : null;

    let ordered = scored.slice(0, 24);
    const bmiTxt = Number.isFinite(bmi) ? bmi.toFixed(1) : "-"; // ❗ NaN 방지
    let reason = `BMI ${bmiTxt}(${cat}), ${kcalWin[0]}~${kcalWin[1]}kcal, 태그:${wanted.join(",") || "-"}`;
    try {
      const { order, reason: r } = await rerankWithLLM(
        openai,
        { bmi, cat, wanted, kcalWin, allergies: up.allergies, diseases: up.diseases },
        ordered
      );
      if (order && order.length) {
        const map = new Map(ordered.map((m) => [m.id, m]));
        ordered = order.map((id) => map.get(id)).filter(Boolean);
      }
      if (r) reason = r;
    } catch {
      // LLM 실패는 폴백
    }

    // 6) exclude 적용 → 무작위 배치
    const filtered = ordered.filter((m) => !excludeSet.has(m.id.toLowerCase()));
    const picked = shuffle([...filtered]).slice(0, Math.max(1, Math.min(10, batchSize)));

    // ✅ 절대 NaN / Infinity / undefined가 응답 객체에 들어가지 않게 보장됨
    return {
      analysisMessage: reason,
      items: picked,
      meta: {
        totalAfterFilter: filtered.length,
        excludedCount: exclude.length,
        batchSize,
      },
    };
  }
);
