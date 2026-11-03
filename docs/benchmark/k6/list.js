import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

// 벤치 네트워크 내부에서 앱 서비스명(app)으로 접근
const BASE = __ENV.BASE || 'http://bench-app:8080/api';
const VUS = __ENV.VUS || 20;
const DURATION = __ENV.DURATION || '30s';

const firstPage = new Trend('t_first_page', true);   // 첫 페이지(전체 정렬) 응답
const deepPage = new Trend('t_deep_page', true);     // 커서 다음 페이지 응답

export const options = {
  scenarios: {
    load: { executor: 'constant-vus', vus: Number(VUS), duration: DURATION },
  },
  thresholds: {
    // 정보용(실패시켜도 무방) — 값 자체를 리포트에서 확인
    t_first_page: ['p(95)>=0'],
  },
};

export function setup() {
  const res = http.post(
    `${BASE}/auth/login`,
    JSON.stringify({ email: 'benchuser@test.com', password: 'benchpass1!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const token = res.json('data.access_token');
  if (!token) throw new Error('login failed: ' + res.body);
  return { token };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };

  // 1) 첫 페이지 (cursor 없음 → 전체 created_at 정렬)
  const r1 = http.get(`${BASE}/posts?size=20`, { headers });
  firstPage.add(r1.timings.duration);
  check(r1, { 'first 200': (r) => r.status === 200 });

  // 2) 커서 기반 다음 페이지 (첫 페이지 마지막 id를 커서로)
  let cursor = null;
  try { const posts = r1.json('data.posts'); cursor = posts[posts.length - 1].id; } catch (e) {}
  if (cursor) {
    const r2 = http.get(`${BASE}/posts?size=20&cursor=${cursor}`, { headers });
    deepPage.add(r2.timings.duration);
    check(r2, { 'deep 200': (r) => r.status === 200 });
  }
}
