// 동시 사용자 부하 테스트 (조회 전용, 인증 불필요). Node 18+ 내장 fetch 만 사용한다.
// 사용: node load/load_test.mjs --base http://localhost:8080 --scenario list12 --levels 1,10,50 --seconds 15
// 시나리오: list12 (목록 size=12) | list100 (size=100) | detail (같은 공고 상세, 조회수 UPDATE 집중) | mixed (목록 80% + 상세 20%)
// 주의: detail/mixed 는 조회수(view_count)를 실제로 올린다. 측정용 서버에서만 쓴다.
// 안전장치: 오류율 20% 초과 또는 p95 10초 초과 시 다음(더 높은) 동시 사용자 단계로 가지 않고 중단한다.
import { writeFileSync, mkdirSync } from 'node:fs';

const args = Object.fromEntries(process.argv.slice(2).reduce((a, v, i, arr) => (v.startsWith('--') ? [...a, [v.slice(2), arr[i + 1]]] : a), []));
const base = (args.base ?? 'http://localhost:8080').replace(/\/$/, '');
const scenario = args.scenario ?? 'list12';
const levels = (args.levels ?? '1,10,50').split(',').map(Number);
const seconds = Number(args.seconds ?? 15);
const timeoutMs = 15000;

async function getJson(path) {
  const res = await fetch(base + path, { signal: AbortSignal.timeout(timeoutMs) });
  return { status: res.status, body: res.ok ? await res.json() : null };
}

// 시작 전에 상세 조회용 공고 id 목록을 확보한다 (측정에서 제외)
const first = await getJson('/api/jobs?size=100&page=0');
if (!first.body) throw new Error('목록 조회 실패: HTTP ' + first.status);
const ids = first.body.content.map((j) => j.jobPostingId ?? j.id).filter(Boolean);
if (ids.length === 0) throw new Error('공고가 없습니다');
const hotId = ids[0];
console.log(`대상 ${base} / 시나리오 ${scenario} / 공고 ${first.body.totalElements}건 / 상세 후보 ${ids.length}개`);

function pickPath() {
  switch (scenario) {
    case 'list12': return '/api/jobs?size=12&page=0&sortBy=latest';
    case 'list100': return '/api/jobs?size=100&page=0&sortBy=latest';
    case 'detail': return `/api/jobs/${hotId}`;
    case 'detail_spread': return `/api/jobs/${ids[Math.floor(Math.random() * ids.length)]}`; // 대조군: 같은 코드 경로, 행만 분산
    case 'mixed': return Math.random() < 0.8 ? '/api/jobs?size=12&page=0&sortBy=latest' : `/api/jobs/${ids[Math.floor(Math.random() * ids.length)]}`;
    default: throw new Error('알 수 없는 시나리오: ' + scenario);
  }
}

const pct = (sorted, p) => (sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))] : NaN);

async function runLevel(c) {
  const lat = []; let ok = 0, fail = 0; const codes = {};
  const end = Date.now() + seconds * 1000;
  async function worker() {
    while (Date.now() < end) {
      const t0 = performance.now();
      try {
        const res = await fetch(base + pickPath(), { signal: AbortSignal.timeout(timeoutMs) });
        await res.arrayBuffer();
        const dt = performance.now() - t0;
        codes[res.status] = (codes[res.status] ?? 0) + 1;
        if (res.ok) { ok++; lat.push(dt); } else fail++;
      } catch (e) {
        fail++; codes[e.name] = (codes[e.name] ?? 0) + 1;
      }
    }
  }
  const t0 = performance.now();
  await Promise.all(Array.from({ length: c }, worker));
  const elapsed = (performance.now() - t0) / 1000;
  lat.sort((a, b) => a - b);
  return {
    concurrency: c, requests: ok + fail, rps: +((ok + fail) / elapsed).toFixed(1), errorRate: +((fail / Math.max(1, ok + fail)) * 100).toFixed(1),
    p50: +pct(lat, 50).toFixed(0), p95: +pct(lat, 95).toFixed(0), p99: +pct(lat, 99).toFixed(0), max: +(lat.at(-1) ?? NaN).toFixed(0), codes,
  };
}

// 워밍업 (JIT·연결 풀 준비) — 결과에서 제외
for (let i = 0; i < 5; i++) await getJson('/api/jobs?size=12&page=0');

const results = [];
for (const c of levels) {
  const r = await runLevel(c);
  results.push(r);
  console.log(`동시 ${String(c).padStart(3)}명 | ${String(r.rps).padStart(7)} req/s | p50 ${r.p50}ms p95 ${r.p95}ms p99 ${r.p99}ms max ${r.max}ms | 오류 ${r.errorRate}% ${JSON.stringify(r.codes)}`);
  if (r.errorRate > 20 || r.p95 > 10000) { console.log('안전 기준 초과 → 더 높은 단계는 실행하지 않고 중단'); break; }
}

mkdirSync(new URL('./results/', import.meta.url), { recursive: true });
const label = args.label ?? `${scenario}_${new URL(base).hostname.replace(/\W/g, '_')}`;
writeFileSync(new URL(`./results/${label}.json`, import.meta.url), JSON.stringify({ base, scenario, seconds, results }, null, 2));
