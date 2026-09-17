import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import Ic, { ICONS, Mark } from '@/components/Ic';
import DatePicker from '@/components/DatePicker';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { useIniciarSessao } from '@/features/auth/hooks/useSessao';
import { useResponsiveTurnstileSize } from '@/features/auth/hooks/useResponsiveTurnstileSize';
import { alternarTema } from '@/lib/theme';
import { capitalizarNome, dataIso } from '@/lib/utils';
import { PasswordStrength } from '../components/PasswordStrength';
import { validarSenha } from '@/lib/senha';
import { TURNSTILE_LOCAL_TEST_MODE, TURNSTILE_SITE_KEY } from '@/lib/turnstile';
import { Turnstile } from '@marsidev/react-turnstile';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function Signup() {
  const navigate = useNavigate();
  const iniciarSessao = useIniciarSessao();
  const turnstileSize = useResponsiveTurnstileSize();
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [nascimento, setNascimento] = useState(null);
  const [nascAberto, setNascAberto] = useState(false);
  const [senha, setSenha] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [verSenha, setVerSenha] = useState(false);
  const [verConfirmar, setVerConfirmar] = useState(false);
  const [termos, setTermos] = useState(false);
  const [erroSenha, setErroSenha] = useState(false);
  const [erroForm, setErroForm] = useState('');

  const [emailStatus, setEmailStatus] = useState('idle');
  const emailVerificado = useRef('');

  const [turnstileToken, setTurnstileToken] = useState(null);

  useEffect(() => {
    const atual = email.trim();
    if (!atual) { setEmailStatus('idle'); emailVerificado.current = ''; return; }
    if (!EMAIL_RE.test(atual)) { setEmailStatus('invalid'); emailVerificado.current = atual; return; }
    setEmailStatus('idle');
    const timer = setTimeout(() => {
      emailVerificado.current = atual;
      setEmailStatus('checking');
      api.get(endpoints.auth.checkEmail(atual))
        .then((res) => {
          if (emailVerificado.current !== atual) return;
          if (res?.registered) setEmailStatus('registered');
          else if (res?.deliverable === false) setEmailStatus('invalid');
          else setEmailStatus('ok');
        })
        .catch(() => {
          if (emailVerificado.current === atual) setEmailStatus('error');
        });
    }, 500);
    return () => clearTimeout(timer);
  }, [email]);

  const cadastro = useMutation({
    mutationFn: (token) => api.post(endpoints.auth.register, {
      name: capitalizarNome(nome),
      email: email.trim(),
      password: senha,
      birthDate: dataIso(nascimento),
    }, {
      headers: {
        'X-Turnstile-Token': token
      }
    }),
    onSuccess: (res) => {
      iniciarSessao(
        { accessToken: res.accessToken, refreshToken: res.refreshToken, name: capitalizarNome(nome) },
        { lembrar: false },
      );
      navigate('/onboarding', { replace: true });
    },
    onError: (e) => setErroForm(e.message || 'Erro ao criar conta. Tente novamente.'),
  });

  function enviar(e) {
    e.preventDefault();
    setErroForm('');
    const validacaoSenha = validarSenha(senha);

    if (!nome.trim()) { setErroForm('Informe seu nome.'); return; }
    if (!EMAIL_RE.test(email.trim())) { setErroForm('E-mail inválido. Use o formato voce@exemplo.com.'); return; }
    if (emailStatus === 'registered') { setErroForm('Este email já está em uso.'); return; }
    if (emailStatus === 'invalid') { setErroForm('Não encontramos esse provedor de email. Verifique se digitou corretamente.'); return; }
    if (!nascimento) { setErroForm('Informe sua data de nascimento.'); return; }
    if (!validacaoSenha.isValid) { 
      setErroForm('Sua senha ainda possui pendências. Verifique os requisitos marcados em vermelho acima.'); 
      return; 
    }
    if (senha !== confirmar) { setErroSenha(true); return; }
    if (!termos) { setErroForm('É preciso aceitar os Termos de Uso.'); return; }
    if (!turnstileToken) { setErroForm('Aguarde a verificação de segurança (Turnstile) ser concluída.'); return; }

    cadastro.mutate(turnstileToken); 
  }

  const statusEmail = {
    checking: { texto: 'Verificando e-mail…', classe: 'field__error--muted' },
    registered: { texto: 'Este email já está em uso.', classe: '' },
    invalid: email.trim() && !EMAIL_RE.test(email.trim())
      ? { texto: 'E-mail inválido. Use o formato voce@exemplo.com.', classe: '' }
      : { texto: 'Não encontramos esse provedor de email. Verifique se digitou corretamente.', classe: '' },
  }[emailStatus];

  return (
    <>
      <button className="btn-icon auth__theme" onClick={alternarTema} aria-label="Alternar tema">
        <Ic d={ICONS.moon} />
      </button>

      <div className="auth auth--signup">
        <aside className="auth__aside">
          <div className="auth__brand"><span className="auth__mark" aria-hidden="true"><Mark size={60} /></span></div>
          <div className="auth__quote">
            <h2>Comece <em>agora.</em></h2>
            <p>Crie sua conta gratuitamente e descubra como um dia organizado pode ser leve.</p>
          </div>
          <ul className="auth__checklist">
            <li><Ic d={ICONS.check} strokeWidth={2.2} /> Sem cartão de crédito</li>
            <li><Ic d={ICONS.check} strokeWidth={2.2} /> Configuração em 2 minutos</li>
          </ul>
          <div className="auth__bottom">
            <div className="auth__foot">Gerenciador de Tarefas</div>
            <div className="auth__brand"><span className="auth__word">JustDoIt</span></div>
          </div>
        </aside>

        <main className="auth__main">
          <div className="auth__card">
            <div className="auth__head">
              <div className="auth__eyebrow">Vamos começar</div>
              <h1 className="auth__title">Criar conta</h1>
              <p className="auth__sub">Já tem uma conta? <Link to="/">Entrar</Link></p>
            </div>

            <form className="auth__form" onSubmit={enviar} noValidate>
              <div className="field">
                <label className="field__label" htmlFor="nome">Nome completo</label>
                <div className="field__control">
                  <Ic d={ICONS.user} />
                  <input id="nome" type="text" placeholder="Seu nome" autoComplete="name" required
                    value={nome} onChange={(e) => setNome(e.target.value)} />
                </div>
              </div>

              <div className="field">
                <label className="field__label" htmlFor="email">E-mail</label>
                <div className="field__control">
                  <Ic d={ICONS.mail} />
                  <input id="email" type="email" placeholder="voce@exemplo.com" autoComplete="email" required
                    value={email} onChange={(e) => setEmail(e.target.value)} />
                </div>
                {statusEmail && (
                  <span className={`field__error ${statusEmail.classe}`}>{statusEmail.texto}</span>
                )}
              </div>

              <div className="field">
                <label className="field__label">Data de nascimento</label>
                <div className="date-pick">
                  <button
                    className={`field__control field__date-btn ${nascAberto ? 'is-open' : ''}`}
                    type="button"
                    onClick={() => setNascAberto((v) => !v)}
                  >
                    <Ic d={ICONS.calendar} size={18} style={{ flex: 'none', color: 'var(--color-text-muted)' }} />
                    <span className={`field__date-val ${nascimento ? 'is-set' : ''}`}>
                      {nascimento
                        ? `${String(nascimento.getDate()).padStart(2, '0')}/${String(nascimento.getMonth() + 1).padStart(2, '0')}/${nascimento.getFullYear()}`
                        : 'Selecionar data…'}
                    </span>
                    <Ic d={ICONS.chevron} size={14} className="date-pick__chevron" strokeWidth={2.5} />
                  </button>
                  <DatePicker
                    aberto={nascAberto}
                    onFechar={() => setNascAberto(false)}
                    onSelect={setNascimento}
                    selecionada={nascimento}
                    modoNascimento
                  />
                </div>
              </div>

              <div className="field">
                <label className="field__label" htmlFor="senha">Senha</label>
                <div className="field__control">
                  <Ic d={ICONS.lock} />
                  <input id="senha" type={verSenha ? 'text' : 'password'} placeholder="Mínimo 8 caracteres"
                    autoComplete="new-password" required minLength={8}
                    value={senha} onChange={(e) => setSenha(e.target.value)} />
                  <button type="button" className="field__eye" onClick={() => setVerSenha((v) => !v)}
                    aria-label={verSenha ? 'Ocultar senha' : 'Mostrar senha'}>
                    <Ic d={ICONS.eye} />
                  </button>
                </div>
                <PasswordStrength password={senha} />
              </div>

              <div className="field">
                <label className="field__label" htmlFor="confirmar">Confirmar senha</label>
                <div className="field__control">
                  <Ic d={ICONS.lock} />
                  <input id="confirmar" type={verConfirmar ? 'text' : 'password'} placeholder="Repita a senha"
                    autoComplete="new-password" required
                    value={confirmar}
                    onChange={(e) => { setConfirmar(e.target.value); setErroSenha(false); }} />
                  <button type="button" className="field__eye" onClick={() => setVerConfirmar((v) => !v)}
                    aria-label={verConfirmar ? 'Ocultar senha' : 'Mostrar senha'}>
                    <Ic d={ICONS.eye} />
                  </button>
                </div>
                {erroSenha && <span className="field__error">As senhas não coincidem.</span>}
              </div>

              <label className="checkline">
                <input type="checkbox" checked={termos} onChange={(e) => setTermos(e.target.checked)} />
                <span>Aceito os <a href="#">Termos de Uso</a> e a <a href="#">Política de Privacidade</a></span>
              </label>

              {erroForm && <span className="field__error">{erroForm}</span>}
              
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
                  options={{ theme: 'light', size: TURNSTILE_LOCAL_TEST_MODE ? 'invisible' : turnstileSize }}
                />
              </div>

              <button 
                type="submit" 
                className="btn btn--primary btn--lg btn--full" 
                disabled={cadastro.isPending || (senha.length > 0 && !validarSenha(senha).isValid)}
              >
                {cadastro.isPending ? 'Criando conta…' : 'Criar conta'}
              </button>
            </form>
          </div>
        </main>
      </div>
    </>
  );
}
