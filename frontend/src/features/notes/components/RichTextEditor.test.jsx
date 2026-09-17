import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RichTextEditor from './RichTextEditor';
import { vi } from 'vitest';

describe('RichTextEditor', () => {
  it('exibe os controles de formatação visual', () => {
    render(<RichTextEditor value="" onChange={() => {}} />);

    expect(screen.getByRole('combobox', { name: 'Estilo do texto' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Tamanho da fonte' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Negrito' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Itálico' })).toBeInTheDocument();
    expect(screen.getByLabelText('Cor do texto')).toBeInTheDocument();
  });

  it('permite ativar a formatação antes de digitar', async () => {
    const user = userEvent.setup();
    render(<RichTextEditor value="" onChange={() => {}} />);
    const negrito = screen.getByRole('button', { name: 'Negrito' });

    await user.click(negrito);

    expect(negrito).toHaveAttribute('aria-pressed', 'true');
  });

  it('serializa a formatação sem tags HTML, conforme a validação do backend', async () => {
    const onChange = vi.fn();
    render(<RichTextEditor value="<p><strong>nota</strong></p>" onChange={onChange} />);

    const salvo = onChange.mock.calls.at(-1)[0];
    expect(salvo).toMatch(/^jdi:rich-text:/);
    expect(salvo).not.toContain('<');
    expect(JSON.parse(salvo.replace(/^jdi:rich-text:/, '')).type).toBe('doc');
  });
});
