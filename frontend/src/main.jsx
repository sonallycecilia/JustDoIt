import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import App from '@/App';
import { iniciarTema } from '@/lib/theme';
import { ApiError, SESSION_EXPIRED_EVENT } from '@/api/client';

// Estilos globais do design system existente (copiados do front antigo)
import './styles/tokens.css';
import './styles/reset.css';
import './styles/global.css';
import './styles/components/button.css';
import './styles/components/auth.css';
import './styles/components/sidebar.css';
import './styles/components/card.css';
import './styles/components/badge.css';
import './styles/components/date-picker.css';
import './styles/components/legal-modal.css';
import './styles/components/development-chat.css';
import './styles/pages/landing.css';
import './styles/pages/landing-showcase.css';
import './styles/pages/home.css';
import './styles/pages/dashboard.css';
import './styles/pages/analytics.css';
import './styles/pages/settings.css';
import './styles/pages/onboarding.css';
import './styles/pages/todo.css';
import './styles/pages/task-detail.css';
import './styles/pages/notes.css';
import './styles/pages/calendar.css';
import './styles/pages/event-summary.css';

iniciarTema();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,          // evita refetch em toda troca de foco/página
      retry: (failureCount, error) => {
        // 4xx não se resolve repetindo (sem permissão / não existe); rede sim.
        if (error instanceof ApiError && error.status < 500) return false;
        return failureCount < 2;
      },
    },
  },
});

// O cliente HTTP emite este evento antes de redirecionar uma sessão expirada.
// Limpar aqui impede que tarefas/categorias antigas sobrevivam ao próximo login.
window.addEventListener(SESSION_EXPIRED_EVENT, () => queryClient.clear());

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
