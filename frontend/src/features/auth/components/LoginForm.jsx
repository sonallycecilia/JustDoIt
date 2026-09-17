// Formulário de login — extraído da página /login para ser reusado no lado
// direito da home dividida. Toda a lógica de autenticação vive aqui.
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import Ic, { ICONS } from '@/components/Ic';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { useIniciarSessao } from '@/features/auth/hooks/useSessao';
import { useResponsiveTurnstileSize } from '@/features/auth/hooks/useResponsiveTurnstileSize';
import { TURNSTILE_LOCAL_TEST_MODE, TURNSTILE_SITE_KEY } from '@/lib/turnstile';
import { Turnstile } from '@marsidev/react-turnstile';

export default function LoginForm() {
  const navigate = useNavigate();
  const iniciarSessao = useIniciarSessao();
  const turnstileSize = useResponsiveTurnstileSize();
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [verSenha, setVerSenha] = useState(false);
  // Decide o prazo do refresh token no backend. A conta ativa sempre fica
  // isolada por aba; quando marcado, uma cópia também sobrevive ao navegador.
  const [lembrar, setLembrar] = useState(true);

  const [turnstileToken, setTurnstileToken] = useState(null);
  const [erroForm, setErroForm] = useState('');

  const login = useMutation({
    mutationFn: (token) => api.post(endpoints.auth.login, {
      email: email.trim(),
      password: senha,
      rememberMe: lembrar,
    }, {
      headers: {
        'X-Turnstile-Token': token
      }
    }),
    onSuccess: (res) => {
      // Entrar descarta o cache da sessão anterior (ver useSessao): sem isso,
      // trocar de conta na mesma aba mostrava os dados do usuário antigo.
      iniciarSessao({ accessToken: res.accessToken, refreshToken: res.refreshToken }, { lembrar });
      navigate('/visao-geral', { replace: true });
    },
    onError: (e) => setErroForm(e.message || 'E-mail ou senha incorretos.'),
  });

  function enviar(e) {
    e.preventDefault();
    setErroForm('');
    if (!email.trim() || !senha) return;
    if (!turnstileToken) {
      setErroForm('Aguarde a verificação de segurança ser concluída.');
      return;
    }
    login.mutate(turnstileToken);
  }

  return (
    <div className="auth__card">
      <div className="auth__head">
        <div className="auth__eyebrow">Bem-vindo de volta</div>
        <h1 className="auth__title">Entrar</h1>
        <p className="auth__sub">Novo por aqui? <Link to="/signup">Criar uma conta</Link></p>
      </div>

      <form className="auth__form" onSubmit={enviar} noValidate>
        <div className="field">
          <label className="field__label" htmlFor="email">E-mail</label>
          <div className="field__control">
            <Ic d={ICONS.mail} />
            <input
              id="email"
              type="email"
              placeholder="voce@exemplo.com"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
        </div>

        <div className="field">
          <label className="field__label" htmlFor="senha">Senha</label>
          <div className="field__control">
            <Ic d={ICONS.lock} />
            <input
              id="senha"
              type={verSenha ? 'text' : 'password'}
              placeholder="Sua senha"
              autoComplete="current-password"
              required
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
            />
            <button
              type="button"
              className="field__eye"
              onClick={() => setVerSenha((v) => !v)}
              aria-label={verSenha ? 'Ocultar senha' : 'Mostrar senha'}
            >
              <Ic d={ICONS.eye} />
            </button>
          </div>
        </div>

        <div className="auth__rowend">
          <label className="checkline">
            <input
              type="checkbox"
              checked={lembrar}
              onChange={(e) => setLembrar(e.target.checked)}
            /> Manter conectado
          </label>
          <a className="auth__forgot">Esqueceu a senha?</a>
        </div>

        {(login.isError || erroForm) && (
          <span className="field__error">{erroForm || login.error?.message || 'E-mail ou senha incorretos.'}</span>
        )}

        <div className="auth__turnstile">
          <Turnstile
            siteKey={TURNSTILE_SITE_KEY}
            onSuccess={(token) => {
              console.log("Turnstile gerado com sucesso:", token);
              setTurnstileToken(token);
            }}
            onExpire={() => {
              console.log("Turnstile expirou");
              setTurnstileToken(null);
            }}
            onError={(err) => console.error('TURNSTILE ERROR:', err)}
            options={{ theme: 'light', size: TURNSTILE_LOCAL_TEST_MODE ? 'invisible' : turnstileSize }}
          />
        </div>

        <button type="submit" className="btn btn--primary btn--lg btn--full" disabled={login.isPending}>
          {login.isPending ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </div>
  );
}
