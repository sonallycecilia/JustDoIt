import { useEffect, useRef, useState } from 'react';
import Ic, { ICONS } from '@/components/Ic';
import { useSendSupportMessage, useSupportMessages } from '@/features/feedback/hooks/useDevelopmentChat';
import { useModalA11y } from '@/hooks/useModalA11y';

const MESSAGE_LIMIT = 4000;
const PAGE_URL_LIMIT = 2048;
const USER_AGENT_LIMIT = 512;

const SENDER_LABELS = {
  USER: 'Você',
  DEVELOPMENT: 'Desenvolvimento',
  AI: 'Assistente',
};

function formatDate(value) {
  if (!value) return '';
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

export default function DevelopmentChatModal({ aberto, onFechar }) {
  const [message, setMessage] = useState('');
  const [validationError, setValidationError] = useState('');
  const [sendError, setSendError] = useState('');
  const [sentStatus, setSentStatus] = useState('');
  const dialogRef = useRef(null);
  const messageRef = useRef(null);
  const historyRef = useRef(null);
  const supportMessages = useSupportMessages(aberto);
  const sendMessage = useSendSupportMessage();

  useModalA11y({
    aberto,
    containerRef: dialogRef,
    initialFocusRef: messageRef,
    onFechar,
    closeOnEscape: !sendMessage.isPending,
  });

  useEffect(() => {
    if (!aberto) return;
    setValidationError('');
    setSendError('');
    setSentStatus('');
    sendMessage.reset();
  }, [aberto]);

  useEffect(() => {
    if (!aberto || !supportMessages.data?.length) return;
    if (historyRef.current) historyRef.current.scrollTop = historyRef.current.scrollHeight;
  }, [aberto, supportMessages.data]);

  if (!aberto) return null;

  function close() {
    if (!sendMessage.isPending) onFechar();
  }

  function send(event) {
    event.preventDefault();
    const content = message.trim();
    if (!content) {
      setValidationError('Escreva uma mensagem antes de enviar.');
      messageRef.current?.focus();
      return;
    }

    setValidationError('');
    setSendError('');
    setSentStatus('');
    sendMessage.mutate({
      content,
      pageUrl: window.location.href.slice(0, PAGE_URL_LIMIT),
      userAgent: window.navigator.userAgent.slice(0, USER_AGENT_LIMIT),
    }, {
      onSuccess: () => {
        setMessage('');
        setSentStatus('Mensagem enviada.');
        messageRef.current?.focus();
      },
      onError: () => setSendError('Não foi possível enviar a mensagem. Verifique sua conexão e tente novamente.'),
    });
  }

  const messages = supportMessages.data || [];

  return (
    <div className="development-chat">
      <div className="development-chat__backdrop" onClick={close} />
      <section
        ref={dialogRef}
        className="development-chat__card"
        role="dialog"
        aria-labelledby="development-chat-title"
        tabIndex={-1}
      >
        <header className="development-chat__head">
          <div>
            <h2 id="development-chat-title">Falar com o desenvolvimento</h2>
            <p>Canal interno do JustDoIt</p>
          </div>
          <button className="development-chat__close" type="button" onClick={close} disabled={sendMessage.isPending} aria-label="Fechar">
            <Ic d={ICONS.close} />
          </button>
        </header>

        <div ref={historyRef} className="development-chat__history" aria-live="polite">
          {supportMessages.isPending && <p className="development-chat__state">Carregando conversa…</p>}
          {supportMessages.isError && (
            <div className="development-chat__state development-chat__state--error" role="alert">
              <p>Não foi possível carregar a conversa.</p>
              <button className="btn btn--secondary btn--sm" type="button" onClick={() => supportMessages.refetch()}>Tentar novamente</button>
            </div>
          )}
          {!supportMessages.isPending && !supportMessages.isError && messages.length === 0 && (
            <div className="development-chat__empty">
              <span className="development-chat__empty-icon"><Ic d={ICONS.chat} /></span>
              <p>Comece a conversa enviando uma dúvida, sugestão ou problema.</p>
            </div>
          )}
          {!supportMessages.isError && messages.map((item) => (
            <article
              key={item.id}
              className={`development-chat__message development-chat__message--${item.sender === 'USER' ? 'user' : 'team'}`}
            >
              <div className="development-chat__message-meta">
                <strong>{SENDER_LABELS[item.sender] || item.sender}</strong>
                <time dateTime={item.createdAt}>{formatDate(item.createdAt)}</time>
              </div>
              <p>{item.content}</p>
            </article>
          ))}
        </div>

        <form className="development-chat__composer" noValidate onSubmit={send}>
          <label htmlFor="development-chat-message">Mensagem</label>
          <textarea
            ref={messageRef}
            id="development-chat-message"
            value={message}
            maxLength={MESSAGE_LIMIT}
            required
            disabled={sendMessage.isPending}
            aria-invalid={Boolean(validationError)}
            aria-describedby="development-chat-feedback development-chat-counter"
            placeholder="Escreva sua mensagem…"
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                event.currentTarget.form?.requestSubmit();
              }
            }}
            onChange={(event) => {
              setMessage(event.target.value);
              if (validationError) setValidationError('');
              if (sentStatus) setSentStatus('');
            }}
          />
          <div className="development-chat__feedback">
            <span id="development-chat-feedback" className={validationError || sendError ? 'development-chat__error' : 'development-chat__sent'}>
              {validationError || sendError || sentStatus}
            </span>
            <span id="development-chat-counter" className="development-chat__counter">{message.length}/{MESSAGE_LIMIT}</span>
          </div>
          <div className="development-chat__actions">
            <button className="btn btn--primary btn--sm" type="submit" disabled={sendMessage.isPending}>
              {sendMessage.isPending ? 'Enviando…' : sendError ? 'Tentar novamente' : 'Enviar'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
