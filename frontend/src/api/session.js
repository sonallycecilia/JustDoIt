// Cada aba mantém sua conta ativa no sessionStorage. As sessões marcadas como
// "Manter conectado" também ficam em chaves independentes no localStorage,
// identificadas pelo login que as originou. Entrar em outra conta não apaga
// nem substitui a sessão da primeira.
const LEGACY_KEY = 'jdi.sessao';
const ACTIVE_KEY = 'jdi.sessao.ativa';
const REMEMBERED_PREFIX = 'jdi.sessao.lembrada.';
const LAST_SESSION_KEY = 'jdi.sessao.ultima';

function parse(storage, key) {
  try {
    const raw = storage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function sessionId() {
  try {
    return crypto.randomUUID();
  } catch {
    return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
}

function rememberedSessions() {
  const sessions = [];
  for (let index = 0; index < localStorage.length; index += 1) {
    const key = localStorage.key(index);
    if (!key?.startsWith(REMEMBERED_PREFIX)) continue;
    const session = parse(localStorage, key);
    if (session?.sessionId) sessions.push(session);
  }
  return sessions;
}

function rememberedSession(id) {
  return id ? parse(localStorage, `${REMEMBERED_PREFIX}${id}`) : null;
}

function saveRemembered(session) {
  localStorage.setItem(`${REMEMBERED_PREFIX}${session.sessionId}`, JSON.stringify(session));
}

function normalize(session, remember) {
  return {
    ...session,
    sessionId: session.sessionId || sessionId(),
    lembrar: remember ?? Boolean(session.lembrar),
    em: session.em || Date.now(),
  };
}

function migrateLegacyLocalSession() {
  const legacy = parse(localStorage, LEGACY_KEY);
  if (!legacy) return null;

  const migrated = normalize(legacy, true);
  saveRemembered(migrated);
  localStorage.setItem(LAST_SESSION_KEY, migrated.sessionId);
  localStorage.removeItem(LEGACY_KEY);
  return migrated;
}

function newestRememberedSession() {
  const sessions = rememberedSessions();
  const lastId = localStorage.getItem(LAST_SESSION_KEY);
  const last = rememberedSession(lastId);
  if (last) return last;
  return sessions.sort((a, b) => (b.em || 0) - (a.em || 0))[0] || null;
}

export function lerSessao() {
  try {
    let active = parse(sessionStorage, ACTIVE_KEY);

    // Migra a sessão curta do formato anterior sem derrubar o usuário.
    if (!active) {
      const legacyTab = parse(sessionStorage, LEGACY_KEY);
      if (legacyTab) {
        active = normalize(legacyTab, false);
        sessionStorage.setItem(ACTIVE_KEY, JSON.stringify(active));
        sessionStorage.removeItem(LEGACY_KEY);
      }
    }

    if (active) {
      // Outra aba da MESMA sessão pode ter rotacionado os tokens. Adota o par
      // mais novo sem trocar de conta, pois o sessionId precisa ser o mesmo.
      if (active.lembrar && active.sessionId) {
        const newer = rememberedSession(active.sessionId);
        if (newer && (newer.em || 0) > (active.em || 0)) {
          active = newer;
          sessionStorage.setItem(ACTIVE_KEY, JSON.stringify(active));
        }
      }
      return active;
    }

    const remembered = migrateLegacyLocalSession() || newestRememberedSession();
    if (!remembered) return null;

    active = normalize(remembered, true);
    sessionStorage.setItem(ACTIVE_KEY, JSON.stringify(active));
    return active;
  } catch {
    return null;
  }
}

/**
 * `lembrar` é informado apenas ao iniciar uma sessão. Atualizações posteriores
 * preservam o sessionId e propagam tokens rotacionados somente para as abas que
 * pertencem à mesma sessão.
 */
export function gravarSessao(dados, { lembrar } = {}) {
  const newSession = lembrar !== undefined;

  try {
    // Preserva uma sessão lembrada do formato antigo antes de iniciar outra.
    if (newSession) migrateLegacyLocalSession();

    const base = newSession ? {} : (lerSessao() || {});
    const session = {
      ...base,
      ...dados,
      sessionId: newSession ? sessionId() : (base.sessionId || sessionId()),
      lembrar: newSession ? Boolean(lembrar) : Boolean(base.lembrar),
      em: Math.max(Date.now(), (base.em || 0) + 1),
    };

    sessionStorage.setItem(ACTIVE_KEY, JSON.stringify(session));
    sessionStorage.removeItem(LEGACY_KEY);

    if (session.lembrar) {
      saveRemembered(session);
      localStorage.setItem(LAST_SESSION_KEY, session.sessionId);
    }
  } catch { /* storage indisponível: segue sem persistir */ }
}

export function limparSessao() {
  try {
    const active = parse(sessionStorage, ACTIVE_KEY) || parse(sessionStorage, LEGACY_KEY);
    sessionStorage.removeItem(ACTIVE_KEY);
    sessionStorage.removeItem(LEGACY_KEY);

    if (active?.sessionId) {
      localStorage.removeItem(`${REMEMBERED_PREFIX}${active.sessionId}`);

      if (localStorage.getItem(LAST_SESSION_KEY) === active.sessionId) {
        const next = rememberedSessions()
          .sort((a, b) => (b.em || 0) - (a.em || 0))[0];
        if (next) localStorage.setItem(LAST_SESSION_KEY, next.sessionId);
        else localStorage.removeItem(LAST_SESSION_KEY);
      }
    } else {
      // Compatibilidade com logout antes de a sessão antiga ser migrada.
      localStorage.removeItem(LEGACY_KEY);
    }
  } catch { /* storage indisponível */ }
}

export function estaLogado() {
  const s = lerSessao();
  return Boolean(s && s.accessToken);
}
