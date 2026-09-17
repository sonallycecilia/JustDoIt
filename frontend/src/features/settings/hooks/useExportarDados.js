import { useMutation } from '@tanstack/react-query';
import { api, baixarArquivo } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { nomePadrao, salvarArquivo } from '@/features/settings/lib/exportacao';

const POLL_INTERVAL_MS = 1000;
const CLIENT_TIMEOUT_MS = 31 * 60 * 1000;

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

export function useExportarDados() {
  return useMutation({
    mutationFn: async (formato) => {
      const accepted = await api.post(endpoints.dados.solicitarExportacao(formato));
      if (!accepted?.jobId) throw new Error('O servidor não retornou o job da exportação.');

      const deadline = Date.now() + CLIENT_TIMEOUT_MS;
      let job;
      do {
        job = await api.get(endpoints.dados.statusExportacao(accepted.jobId));
        if (job.status === 'FAILED') {
          throw new Error(job.error || 'Não foi possível gerar a exportação.');
        }
        if (job.status === 'EXPIRED') {
          throw new Error('A exportação expirou. Solicite um novo arquivo.');
        }
        if (job.status === 'COMPLETED') break;
        if (Date.now() >= deadline) throw new Error('A exportação excedeu o tempo limite.');
        await wait(POLL_INTERVAL_MS);
      } while (true);

      if (!job.downloadUrl) throw new Error('O link temporário não foi disponibilizado.');
      const { blob, nomeArquivo } = await baixarArquivo(job.downloadUrl);
      const nome = nomeArquivo || nomePadrao(formato);
      salvarArquivo(blob, nome);
      return nome;
    },
  });
}
