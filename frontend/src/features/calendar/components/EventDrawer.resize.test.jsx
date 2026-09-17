import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import EventDrawer from '@/features/calendar/components/EventDrawer';

describe('painel lateral do calendário — redimensionamento', () => {
  beforeEach(() => localStorage.clear());

  it('expande ao arrastar a borda para a esquerda e salva a largura', () => {
    const { container } = render(<EventDrawer onClose={vi.fn()}>Detalhes</EventDrawer>);
    const divisor = screen.getByRole('separator', { name: 'Redimensionar painel do calendário' });

    fireEvent.pointerDown(divisor, { pointerId: 2, clientX: 500 });
    fireEvent.pointerMove(document, { pointerId: 2, clientX: 400 });
    fireEvent.pointerUp(document, { pointerId: 2, clientX: 400 });

    expect(container.querySelector('.cal-drawer')).toHaveStyle({ width: '620px' });
    expect(localStorage.getItem('jdi-calendar-drawer-width')).toBe('620px');
  });

  it('também pode ser expandido pelo teclado', () => {
    const { container } = render(<EventDrawer onClose={vi.fn()}>Detalhes</EventDrawer>);
    const divisor = screen.getByRole('separator', { name: 'Redimensionar painel do calendário' });

    fireEvent.keyDown(divisor, { key: 'ArrowLeft' });

    expect(container.querySelector('.cal-drawer')).toHaveStyle({ width: '536px' });
    expect(divisor).toHaveAttribute('aria-valuenow', '536');
  });
});
