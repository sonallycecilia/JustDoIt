import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Ic, { ICONS } from '@/components/Ic';
import {
  useDeleteNotification,
  useMarkNotificationRead,
  useNotifications,
} from '@/features/notifications/hooks/useNotifications';

const SHOWN_KEY = 'jdi-browser-notifications-shown';

function shownIds() {
  try {
    return new Set(JSON.parse(sessionStorage.getItem(SHOWN_KEY) || '[]'));
  } catch {
    return new Set();
  }
}

function rememberShown(ids) {
  sessionStorage.setItem(SHOWN_KEY, JSON.stringify([...ids].slice(-200)));
}

function formattedDate(value) {
  if (!value) return '';
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

function temporaryDownloadUrl(item) {
  if (item.type !== 'EXPORT_READY') return null;
  return item.message?.match(/https?:\/\/\S+/)?.[0] || null;
}

export default function NotificationCenter() {
  const navigate = useNavigate();
  const rootRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const { data: notifications = [], isLoading } = useNotifications();
  const markRead = useMarkNotificationRead();
  const deleteNotification = useDeleteNotification();
  const unreadCount = useMemo(() => notifications.filter((item) => !item.read).length, [notifications]);

  useEffect(() => {
    if (!open) return undefined;
    const close = (event) => {
      if (event.key === 'Escape' || (event.type === 'mousedown' && !rootRef.current?.contains(event.target))) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', close);
    };
  }, [open]);

  useEffect(() => {
    if (typeof window.Notification === 'undefined'
      || window.Notification.permission !== 'granted'
      || document.visibilityState !== 'visible') return;

    const ids = shownIds();
    const newReminders = notifications.filter((item) => (
      ['TASK_REMINDER', 'EXPORT_READY'].includes(item.type) && !item.read && !ids.has(item.id)
    ));

    for (const item of newReminders) {
      const browserNotification = new window.Notification(item.title, {
        body: item.message,
        tag: `justdoit-task-${item.taskId || item.id}`,
        renotify: true,
      });
      browserNotification.onclick = () => {
        window.focus();
        const downloadUrl = temporaryDownloadUrl(item);
        if (downloadUrl) window.location.assign(downloadUrl);
        else if (item.taskId) navigate(`/tasks/${item.taskId}`);
        browserNotification.close();
      };
      ids.add(item.id);
    }
    if (newReminders.length > 0) rememberShown(ids);
  }, [navigate, notifications]);

  async function toggle() {
    setOpen((value) => !value);
    if (typeof window.Notification !== 'undefined' && window.Notification.permission === 'default') {
      await window.Notification.requestPermission().catch(() => 'denied');
    }
  }

  function openAlert(item) {
    if (!item.read) markRead.mutate(item.id);
    setOpen(false);
    const downloadUrl = temporaryDownloadUrl(item);
    if (downloadUrl) window.location.assign(downloadUrl);
    else if (item.taskId) navigate(`/tasks/${item.taskId}`);
  }

  function deleteAlert(item) {
    setDeleteError('');
    deleteNotification.mutate(item.id, {
      onError: () => setDeleteError('Não foi possível excluir o alerta. Tente novamente.'),
    });
  }

  return (
    <div className="notification-center" ref={rootRef}>
      <button
        className="btn-icon notification-center__trigger"
        type="button"
        aria-label={`Alertas${unreadCount ? `: ${unreadCount} não lidos` : ''}`}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={toggle}
      >
        <Ic d={ICONS.bell} />
        {unreadCount > 0 && <span className="notification-center__badge">{Math.min(unreadCount, 99)}</span>}
      </button>

      {open && (
        <section className="notification-panel" role="dialog" aria-label="Todos os alertas">
          <header className="notification-panel__header">
            <div>
              <strong>Alertas</strong>
              <span>{unreadCount ? `${unreadCount} não lido${unreadCount === 1 ? '' : 's'}` : 'Tudo em dia'}</span>
            </div>
            <button type="button" aria-label="Fechar alertas" onClick={() => setOpen(false)}>
              <Ic d={ICONS.close} size={16} />
            </button>
          </header>
          <div className="notification-panel__list">
            {deleteError && (
              <p className="notification-panel__error" role="alert">{deleteError}</p>
            )}
            {isLoading && <p className="notification-panel__empty">Carregando alertas…</p>}
            {!isLoading && notifications.length === 0 && (
              <p className="notification-panel__empty">Nenhum alerta no momento.</p>
            )}
            {notifications.map((item) => (
              <div
                key={item.id}
                className={`notification-item ${item.read ? '' : 'is-unread'}`}
              >
                <button
                  type="button"
                  className="notification-item__open"
                  onClick={() => openAlert(item)}
                >
                  <span className="notification-item__icon"><Ic d={ICONS.bell} size={15} /></span>
                  <span className="notification-item__content">
                    <strong>{item.title}</strong>
                    <span>{item.message}</span>
                    <time dateTime={item.createdAt}>{formattedDate(item.createdAt)}</time>
                  </span>
                  {!item.read && <span className="notification-item__dot" aria-label="Não lido" />}
                </button>
                <button
                  type="button"
                  className="notification-item__delete"
                  aria-label={`Excluir alerta: ${item.title}`}
                  title="Excluir alerta"
                  disabled={deleteNotification.isPending && deleteNotification.variables === item.id}
                  onClick={() => deleteAlert(item)}
                >
                  <Ic d={ICONS.trash} size={16} />
                </button>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
