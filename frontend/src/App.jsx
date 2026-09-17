import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Home from '@/features/landing/pages/Home';
import Signup from '@/features/auth/pages/Signup';
import Onboarding from '@/features/auth/pages/Onboarding';
import Todo from '@/features/tasks/pages/Todo';
import TaskDetail from '@/features/tasks/pages/TaskDetail';
import Anotacoes from '@/features/notes/pages/Anotacoes';
import Calendario from '@/features/calendar/pages/Calendario';
import VisaoGeral from '@/features/dashboard/pages/VisaoGeral';
import Analise from '@/features/dashboard/pages/Analise';
import Configuracoes from '@/features/settings/pages/Configuracoes';
import { estaLogado } from '@/api/session';
import { HistoryList } from '@/features/weekly-closure/pages/HistoryList';
import { HistoryDetail } from '@/features/weekly-closure/pages/HistoryDetail';
import { APP_ROUTES } from '@/appRoutes';

function RequireAuth({ children }) {
  const location = useLocation();
  if (!estaLogado()) {
    // A home é a própria tela de login (landing à esquerda, formulário à direita).
    return <Navigate to="/" replace state={{ de: location.pathname }} />;
  }
  return children;
}

export default function App() {
  const elements = {
    home: <Home />,
    login: <Navigate to="/" replace />,
    cadastro: <Signup />,
    onboarding: <Onboarding />,
    'visao-geral': <VisaoGeral />,
    tarefas: <Todo />,
    anotacoes: <Anotacoes />,
    calendario: <Calendario />,
    analise: <Analise />,
    configuracoes: <Configuracoes />,
    'nova-tarefa': <TaskDetail />,
    'detalhe-tarefa': <TaskDetail />,
    historico: <HistoryList />,
    'detalhe-historico': <HistoryDetail />,
  };

  return (
    <Routes>
      {APP_ROUTES.map((route) => (
        <Route
          key={route.id}
          path={route.path}
          element={route.private ? <RequireAuth>{elements[route.id]}</RequireAuth> : elements[route.id]}
        />
      ))}
      {/* Logado, a home do app é a Visão geral; deslogado, a landing + login. */}
      <Route path="*" element={<Navigate to={estaLogado() ? '/visao-geral' : '/'} replace />} />
    </Routes>
  );
}
