// Cliente HTTP único do app. Porta a lógica de renovação de token do front
// antigo (scripts/core/api.js): 401/403 → tenta /auth/refresh UMA vez (promessa
// compartilhada entre requisições concorrentes) e refaz a requisição original.
// Se o refresh responder 401, a sessão acabou → limpa e manda para a home;
// qualquer outra falha (rede, 5xx, 429) é passageira e preserva a sessão.
import { endpoints } from '@/api/endpoints';
import { lerSessao, gravarSessao, limparSessao } from '@/api/session';

let refreshing = null; // promessa compartilhada p/ evitar refresh duplicado

// O QueryClient vive em main.jsx e não pode ser importado aqui sem criar um
// ciclo. O evento permite que a raiz descarte imediatamente qualquer dado em
// cache quando a sessão expira, antes de mandar o usuário para o login.
export const SESSION_EXPIRED_EVENT = 'jdi:session-expired';

function expirarSessao() {
  // Requisições concorrentes podem descobrir a expiração ao mesmo tempo. A
  // primeira limpa e redireciona; as demais não repetem esses efeitos.
  if (!lerSessao()) return;
  limparSessao();
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  window.location.assign('/');
}

function sessaoFoiRecusadaDepoisDaRenovacao(response, url) {
  // 401 sempre significa que a credencial não autenticou. Um 403 ainda pode
  // ser uma regra de negócio legítima; por compatibilidade com backends antigos,
  // tratamos como sessão apenas quando o próprio /auth/me também foi recusado.
  return response.status === 401
    || (response.status === 403 && url === endpoints.auth.me);
}

function finalizarRepeticao(response, url) {
  if (sessaoFoiRecusadaDepoisDaRenovacao(response, url)) expirarSessao();
  return response;
}

function refreshTokens() {
  if (refreshing) return refreshing; // correção -  atua como uma trava: se já tem refresh em andamento, espera ele terminar e devolve o access token novo.
  const sessao = lerSessao();
  if (!sessao?.refreshToken) return Promise.reject(new ApiError('Sessão expirada', 401));

  refreshing = fetch(endpoints.auth.refresh, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: sessao.refreshToken }),
  })
    .then((res) => {
      if (!res.ok) throw new ApiError('Falha ao renovar sessão', res.status);
      return res.json();
    })
    .then((data) => {
      gravarSessao({ accessToken: data.accessToken, refreshToken: data.refreshToken });
      return data.accessToken;
    })
    .finally(() => { refreshing = null; });

  return refreshing;
}

// quando algo da errado emite o aviso e retorna o status code e o corpo da resposta (JSON ou null)
export class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}
// pega a resposta e transforma em JSON ( ou null se não houver corpo)
// injeta o acess token no header Authorization 
function enviar(method, url, body, accessToken, headersExtras = {}) {
  const headers = { 'Content-Type': 'application/json', ...headersExtras };
  if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`;
  const options = { method, headers };
  if (body !== undefined) options.body = JSON.stringify(body);
  return fetch(url, options);
}

async function tratarResposta(res) {
  if (!res.ok) {
    let corpo = null;
    try { corpo = await res.json(); } catch { /* corpo vazio/não-JSON */ }
    throw new ApiError(corpo?.error || corpo?.message || `Erro ${res.status}`, res.status, corpo);
  }
  if (res.status === 204) return null;
  // Alguns endpoints respondem 200/201 com corpo vazio; res.json() quebraria.
  const txt = await res.text();
  return txt ? JSON.parse(txt) : null;
}

// Faz a requisição com renovação de token e devolve a Response crua. Quem chama
// decide como ler o corpo — JSON (request) ou binário (baixarArquivo).
async function requisitar(method, url, body, headersExtras) {
  const sessao = lerSessao();
  const res = await enviar(method, url, body, sessao?.accessToken, headersExtras);

  const respostaDeAutenticacao = res.status === 401 || res.status === 403;
  if (!respostaDeAutenticacao || url === endpoints.auth.refresh) return res;

  if (!sessao?.refreshToken) {
    // 403 pode ser autorização de negócio e não deve deslogar. Já 401, ou
    // a recusa do endpoint que identifica a conta, provam que a sessão acabou.
    if (sessaoFoiRecusadaDepoisDaRenovacao(res, url)) expirarSessao();
    return res;
  }

  // antes da correção: ter duas abas abertas com a mesma sessão podia levar a um refresh token ser descartado,
  // se ambas abas tentassem renovar ao mesmo tempo.
  //  A primeira aba que chegasse no backend ganhava, a segunda recebia 401 e perdia a sessão.
  // correção: o armazenamento da sessão é lido novamente antes de renovar;
  // abas da mesma sessão recebem o token novo sem trocar para outra conta.
  const atual = lerSessao();
  if (atual?.accessToken && atual.accessToken !== sessao.accessToken) {
    return finalizarRepeticao(await enviar(method, url, body, atual.accessToken, headersExtras), url);
  }

  let novoAccess;
  try {
    novoAccess = await refreshTokens();
  } catch (e) {
    // antes da correção: qualquer erro de rede, falha no servidor, erro 500 (internal server error) 
    // ou 429 (too many requests) derrubava o usuário.
    // Agora, só encerra a sessão quando o servidor diz que o refresh token não vale mais (401). 
    if (e instanceof ApiError && e.status === 401) {
      expirarSessao();
    }
    throw e;
  }
  // Refresh OK: refaz a requisição original. Se o token novo também for
  // recusado, não mantém uma sessão fantasma com o painel preso em 401/403.
  return finalizarRepeticao(await enviar(method, url, body, novoAccess, headersExtras), url);
}

async function request(method, url, body, headersExtras) {
  return tratarResposta(await requisitar(method, url, body, headersExtras));
}

export const api = {
  get: (url) => request('GET', url),
  post: (url, body, config) => request('POST', url, body, config?.headers),
  put: (url, body) => request('PUT', url, body),
  patch: (url, body) => request('PATCH', url, body),
  remove: (url) => request('DELETE', url),
};

// Extrai o nome do arquivo do Content-Disposition. O backend expõe esse cabeçalho
// no CORS; se algum proxy o engolir, devolve null e quem chama usa um nome padrão.
function nomeNoCabecalho(disposition) {
  if (!disposition) return null;
  const casou = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  if (!casou) return null;
  try { return decodeURIComponent(casou[1]); } catch { return casou[1]; }
}

// Baixa um arquivo (exportação de dados) pelo mesmo caminho de renovação de
// token das demais chamadas. Devolve o corpo cru como Blob — nada de JSON.parse,
// porque o CSV quebraria — junto do nome sugerido pelo servidor.
export async function baixarArquivo(url) {
  const res = await requisitar('GET', url);
  if (!res.ok) {
    // Mesmo quando o sucesso viria em CSV, o erro do backend vem em JSON.
    let corpo = null;
    try { corpo = await res.json(); } catch { /* corpo vazio/não-JSON */ }
    throw new ApiError(corpo?.error || corpo?.message || `Erro ${res.status}`, res.status, corpo);
  }
  return {
    blob: await res.blob(),
    nomeArquivo: nomeNoCabecalho(res.headers.get('Content-Disposition')),
  };
}

// GET que trata 404 como "ainda não existe" (configs de módulo, nota, timer…)
// em vez de erro — o backend responde 404 até o primeiro PUT.
export async function getOuNull(url) {
  try {
    return await api.get(url);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}