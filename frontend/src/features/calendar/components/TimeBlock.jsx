// Bloco de tempo alocado no calendário. Cor por categoria, barra de
// prioridade à esquerda, arrastável (drag-and-drop). Port do componente
// UMD do app antigo — globais viraram exports.
import { useRef } from 'react';
import { fmtHora } from '@/features/calendar/hooks/useTimeBlocks';
import { horas } from '@/lib/utils';

export const COR_PRIORIDADE = {
  urgent: 'var(--color-priority-urgent)',
  important: 'var(--color-priority-important)',
  normal: 'var(--color-priority-normal)',
  low: 'var(--color-priority-low)',
};

export const ICONES_MOD = {
  foco: 'M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 4a6 6 0 1 1-6 6 6 6 0 0 1 6-6zm0 4a2 2 0 1 0 2 2 2 2 0 0 0-2-2z',
  ciclo: 'M3 2v6h6 M21 12A9 9 0 0 0 6 5.3L3 8 M21 22v-6h-6 M3 12a9 9 0 0 0 15 6.7l3-2.7',
  tempo: 'M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2z M12 6v6l4 2',
  notas: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6',
};

export default function TimeBlock({ ev, rowH, startHour, catCor, onDragStart, onDragEnd, dragging, onOpen, onDrawer, onDelete, onToggle, layout }) {
  const wasDragged = useRef(false);
  const top = (ev.ini - startHour) * rowH;
  const height = (ev.fim - ev.ini) * rowH - 4;

  // Cor real da categoria (vem das categorias do usuário). Tinge o card
  // inteiro — fundo suave, borda e texto — enquanto a barrinha à esquerda
  // segue refletindo a prioridade. Sem cor resolvida, mantém a classe CSS.
  const style = { top: `${top}px`, height: `${height}px` };
  // Layout lado a lado quando há sobreposição de horários: em vez de ocupar a
  // largura toda (left/right no CSS), o bloco fica numa "coluna" da largura da
  // faixa de eventos que se sobrepõem. `layout` traz {left, width} em fração.
  if (layout) {
    style.left = `calc(${layout.left * 100}% + 3px)`;
    style.width = `calc(${layout.width * 100}% - 6px)`;
    style.right = 'auto';
  }
  if (catCor) {
    style.background = `color-mix(in srgb, ${catCor} var(--block-tint, 13%), var(--color-card))`;
    style.borderColor = `color-mix(in srgb, ${catCor} 45%, transparent)`;
    style.color = catCor;
  }

  return (
    <div
      className={`timeblock timeblock--${ev.cat} ${layout && layout.width < 0.9 ? 'timeblock--multi' : ''} ${dragging ? 'is-dragging' : ''} ${ev.done ? 'timeblock--done' : ''}`}
      style={style}
      draggable="true"
      // setData é obrigatório: o Firefox NÃO inicia o arraste quando o
      // dataTransfer fica vazio (o bloco não sai do lugar). O payload em si não
      // é lido no drop — a coluna usa o evento guardado em `arrastando` —, mas
      // precisa existir para o gesto começar.
      onDragStart={(e) => { e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', ev.id); onDragStart(ev); wasDragged.current = true; }}
      onDragEnd={() => { onDragEnd(); setTimeout(() => { wasDragged.current = false; }, 0); }}
      onClick={() => { if (!wasDragged.current && onOpen) onOpen(ev); }}
      title={ev.titulo}
    >
      <span className="timeblock__prio" style={{ background: COR_PRIORIDADE[ev.prio] }}></span>

      <div className="timeblock__row">
        {onToggle && ev.taskId && (
          <button
            className={`timeblock__check${ev.done ? ' is-on' : ''}`}
            onClick={(e) => { e.stopPropagation(); onToggle(ev); }}
            title={ev.done ? 'Reabrir tarefa' : 'Marcar como concluída'}
          >
            {/* Dimensão real vem do CSS (.timeblock__check svg), que encolhe
                em blocos estreitos — aqui só o strokeWidth precisa acompanhar. */}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6L9 17l-5-5" />
            </svg>
          </button>
        )}

        <div className="timeblock__content">
          <span className="timeblock__title">{ev.titulo}</span>
          <span className="timeblock__meta">
            <span>{ev.semHora ? 'Sem hora' : fmtHora(ev.ini)}</span>
            {/* Estimativa da tarefa. O evento virtual tem altura fixa, então sem
                este texto uma tarefa de 3h ficaria idêntica a uma de 15 min. */}
            {ev.estimadoMin > 0 && (
              <span className="timeblock__est" title="Tempo estimado da tarefa">
                tempo {horas(ev.estimadoMin / 60)}
              </span>
            )}
            {ev.mod && ICONES_MOD[ev.mod] && (
              <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                {ICONES_MOD[ev.mod].split(' M').map((d, i) => <path key={i} d={(i ? 'M' : '') + d} />)}
              </svg>
            )}
          </span>
        </div>

        {onDrawer && (
          <button
            className="timeblock__arrow"
            onClick={(e) => { e.stopPropagation(); onDrawer(ev); }}
            title="Abrir painel lateral"
          >
            <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="m9 18 6-6-6-6" />
            </svg>
          </button>
        )}
        {onDelete && (
          <button
            className="timeblock__trash"
            onClick={(e) => { e.stopPropagation(); onDelete(ev); }}
            title="Excluir tarefa"
          >
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 6h18" />
              <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
            </svg>
          </button>
        )}
      </div>
    </div>
  );
}
