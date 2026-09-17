import { useEffect, useState } from 'react';

const COMPACT_TURNSTILE_QUERY = '(max-width: 332px)';

function tamanhoAtual() {
  if (typeof window === 'undefined' || !window.matchMedia) return 'flexible';
  return window.matchMedia(COMPACT_TURNSTILE_QUERY).matches ? 'compact' : 'flexible';
}

export function useResponsiveTurnstileSize() {
  const [size, setSize] = useState(tamanhoAtual);

  useEffect(() => {
    if (!window.matchMedia) return undefined;

    const media = window.matchMedia(COMPACT_TURNSTILE_QUERY);
    const atualizar = () => setSize(media.matches ? 'compact' : 'flexible');

    atualizar();
    media.addEventListener('change', atualizar);
    return () => media.removeEventListener('change', atualizar);
  }, []);

  return size;
}
