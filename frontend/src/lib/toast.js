// Toast imperativo no rodapé da tela (port de core/utils.js do app antigo).
// Fora do React de propósito: erros de mutation disparam de qualquer lugar
// sem precisar de provider/contexto. Estilos em styles/global.css (.jdi-toast).
let _toastEl = null;

export function toast(mensagem, tipo) {
  if (!_toastEl) {
    _toastEl = document.createElement('div');
    document.body.appendChild(_toastEl);
  }
  _toastEl.textContent = mensagem;
  _toastEl.className = `jdi-toast jdi-toast--${tipo || 'error'} is-visible`;
  clearTimeout(_toastEl._timer);
  _toastEl._timer = setTimeout(() => _toastEl.classList.remove('is-visible'), 4000);
}
