// Rascunho da tarefa em criação (/tasks/nova).
//
// Em modo edição o TaskEditor persiste cada mudança sozinho; em modo criação
// não há id para gravar, então nada saía do navegador até o clique em
// "Registrar tarefa". Quem preenchia a tarefa e saía pela sidebar, pelo "Ir
// para To Do" ou fechando a aba perdia tudo — sem aviso e sem volta.
//
// Este rascunho é cache local, não fonte da verdade: some assim que o POST
// /tasks conclui. Mesma ideia do rascunho do NoteComposer no To Do.
import { useCallback, useEffect, useRef } from 'react';

const CHAVE = 'jdi.rascunho.tarefa';
const ESPERA_MS = 500;

export function lerRascunho() {
  try {
    const bruto = localStorage.getItem(CHAVE);
    return bruto ? JSON.parse(bruto) : null;
  } catch {
    return null; // storage bloqueado ou conteúdo corrompido
  }
}

export function limparRascunho() {
  try { localStorage.removeItem(CHAVE); } catch { /* storage indisponível */ }
}

function gravarRascunho(dados) {
  try { localStorage.setItem(CHAVE, JSON.stringify(dados)); } catch { /* storage indisponível */ }
}

// Um rascunho sem título, descrição, nota nem subtarefa não tem o que
// restaurar (o título é obrigatório para criar). Vale também para quando o
// usuário apaga o que tinha escrito: o rascunho velho sai do storage.
function semConteudo(d) {
  return !d.titulo && !d.descricao && !d.nota && (d.subs || []).length === 0;
}

/**
 * `ativo` liga o rascunho só no modo criação — em edição o backend já é a
 * fonte da verdade e um rascunho paralelo só criaria divergência.
 *
 * `agendar(dados)` grava com debounce; `limpar()` apaga e cancela o que estiver
 * pendente (é o que a criação bem-sucedida chama, para o rascunho não voltar).
 */
export function useRascunhoTarefa(ativo) {
  const timer = useRef(null);
  const pendente = useRef(null);

  const gravarPendente = useCallback(() => {
    clearTimeout(timer.current);
    const dados = pendente.current;
    pendente.current = null;
    if (!dados) return;
    if (semConteudo(dados)) limparRascunho();
    else gravarRascunho(dados);
  }, []);

  const agendar = useCallback((dados) => {
    if (!ativo) return;
    pendente.current = dados;
    clearTimeout(timer.current);
    timer.current = setTimeout(gravarPendente, ESPERA_MS);
  }, [ativo, gravarPendente]);

  const limpar = useCallback(() => {
    clearTimeout(timer.current);
    // Zerar o pendente é o que impede a gravação de despedida abaixo de
    // ressuscitar o rascunho da tarefa que acabou de ser criada.
    pendente.current = null;
    limparRascunho();
  }, []);

  // Sair da página antes do debounce disparar não pode custar o que foi
  // digitado: o que estiver pendente é gravado no desmonte.
  useEffect(() => gravarPendente, [gravarPendente]);

  return { agendar, limpar, ler: lerRascunho };
}
