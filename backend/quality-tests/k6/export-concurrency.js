import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const API = __ENV.TASK_API_URL || 'http://localhost:8081';
const tokens = (__ENV.ACCESS_TOKENS || '').split(',').map((value) => value.trim()).filter(Boolean);
const regularLatency = new Trend('regular_endpoint_duration', true);
const regularFailures = new Rate('regular_endpoint_failures');
const submitLatency = new Trend('export_submit_duration', true);

export const options = {
  scenarios: {
    exports: {
      executor: 'constant-vus',
      exec: 'submitExports',
      vus: Number(__ENV.EXPORT_VUS || 4),
      duration: __ENV.TEST_DURATION || '30s',
    },
    regularApi: {
      executor: 'constant-vus',
      exec: 'callRegularEndpoint',
      vus: Number(__ENV.API_VUS || 5),
      duration: __ENV.TEST_DURATION || '30s',
    },
  },
  thresholds: {
    export_submit_duration: ['p(95)<500'],
    regular_endpoint_duration: ['p(95)<1000'],
    regular_endpoint_failures: ['rate==0'],
  },
};

export function setup() {
  if (tokens.length === 0) fail('Informe ACCESS_TOKENS com um JWT válido por usuário concorrente.');
}

function params() {
  const token = tokens[(__VU - 1) % tokens.length];
  return { headers: { Authorization: `Bearer ${token}` }, timeout: '5s' };
}

export function submitExports() {
  const response = http.post(`${API}/me/exports?format=json`, null, params());
  submitLatency.add(response.timings.duration);
  check(response, {
    'exportação é aceita ou limitada explicitamente': (value) => [202, 429].includes(value.status),
  });
}

export function callRegularEndpoint() {
  const response = http.get(`${API}/tasks`, params());
  regularLatency.add(response.timings.duration);
  const ok = check(response, {
    'endpoint comum permanece disponível': (value) => value.status === 200,
    'endpoint comum não sofre timeout': (value) => !value.error_code,
  });
  regularFailures.add(!ok);
}
