// Conta do usuário (auth-service): GET/PUT/DELETE /auth/me.
// A sessão local guarda nome/e-mail só para pintar a sidebar antes da resposta
// chegar — o backend continua sendo a fonte da verdade.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { gravarSessao } from '@/api/session';
import { useEncerrarSessao } from '@/features/auth/hooks/useSessao';

export function useConta() {
  return useQuery({
    queryKey: ['usuario'],
    queryFn: async () => {
      const user = await api.get(endpoints.auth.me);
      if (user?.name) gravarSessao({ name: user.name, email: user.email });
      return user;
    },
    staleTime: 5 * 60_000,
  });
}

// PUT parcial: envie só os campos que mudaram ({name}, {email},
// {currentPassword,newPassword} ou {avatarUrl}).
export function useAtualizarConta() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dados) => api.put(endpoints.auth.me, dados),
    onSuccess: (user) => {
      if (user) {
        qc.setQueryData(['usuario'], user);
        gravarSessao({ name: user.name, email: user.email });
      }
    },
  });
}

export function useExcluirConta() {
  const encerrarSessao = useEncerrarSessao();
  return useMutation({
    mutationFn: () => api.remove(endpoints.auth.me),
    // A conta deixou de existir no backend: o cache dela não pode sobreviver
    // para a próxima sessão desta mesma aba.
    onSuccess: () => encerrarSessao(),
  });
}

// Mensagens do backend traduzidas para o texto exibido ao usuário.
export function traduzErroConta(err) {
  const m = err?.message || '';
  if (/already registered/i.test(m)) return 'Este email já está em uso.';
  if (/current password/i.test(m)) return 'Senha atual incorreta.';
  if (/Password must be/i.test(m)) return 'A nova senha deve ter entre 8 e 100 caracteres.';
  return m || 'Não foi possível salvar as alterações.';
}
