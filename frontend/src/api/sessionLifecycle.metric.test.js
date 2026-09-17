import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { gravarSessao, lerSessao, limparSessao } from '@/api/session';

const ACTIVE_SESSION_KEY = 'jdi.sessao.ativa';
const REMEMBERED_SESSION_PREFIX = 'jdi.sessao.lembrada.';
const TOTAL_SCENARIOS = 11;
let passedScenarios = 0;

function response(status, body = null) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(),
    json: vi.fn().mockResolvedValue(body),
    text: vi.fn().mockResolvedValue(body === null ? '' : JSON.stringify(body)),
  };
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function protectedCalls(fetchMock, protectedUrl) {
  return fetchMock.mock.calls.filter(([url]) => url === protectedUrl);
}

function refreshCalls(fetchMock) {
  return fetchMock.mock.calls.filter(([url]) => url === endpoints.auth.refresh);
}

function passScenario() {
  passedScenarios += 1;
}

beforeEach(() => {
  limparSessao();
  localStorage.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();
  vi.stubGlobal('fetch', vi.fn());
});

afterAll(() => {
  const tps = (passedScenarios / TOTAL_SCENARIOS) * 100;
  console.log(
    `[MÉTRICA SEGURANÇA - CICLO DE SESSÃO FRONTEND] `
      + `A=${passedScenarios} cenários corretos / B=${TOTAL_SCENARIOS} cenários testados -> TPS = ${tps.toFixed(2)}%`,
  );
  expect(passedScenarios, 'TPS do frontend deve atingir exatamente 100%').toBe(TOTAL_SCENARIOS);
});

describe('Taxa de Proteção do Ciclo de Sessão (TPS) - cliente frontend', () => {
  it('1/11: compartilha uma única renovação entre requisições concorrentes', async () => {
    const protectedUrl = endpoints.auth.me;
    const refreshResponse = deferred();
    let attempts = 0;
    fetch.mockImplementation((url) => {
      if (url === endpoints.auth.refresh) return refreshResponse.promise;
      attempts += 1;
      if (attempts <= 2) return Promise.resolve(response(401));
      return Promise.resolve(response(200, { attempt: attempts }));
    });
    gravarSessao({ accessToken: 'expired.access', refreshToken: 'valid.refresh' }, { lembrar: true });

    const first = api.get(protectedUrl);
    const second = api.get(protectedUrl);
    await vi.waitFor(() => expect(refreshCalls(fetch)).toHaveLength(1));

    refreshResponse.resolve(response(200, {
      accessToken: 'rotated.access',
      refreshToken: 'rotated.refresh',
    }));

    await expect(Promise.all([first, second])).resolves.toHaveLength(2);
    expect(refreshCalls(fetch)).toHaveLength(1);
    expect(protectedCalls(fetch, protectedUrl)).toHaveLength(4);
    const replayHeaders = protectedCalls(fetch, protectedUrl).slice(-2)
      .map(([, options]) => options.headers.Authorization);
    expect(replayHeaders).toEqual(['Bearer rotated.access', 'Bearer rotated.access']);
    passScenario();
  });

  it('2/11: reutiliza o access token que outra aba já renovou sem novo refresh', async () => {
    const protectedUrl = endpoints.auth.me;
    const firstResponse = deferred();
    fetch
      .mockImplementationOnce(() => firstResponse.promise)
      .mockResolvedValueOnce(response(200, { id: 'current-user' }));
    gravarSessao({ accessToken: 'old.access', refreshToken: 'old.refresh' }, { lembrar: true });

    const request = api.get(protectedUrl);
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    const current = lerSessao();
    localStorage.setItem(`${REMEMBERED_SESSION_PREFIX}${current.sessionId}`, JSON.stringify({
      ...current,
      accessToken: 'other-tab.access',
      refreshToken: 'other-tab.refresh',
      em: current.em + 1,
    }));
    firstResponse.resolve(response(401));

    await expect(request).resolves.toEqual({ id: 'current-user' });
    expect(refreshCalls(fetch)).toHaveLength(0);
    expect(fetch.mock.calls[1][1].headers.Authorization).toBe('Bearer other-tab.access');
    passScenario();
  });

  it('3/11: persiste o par rotacionado sem mudar a escolha de manter conectado', async () => {
    fetch
      .mockResolvedValueOnce(response(401))
      .mockResolvedValueOnce(response(200, {
        accessToken: 'new.access',
        refreshToken: 'new.refresh',
      }))
      .mockResolvedValueOnce(response(200, { ok: true }));
    gravarSessao({ accessToken: 'old.access', refreshToken: 'old.refresh' }, { lembrar: false });

    await expect(api.get(endpoints.auth.me)).resolves.toEqual({ ok: true });

    expect(lerSessao()).toMatchObject({ accessToken: 'new.access', refreshToken: 'new.refresh' });
    expect([...Array(localStorage.length).keys()]
      .map((index) => localStorage.key(index))
      .some((key) => key?.startsWith(REMEMBERED_SESSION_PREFIX))).toBe(false);
    expect(sessionStorage.getItem(ACTIVE_SESSION_KEY)).not.toBeNull();
    passScenario();
  });

  it('4/11: refresh 401 encerra a sessão local', async () => {
    fetch
      .mockResolvedValueOnce(response(401))
      .mockResolvedValueOnce(response(401));
    gravarSessao({ accessToken: 'expired.access', refreshToken: 'invalid.refresh' }, { lembrar: true });

    await expect(api.get(endpoints.auth.me)).rejects.toMatchObject({ status: 401 });

    expect(lerSessao()).toBeNull();
    passScenario();
  });

  it('5/11: refresh 429 preserva a sessão para nova tentativa', async () => {
    await expectTransientRefreshFailure(() => Promise.resolve(response(429)), 429);
    passScenario();
  });

  it('6/11: refresh 5xx preserva a sessão para nova tentativa', async () => {
    await expectTransientRefreshFailure(() => Promise.resolve(response(503)), 503);
    passScenario();
  });

  it('7/11: falha de rede no refresh preserva a sessão', async () => {
    const networkError = new TypeError('network unavailable');
    fetch
      .mockResolvedValueOnce(response(401))
      .mockRejectedValueOnce(networkError);
    gravarSessao({ accessToken: 'expired.access', refreshToken: 'retryable.refresh' }, { lembrar: true });

    await expect(api.get(endpoints.auth.me)).rejects.toBe(networkError);

    expect(lerSessao()).toMatchObject({
      accessToken: 'expired.access',
      refreshToken: 'retryable.refresh',
    });
    passScenario();
  });

  it('8/11: token renovado que continua em 401 encerra a sessão', async () => {
    fetch
      .mockResolvedValueOnce(response(401))
      .mockResolvedValueOnce(response(200, {
        accessToken: 'rejected.access',
        refreshToken: 'rotated.refresh',
      }))
      .mockResolvedValueOnce(response(401));
    gravarSessao({ accessToken: 'expired.access', refreshToken: 'valid.refresh' }, { lembrar: true });

    await expect(api.get(endpoints.auth.me)).rejects.toMatchObject({ status: 401 });
    expect(lerSessao()).toBeNull();
    passScenario();
  });

  it('9/11: /auth/me em 403 depois do refresh encerra sessões de backend legado', async () => {
    fetch
      .mockResolvedValueOnce(response(403))
      .mockResolvedValueOnce(response(200, {
        accessToken: 'rejected.access',
        refreshToken: 'rotated.refresh',
      }))
      .mockResolvedValueOnce(response(403));
    gravarSessao({ accessToken: 'expired.access', refreshToken: 'valid.refresh' }, { lembrar: false });

    await expect(api.get(endpoints.auth.me)).rejects.toMatchObject({ status: 403 });
    expect(lerSessao()).toBeNull();
    passScenario();
  });

  it('10/11: 403 de regra de negócio não encerra uma sessão válida', async () => {
    const businessUrl = endpoints.tasks.complete('closed-task');
    fetch
      .mockResolvedValueOnce(response(403))
      .mockResolvedValueOnce(response(200, {
        accessToken: 'valid.access',
        refreshToken: 'rotated.refresh',
      }))
      .mockResolvedValueOnce(response(403, { error: 'Ciclo fechado' }));
    gravarSessao({ accessToken: 'old.access', refreshToken: 'valid.refresh' }, { lembrar: false });

    await expect(api.patch(businessUrl)).rejects.toMatchObject({ status: 403 });
    expect(lerSessao()).toMatchObject({ accessToken: 'valid.access' });
    passScenario();
  });

  it('11/11: 401 sem refresh token limpa uma sessão legada incompleta', async () => {
    fetch.mockResolvedValueOnce(response(401));
    gravarSessao({ accessToken: 'expired.access' }, { lembrar: false });

    await expect(api.get(endpoints.auth.me)).rejects.toMatchObject({ status: 401 });
    expect(lerSessao()).toBeNull();
    passScenario();
  });
});

async function expectTransientRefreshFailure(refreshResult, expectedStatus) {
  fetch
    .mockResolvedValueOnce(response(401))
    .mockImplementationOnce(refreshResult);
  gravarSessao({ accessToken: 'expired.access', refreshToken: 'retryable.refresh' }, { lembrar: true });

  await expect(api.get(endpoints.auth.me)).rejects.toSatisfy(
    (error) => error instanceof ApiError && error.status === expectedStatus,
  );
  expect(lerSessao()).toMatchObject({
    accessToken: 'expired.access',
    refreshToken: 'retryable.refresh',
  });
}
