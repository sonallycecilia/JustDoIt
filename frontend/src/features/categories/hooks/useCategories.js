import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';

// "Genérico" é a categoria padrão de todo usuário e NÃO existe no backend:
// é representada por categoryId null nas tarefas.
export const CAT_GENERICO = { id: 'generico', nome: 'Genérico', cor: 'var(--color-cat-generico)' };

function mapear(c) {
  return { id: c.id, nome: c.name, cor: c.color || CAT_GENERICO.cor };
}

// Lista de categorias do usuário, sempre com "Genérico" no topo.
export function useCategorias() {
  return useQuery({
    queryKey: ['categorias'],
    // A categoria Genérico é local (categoryId null) e sempre existe, mesmo
    // quando o backend ainda está carregando ou está temporariamente fora.
    // Sem este estado inicial, o editor caía em "Todas as categorias" e abria
    // um dropdown vazio, embora esse modo nem permita selecionar "Todas".
    initialData: [CAT_GENERICO],
    initialDataUpdatedAt: 0,
    queryFn: async () => {
      const cats = await api.get(endpoints.categories.list);
      const resto = (Array.isArray(cats) ? cats : [])
        .map(mapear)
        .filter((c) => c.nome !== CAT_GENERICO.nome);
      return [CAT_GENERICO, ...resto];
    },
  });
}

export function useCriarCategoria() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ nome, cor }) => api.post(endpoints.categories.create, { name: nome, color: cor }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categorias'] }),
  });
}

export function useAtualizarCategoria() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, nome, cor }) => api.put(endpoints.categories.update(id), { name: nome, color: cor }),
    // As tarefas carregam o categoryId, não o nome: renomear muda o rótulo
    // exibido, então a lista de tarefas também precisa ser reprocessada.
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categorias'] });
      qc.invalidateQueries({ queryKey: ['tarefas'] });
      qc.invalidateQueries({ queryKey: ['notas'] });
    },
  });
}

// DELETE /categories/{id} zera o category_id das tarefas da categoria no
// backend — elas voltam para "Genérico" sozinhas quando a lista é recarregada.
export function useRemoverCategoria() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id) => api.remove(endpoints.categories.remove(id)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categorias'] });
      qc.invalidateQueries({ queryKey: ['tarefas'] });
      qc.invalidateQueries({ queryKey: ['notas'] });
    },
  });
}

// Resolve nome/cor de uma categoria a partir do categoryId do backend.
export function categoriaPorId(categorias, id) {
  if (!id) return CAT_GENERICO;
  return (categorias || []).find((c) => c.id === id) || CAT_GENERICO;
}
