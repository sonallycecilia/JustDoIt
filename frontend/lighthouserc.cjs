const targetUrl = process.env.LCP_TARGET_URL ?? 'http://127.0.0.1:4173/';
const numberOfRuns = Number(process.env.LCP_EXPECTED_RUNS ?? 4);

if (!Number.isInteger(numberOfRuns) || numberOfRuns <= 0) {
  throw new Error(`LCP_EXPECTED_RUNS deve ser um inteiro positivo; recebido: ${process.env.LCP_EXPECTED_RUNS}`);
}

module.exports = {
  ci: {
    collect: {
      url: [targetUrl],
      numberOfRuns,
      // Invocar o Vite diretamente evita que o lhci espere por um processo npm
      // filho no Windows depois de concluir a coleta.
      startServerCommand: 'node ./node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 4173',
      startServerReadyPattern: 'Local',
      settings: {
        preset: 'desktop',
        onlyCategories: ['performance'],
        maxWaitForLoad: 30000,
        pauseAfterLoadMs: 1000,
      },
    },
  },
};
