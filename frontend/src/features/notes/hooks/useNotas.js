// Anotações livres do usuário (aba "Anotações" + compositor no To Do).
// Fonte da verdade = backend (/notes, task-service). Só o RASCUNHO do
// compositor (texto que ainda não virou nota) fica em localStorage, na
// mesma chave do app antigo — o que se está escrevendo é o mesmo nos dois.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';

// NoteResponse → modelo da UI
function notaDaApi(n) {
  return {
    id: n.id,
    titulo: n.title || '',
    conteudo: n.content || '',
    categoriaId: n.categoryId || null,
    fixada: Boolean(n.pinned),
    criadaEm: n.createdAt || null,
    atualizadaEm: n.updatedAt || null,
  };
}

// modelo da UI → NoteRequest (strings vazias viram null — @NotBlank no backend)
function notaParaApi(d) {
  return {
    title: d.titulo && d.titulo.trim() ? d.titulo.trim() : null,
    content: d.conteudo != null && String(d.conteudo).trim() ? d.conteudo : null,
    categoryId: d.categoriaId && d.categoriaId !== 'generico' ? d.categoriaId : null,
  };
}

export function useNotas() {
  return useQuery({
    queryKey: ['notas'],
    queryFn: () => api.get(endpoints.notes.list),
    select: (dados) => (Array.isArray(dados) ? dados : []).map(notaDaApi),
  });
}

export function useCriarNota() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dados) => api.post(endpoints.notes.create, notaParaApi(dados)),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notas'] }),
  });
}

export function useAtualizarNota() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...dados }) => api.put(endpoints.notes.update(id), notaParaApi(dados)),
    onMutate: async ({ id, titulo, conteudo, categoriaId }) => {
      await qc.cancelQueries({ queryKey: ['notas'] });
      const anterior = qc.getQueryData(['notas']);
      // updatedAt entra junto: sem isso o card mostra a hora antiga até o
      // refetch do onSettled chegar. O servidor manda o valor definitivo depois.
      const agora = new Date();
      const local = new Date(agora.getTime() - agora.getTimezoneOffset() * 60_000)
        .toISOString().slice(0, 23); // LocalDateTime do backend: sem fuso
      qc.setQueryData(['notas'], (notas) =>
        (notas || []).map((n) => (n.id === id
          ? { ...n, titulo, conteudo, categoriaId: categoriaId === 'generico' ? null : categoriaId, atualizadaEm: local }
          : n)));
      return { anterior };
    },
    onError: (_e, _v, ctx) => { if (ctx?.anterior) qc.setQueryData(['notas'], ctx.anterior); },
    onSettled: () => qc.invalidateQueries({ queryKey: ['notas'] }),
  });
}

export function useRemoverNota() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id) => api.remove(endpoints.notes.remove(id)),
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: ['notas'] });
      const anterior = qc.getQueryData(['notas']);
      qc.setQueryData(['notas'], (notas) => (notas || []).filter((n) => n.id !== id));
      return { anterior };
    },
    onError: (_e, _v, ctx) => { if (ctx?.anterior) qc.setQueryData(['notas'], ctx.anterior); },
    onSettled: () => qc.invalidateQueries({ queryKey: ['notas'] }),
  });
}

// Fixar: o backend despina a anterior (só 1 fixada por usuário) e a ordem da
// lista muda — por isso NÃO há optimistic update; refetch traz o estado real.
export function useFixarNota() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id) => api.patch(endpoints.notes.pin(id)),
    onSettled: () => qc.invalidateQueries({ queryKey: ['notas'] }),
  });
}

// ── Rascunho do compositor (compartilhado com o app antigo) ──────────────────
// Mesma chave e mesmo encoding (JSON) do Store do vanilla: 'jdi.' + 'todo-notas'.
const KEY_RASCUNHO = 'jdi.todo-notas';
// O título do compositor é novo (não existia no vanilla), então vai em chave própria
// para não quebrar o formato da chave compartilhada acima.
const KEY_RASCUNHO_TITULO = 'jdi.todo-nota-titulo';

function ler(chave) {
  try {
    const bruto = localStorage.getItem(chave);
    return bruto === null ? '' : JSON.parse(bruto);
  } catch { return ''; }
}
function gravar(chave, txt) {
  try { localStorage.setItem(chave, JSON.stringify(txt)); } catch { /* cheio/indisponível */ }
}

export function lerRascunho() { return ler(KEY_RASCUNHO); }
export function gravarRascunho(txt) { gravar(KEY_RASCUNHO, txt); }

export function lerRascunhoTitulo() { return ler(KEY_RASCUNHO_TITULO); }
export function gravarRascunhoTitulo(txt) { gravar(KEY_RASCUNHO_TITULO, txt); }

export function limparRascunho() {
  localStorage.removeItem(KEY_RASCUNHO);
  localStorage.removeItem(KEY_RASCUNHO_TITULO);
}
