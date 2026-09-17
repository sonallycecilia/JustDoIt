import { useEffect, useRef } from 'react';

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

function focusableElements(container) {
  return [...(container?.querySelectorAll(FOCUSABLE) ?? [])]
    .filter((element) => !element.closest('[aria-hidden="true"]'));
}

/** Gerencia foco inicial, trap de Tab, Escape e restauração ao fechar um diálogo. */
export function useModalA11y({ aberto, containerRef, initialFocusRef, onFechar, closeOnEscape = true }) {
  const onFecharRef = useRef(onFechar);
  const closeOnEscapeRef = useRef(closeOnEscape);
  onFecharRef.current = onFechar;
  closeOnEscapeRef.current = closeOnEscape;

  useEffect(() => {
    if (!aberto) return undefined;

    const previousFocus = document.activeElement;
    const focusTimer = setTimeout(() => {
      const initial = initialFocusRef?.current;
      const fallback = focusableElements(containerRef.current)[0];
      (initial && !initial.disabled ? initial : fallback)?.focus();
    }, 0);

    const onKeyDown = (event) => {
      if (event.key === 'Escape' && closeOnEscapeRef.current) {
        event.preventDefault();
        onFecharRef.current?.();
        return;
      }
      if (event.key !== 'Tab') return;

      const elements = focusableElements(containerRef.current);
      if (!elements.length) {
        event.preventDefault();
        containerRef.current?.focus();
        return;
      }

      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && (document.activeElement === first || !containerRef.current?.contains(document.activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    return () => {
      clearTimeout(focusTimer);
      document.removeEventListener('keydown', onKeyDown);
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, [aberto, containerRef, initialFocusRef]);
}
