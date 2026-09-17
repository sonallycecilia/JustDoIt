import { useState } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ConfirmModal from '@/components/ConfirmModal';

function Harness() {
  const [aberto, setAberto] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setAberto(true)}>Abrir confirmação</button>
      <ConfirmModal
        aberto={aberto}
        titulo="Excluir tarefa"
        onConfirmar={() => {}}
        onFechar={() => setAberto(false)}
      >A tarefa será removida.</ConfirmModal>
    </>
  );
}

describe('acessibilidade do diálogo de confirmação', () => {
  it('move o foco ao abrir e restaura no acionador ao fechar com Escape', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const opener = screen.getByRole('button', { name: 'Abrir confirmação' });

    await user.click(opener);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Excluir' })).toHaveFocus());
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });

  it('aprisiona Tab e Shift+Tab dentro do diálogo', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: 'Abrir confirmação' }));

    const cancelar = screen.getByRole('button', { name: 'Cancelar' });
    const excluir = screen.getByRole('button', { name: 'Excluir' });
    await waitFor(() => expect(excluir).toHaveFocus());

    await user.tab();
    expect(cancelar).toHaveFocus();
    await user.tab({ shift: true });
    expect(excluir).toHaveFocus();
  });
});
