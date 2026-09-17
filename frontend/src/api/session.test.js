import { beforeEach, describe, expect, it } from 'vitest';
import { gravarSessao, lerSessao, limparSessao } from '@/api/session';

const ACTIVE_SESSION_KEY = 'jdi.sessao.ativa';
const REMEMBERED_SESSION_PREFIX = 'jdi.sessao.lembrada.';

function activeSnapshot() {
  return sessionStorage.getItem(ACTIVE_SESSION_KEY);
}

function restoreTab(snapshot) {
  sessionStorage.clear();
  if (snapshot) sessionStorage.setItem(ACTIVE_SESSION_KEY, snapshot);
}

function rememberedCount() {
  return [...Array(localStorage.length).keys()]
    .map((index) => localStorage.key(index))
    .filter((key) => key?.startsWith(REMEMBERED_SESSION_PREFIX)).length;
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

describe('isolamento de contas entre abas', () => {
  it('mantém a conta A ativa quando a conta B entra em outra aba', () => {
    gravarSessao(
      { accessToken: 'access-a', refreshToken: 'refresh-a', email: 'a@example.com' },
      { lembrar: true },
    );
    const tabA = activeSnapshot();

    restoreTab(null);
    gravarSessao(
      { accessToken: 'access-b', refreshToken: 'refresh-b', email: 'b@example.com' },
      { lembrar: true },
    );

    expect(lerSessao()).toMatchObject({ accessToken: 'access-b', email: 'b@example.com' });
    expect(rememberedCount()).toBe(2);

    restoreTab(tabA);
    expect(lerSessao()).toMatchObject({ accessToken: 'access-a', email: 'a@example.com' });
  });

  it('logout da conta B preserva a conta A lembrada', () => {
    gravarSessao(
      { accessToken: 'access-a', refreshToken: 'refresh-a', email: 'a@example.com' },
      { lembrar: true },
    );

    restoreTab(null);
    gravarSessao(
      { accessToken: 'access-b', refreshToken: 'refresh-b', email: 'b@example.com' },
      { lembrar: true },
    );
    limparSessao();

    restoreTab(null);
    expect(lerSessao()).toMatchObject({ accessToken: 'access-a', email: 'a@example.com' });
  });

  it('sincroniza tokens rotacionados apenas entre abas da mesma sessão', () => {
    gravarSessao(
      { accessToken: 'old-access', refreshToken: 'old-refresh' },
      { lembrar: true },
    );
    const staleTab = activeSnapshot();

    gravarSessao({ accessToken: 'new-access', refreshToken: 'new-refresh' });
    restoreTab(staleTab);

    expect(lerSessao()).toMatchObject({
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
    });
  });

  it('migra a sessão persistente do formato anterior', () => {
    localStorage.setItem('jdi.sessao', JSON.stringify({
      accessToken: 'legacy-access',
      refreshToken: 'legacy-refresh',
    }));

    expect(lerSessao()).toMatchObject({
      accessToken: 'legacy-access',
      refreshToken: 'legacy-refresh',
      lembrar: true,
    });
    expect(localStorage.getItem('jdi.sessao')).toBeNull();
  });
});
