// Chave pública oficial da Cloudflare para testes do widget invisível.
// Ela só é usada pelo Vite em desenvolvimento; produção continua exigindo
// VITE_TURNSTILE_SITE_KEY no ambiente do build.
const LOCAL_TEST_SITE_KEY = '1x00000000000000000000BB';

const configuredSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY;

export const TURNSTILE_LOCAL_TEST_MODE = !configuredSiteKey && import.meta.env.DEV;
export const TURNSTILE_SITE_KEY = configuredSiteKey
  || (TURNSTILE_LOCAL_TEST_MODE ? LOCAL_TEST_SITE_KEY : '');
