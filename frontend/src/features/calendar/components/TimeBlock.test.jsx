// Regressão do arraste no calendário: o bloco precisa POR dados no dataTransfer
// no dragstart. O Chrome inicia o arraste com o dataTransfer vazio, mas o
// Firefox não — sem setData o bloco simplesmente não sai do lugar.
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import TimeBlock from '@/features/calendar/components/TimeBlock';

// A cadeia de import puxa o hook de blocos (só por causa de fmtHora), que fala
// com o schedule-service. Nenhuma chamada acontece neste teste — o mock só evita
// carregar o cliente de verdade.
vi.mock('@/api/client', () => ({ api: {} }));

const EV = { id: 'bloco-1', ini: 9, fim: 10, titulo: 'Estudar cálculo', cat: 'estudos', prio: 'normal' };

function montar(ev = EV) {
  const onDragStart = vi.fn();
  const onDragEnd = vi.fn();
  render(<TimeBlock ev={ev} rowH={56} startHour={6} onDragStart={onDragStart} onDragEnd={onDragEnd} />);
  return { bloco: screen.getByTitle(ev.titulo), onDragStart, onDragEnd };
}

// jsdom não implementa DnD: o dataTransfer é injetado no evento à mão.
function dataTransferFalso() {
  return { setData: vi.fn(), effectAllowed: '' };
}

describe('TimeBlock — arraste', () => {
  it('é arrastável', () => {
    const { bloco } = montar();
    expect(bloco).toHaveAttribute('draggable', 'true');
  });

  it('põe dados no dataTransfer ao iniciar o arraste (o Firefox exige)', () => {
    const { bloco } = montar();
    const dataTransfer = dataTransferFalso();

    fireEvent.dragStart(bloco, { dataTransfer });

    expect(dataTransfer.setData).toHaveBeenCalledWith('text/plain', EV.id);
  });

  it('avisa quem move o bloco, com o evento arrastado', () => {
    const { bloco, onDragStart, onDragEnd } = montar();

    fireEvent.dragStart(bloco, { dataTransfer: dataTransferFalso() });
    expect(onDragStart).toHaveBeenCalledWith(EV);

    fireEvent.dragEnd(bloco);
    expect(onDragEnd).toHaveBeenCalled();
  });

  it('marca o arraste como "move" (e não "copy")', () => {
    const { bloco } = montar();
    const dataTransfer = dataTransferFalso();

    fireEvent.dragStart(bloco, { dataTransfer });

    expect(dataTransfer.effectAllowed).toBe('move');
  });

  it('mostra a estimativa da tarefa, já que a altura do bloco não a representa', () => {
    render(<TimeBlock ev={{ ...EV, id: 'task-1', estimadoMin: 180 }} rowH={56} startHour={6}
      onDragStart={vi.fn()} onDragEnd={vi.fn()} />);

    expect(screen.getByText('tempo 3h')).toBeInTheDocument();
  });

  it('omite a estimativa quando a tarefa não tem uma', () => {
    montar();

    expect(screen.queryByText(/^tempo /)).not.toBeInTheDocument();
  });

  it('não abre o modal no clique que encerra um arraste', () => {
    const onOpen = vi.fn();
    render(<TimeBlock ev={EV} rowH={56} startHour={6} onDragStart={vi.fn()} onDragEnd={vi.fn()} onOpen={onOpen} />);
    const bloco = screen.getByTitle(EV.titulo);

    fireEvent.dragStart(bloco, { dataTransfer: dataTransferFalso() });
    fireEvent.click(bloco);

    expect(onOpen).not.toHaveBeenCalled();
  });
});
