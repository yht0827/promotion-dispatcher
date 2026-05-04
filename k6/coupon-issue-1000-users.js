import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter, Rate} from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const PROMOTION_ID = __ENV.PROMOTION_ID || `${Date.now()}`;
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
const BASE_USER_ID = Number(__ENV.BASE_USER_ID || 1000000);
const VUS = Number(__ENV.VUS || 1000);
const ITERATIONS = Number(__ENV.ITERATIONS || 1);
const MAX_DURATION = __ENV.MAX_DURATION || '2m';

const acceptedRate = new Rate('coupon_issue_accepted_rate');
const conflictRate = new Rate('coupon_issue_conflict_rate');
const rateLimitedRate = new Rate('coupon_issue_rate_limited_rate');
const unexpectedStatusRate = new Rate('coupon_issue_unexpected_status_rate');
const acceptedCount = new Counter('coupon_issue_accepted_count');
const conflictCount = new Counter('coupon_issue_conflict_count');
const rateLimitedCount = new Counter('coupon_issue_rate_limited_count');

export const options = {
  scenarios: {
    coupon_issue_users: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: MAX_DURATION,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    coupon_issue_unexpected_status_rate: ['rate==0'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const userId = BASE_USER_ID + __VU;
  const idempotencyKey = `k6-${RUN_ID}-promotion-${PROMOTION_ID}-user-${userId}-iter-${__ITER}`;
  const response = http.post(
    `${BASE_URL}/api/v1/promotions/${PROMOTION_ID}/coupons/issue`,
    JSON.stringify(requestBody()),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': `${userId}`,
        'Idempotency-Key': idempotencyKey,
      },
      tags: {
        api: 'coupon-issue',
      },
    }
  );

  const accepted = response.status === 202;
  const conflict = response.status === 409;
  const rateLimited = response.status === 429;
  const expected = accepted || conflict || rateLimited;

  acceptedRate.add(accepted);
  conflictRate.add(conflict);
  rateLimitedRate.add(rateLimited);
  unexpectedStatusRate.add(!expected);

  if (accepted) {
    acceptedCount.add(1);
  }
  if (conflict) {
    conflictCount.add(1);
  }
  if (rateLimited) {
    rateLimitedCount.add(1);
  }

  check(response, {
    'status is expected': () => expected,
    'accepted response has requestId': (res) => response.status !== 202 || Boolean(res.json('requestId')),
  });

  sleep(0.01);
}

function requestBody() {
  return {
    requestField1: 'value-1',
    requestField2: 'value-2',
    requestField3: 'value-3',
    requestField4: 'value-4',
    requestField5: 'value-5',
    requestField6: 'value-6',
    requestField7: 'value-7',
    requestField8: 'value-8',
    requestField9: 'value-9',
    requestField10: 'value-10',
  };
}
