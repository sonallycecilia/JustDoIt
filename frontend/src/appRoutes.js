export const AUDIT_TASK_ID = '00000000-0000-0000-0000-000000000001';
export const AUDIT_CYCLE_ID = '00000000-0000-0000-0000-000000000002';

// Este manifesto é consumido tanto pelo roteador quanto pela auditoria axe.
// Toda rota de tela deve entrar aqui; redirecionamentos ficam com auditPath nulo.
export const APP_ROUTES = [
  { id: 'home', path: '/', private: false, auditPath: '/' },
  { id: 'login', path: '/login', private: false, auditPath: null },
  { id: 'cadastro', path: '/signup', private: false, auditPath: '/signup' },
  { id: 'onboarding', path: '/onboarding', private: true, auditPath: '/onboarding' },
  { id: 'visao-geral', path: '/visao-geral', private: true, auditPath: '/visao-geral' },
  { id: 'tarefas', path: '/todo', private: true, auditPath: '/todo' },
  { id: 'anotacoes', path: '/anotacoes', private: true, auditPath: '/anotacoes' },
  { id: 'calendario', path: '/calendario', private: true, auditPath: '/calendario' },
  { id: 'analise', path: '/analise', private: true, auditPath: '/analise' },
  { id: 'configuracoes', path: '/configuracoes', private: true, auditPath: '/configuracoes' },
  { id: 'nova-tarefa', path: '/tasks/nova', private: true, auditPath: '/tasks/nova' },
  { id: 'detalhe-tarefa', path: '/tasks/:id', private: true, auditPath: `/tasks/${AUDIT_TASK_ID}` },
  { id: 'historico', path: '/history', private: true, auditPath: '/history' },
  { id: 'detalhe-historico', path: '/history/:cycleId', private: true, auditPath: `/history/${AUDIT_CYCLE_ID}` },
];

export const AUDITABLE_ROUTES = APP_ROUTES
  .filter((route) => route.auditPath)
  .map(({ id, auditPath, private: isPrivate }) => ({
    name: id,
    path: auditPath,
    private: isPrivate,
  }));
