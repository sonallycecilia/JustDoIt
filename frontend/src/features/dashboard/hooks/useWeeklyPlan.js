// Plano semanal (schedule-service, /weekly-plans).
//
// O plano é o que transforma a Análise de painel ao vivo em histórico: enquanto
// a semana está OPEN os números são recalculados a cada visita; ao FECHAR, o
// backend grava o retrato e passa a devolvê-lo congelado. Sem isso, olhar uma
// semana antiga mostraria o estado ATUAL das tarefas, não como a semana foi.
//
// O frontend não guarda o id do plano em lugar nenhum: acha pela data da segunda
// (GET /weekly-plans?weekStart=). O POST é idempotente no backend, então abrir a
// semana duas vezes devolve o mesmo plano.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/api/client';
import { endpoints } from '@/api/endpoints';

// 404 aqui não é erro: significa "esta semana ainda não foi aberta/resumida".
async function getOu404(url) {
  try {
    return await api.get(url);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export function usePlanoSemanal(semana, { enabled = true } = {}) {
  const qc = useQueryClient();
  const chavePlano = ['plano-semanal', semana.inicioIso];

  const plano = useQuery({
    queryKey: chavePlano,
    queryFn: () => getOu404(endpoints.weeklyPlans.atual(semana.inicioIso)),
    staleTime: 60_000,
    enabled,
  });

  const planoId = plano.data?.id || null;
  const fechada = plano.data?.status === 'CLOSED';

  // O resumo só interessa depois de fechada: com a semana aberta, a tela mostra
  // os números ao vivo (useAnaliseSemanal), que não dependem de plano nenhum.
  const resumo = useQuery({
    queryKey: ['resumo-semanal', planoId],
    queryFn: () => getOu404(endpoints.weeklyPlans.summary(planoId)),
    enabled: enabled && Boolean(planoId) && fechada,
    staleTime: 60_000,
  });

  // Fechar a semana: abre o plano se ainda não existir (o backend devolve o
  // existente sem duplicar) e então fecha. O close já gera o resumo no servidor.
  const fechar = useMutation({
    mutationFn: async () => {
      let id = planoId;
      if (!id) {
        const criado = await api.post(endpoints.weeklyPlans.create, {
          weekStartDate: semana.inicioIso,
          weekEndDate: semana.fimIso,
        });
        id = criado.id;
      }
      return api.patch(endpoints.weeklyPlans.close(id), {});
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: chavePlano });
      qc.invalidateQueries({ queryKey: ['resumo-semanal'] });
    },
  });

  return {
    plano: plano.data || null,
    fechada,
    resumo: resumo.data || null,
    carregando: plano.isLoading || resumo.isLoading,
    erro: plano.error || resumo.error || null,
    fechar,
  };
}
