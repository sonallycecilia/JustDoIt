// Página inicial (rota "/"): landing à esquerda, login à direita.
// Substitui o index.html vanilla, que ocupava a tela inteira e mandava o
// visitante para uma tela de login separada.
import { useNavigate } from 'react-router-dom';
import LandingPane from '@/features/landing/components/LandingPane';
import LoginForm from '@/features/auth/components/LoginForm';

export default function Home() {
  const navigate = useNavigate();

  return (
    <div className="home">
      <div className="home__landing">
        <LandingPane onCriarConta={() => navigate('/signup')} />
      </div>
      <div className="home__auth">
        <LoginForm />
      </div>
    </div>
  );
}
