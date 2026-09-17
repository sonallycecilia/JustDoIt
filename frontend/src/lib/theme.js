// Tema claro/escuro — preferência de UI, o único outro dado (além da sessão)
// que fica em localStorage. Mesma chave do front antigo ('jdi.tema').
const KEY = 'jdi.tema';

export function lerTema() {
  try { return JSON.parse(localStorage.getItem(KEY)); } catch { return null; }
}

export function aplicarTema(tema) {
  if (tema === 'dark') document.documentElement.setAttribute('data-theme', 'dark');
  else document.documentElement.removeAttribute('data-theme');
}

export function iniciarTema() {
  const salvo = lerTema();
  const prefereDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
  aplicarTema(salvo || (prefereDark ? 'dark' : 'light'));
}

// Preferência escolhida pelo usuário: 'light' | 'dark' | 'system'.
// "system" é representado pela AUSÊNCIA da chave (mesma convenção do app antigo,
// que faz Store.remover('tema')), por isso não há valor 'system' gravado.
export function preferenciaTema() {
  return lerTema() || 'system';
}

export function definirTema(pref) {
  if (pref === 'system') {
    localStorage.removeItem(KEY);
    aplicarTema(window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  } else {
    localStorage.setItem(KEY, JSON.stringify(pref));
    aplicarTema(pref);
  }
}

export function alternarTema() {
  const novo = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  aplicarTema(novo);
  localStorage.setItem(KEY, JSON.stringify(novo));
  return novo;
}
