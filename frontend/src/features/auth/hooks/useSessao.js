// Troca de sessão (entrar/sair) sem recarregar a página.
//
// O QueryClient é criado uma vez em main.jsx e sobrevive a toda navegação do
// React Router — que é client-side. Como `limparSessao()` mexe só no storage,
// sair e entrar com OUTRA conta na mesma aba deixava o cache da conta anterior
// de pé: ['tarefas'], ['categorias'] e ['usuario'] do usuário antigo apareciam
// para o novo até o refetch chegar (staleTime de 30s nas tarefas/categorias).
//
// Além do vazamento entre contas, isso quebrava a criação de tarefas: o editor
// escolhe a categoria a partir da lista em cache, e um categoryId de outro dono
// faz o POST /tasks falhar no backend (a categoria não é encontrada para o
// usuário do token).
//
// Por isso todo ponto que inicia ou encerra sessão passa por aqui.
import { useQueryClient } from '@tanstack/react-query';
import { gravarSessao, limparSessao } from '@/api/session';
import { limparRascunho } from '@/features/tasks/hooks/useRascunhoTarefa';

/**
 * Grava a sessão nova e descarta o cache da anterior. `opcoes` é repassado a
 * `gravarSessao` ({ lembrar } no login/cadastro).
 *
 * A ordem importa: os tokens novos vão para o storage ANTES do clear, porque
 * limpar o cache faz as queries ativas refazerem a busca na hora — e elas
 * precisam sair já com o token do usuário que acabou de entrar.
 */
export function useIniciarSessao() {
  const qc = useQueryClient();
  return (dados, opcoes) => {
    gravarSessao(dados, opcoes);
    qc.clear();
  };
}

/**
 * Encerra a sessão local e descarta o cache. Quem chama navega para a home em
 * seguida; o refetch que o clear dispara é assíncrono e não chega a pintar nada
 * antes da saída da página.
 */
export function useEncerrarSessao() {
  const qc = useQueryClient();
  return () => {
    limparSessao();
    qc.clear();
    // O rascunho da tarefa em criação também é dado de quem estava logado:
    // deixá-lo no localStorage entregaria o texto de uma conta para a próxima.
    limparRascunho();
  };
}
