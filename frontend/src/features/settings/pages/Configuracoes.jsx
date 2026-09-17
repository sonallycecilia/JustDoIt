// Aba "Configurações": conta (foto, nome, email, senha), aparência,
// preferências, categorias, dados e exclusão de conta.
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Ic, { ICONS } from '@/components/Ic';
import Sidebar from '@/components/Sidebar';
import CategoryModal from '@/features/categories/components/CategoryModal';
import ConfirmModal from '@/components/ConfirmModal';
import ExportModal from '@/features/settings/components/ExportModal';
import { useExportarDados } from '@/features/settings/hooks/useExportarDados';
import {
  CAT_GENERICO,
  useCategorias,
  useCriarCategoria,
  useRemoverCategoria,
} from '@/features/categories/hooks/useCategories';
import { useAtualizarConta, useConta, useExcluirConta, traduzErroConta } from '@/features/auth/hooks/useConta';
import { useTarefas } from '@/features/tasks/hooks/useTasks';
import { CORES_CATEGORIA } from '@/lib/cores';
import { definirTema, preferenciaTema } from '@/lib/theme';
import { capitalizarNome, iniciais } from '@/lib/utils';
import { lerSessao } from '@/api/session';

const TEMAS = [
  { valor: 'light', rotulo: 'Claro' },
  { valor: 'dark', rotulo: 'Escuro' },
  { valor: 'system', rotulo: 'Sistema' },
];

const INICIO_SEMANA_KEY = 'jdi.inicio-semana';
const AVATAR_MAX = 256; // lado máximo do avatar, em px

// Reduz a imagem para no máx. AVATAR_MAX px e devolve um Data URL JPEG (algumas
// dezenas de KB), evitando trafegar a original.
function redimensionar(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('Falha ao ler o arquivo.'));
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error('Imagem inválida.'));
      img.onload = () => {
        const escala = Math.min(1, AVATAR_MAX / Math.max(img.width, img.height));
        const w = Math.max(1, Math.round(img.width * escala));
        const h = Math.max(1, Math.round(img.height * escala));
        const canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        canvas.getContext('2d').drawImage(img, 0, 0, w, h);
        resolve(canvas.toDataURL('image/jpeg', 0.85));
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

/* ─── Conta ─────────────────────────────────────────────────────────────── */
function SecaoConta() {
  const { data: usuario } = useConta();
  const atualizar = useAtualizarConta();
  const arquivoRef = useRef(null);

  // Qual campo está aberto para edição: 'name' | 'email' | 'password' | null
  const [editando, setEditando] = useState(null);
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senhas, setSenhas] = useState({ atual: '', nova: '', conf: '' });
  const [msg, setMsg] = useState({ texto: '', erro: false });

  const sessao = lerSessao();
  const nomeAtual = usuario?.name || sessao?.name || '';
  const emailAtual = usuario?.email || sessao?.email || '';
  const avatarUrl = usuario?.avatarUrl || '';

  // Sincroniza os inputs com o backend, exceto o que está sendo editado agora.
  useEffect(() => { if (editando !== 'name') setNome(nomeAtual); }, [nomeAtual, editando]);
  useEffect(() => { if (editando !== 'email') setEmail(emailAtual); }, [emailAtual, editando]);

  function aviso(texto, erro = false) { setMsg({ texto, erro }); }

  function abrir(campo) {
    setEditando(campo);
    aviso('');
    if (campo === 'password') setSenhas({ atual: '', nova: '', conf: '' });
  }

  function cancelar() {
    setNome(nomeAtual);
    setEmail(emailAtual);
    setEditando(null);
    aviso('');
  }

  function salvar(campo) {
    let body;
    if (campo === 'name') {
      const n = capitalizarNome(nome);
      if (!n) return aviso('Informe seu nome.', true);
      body = { name: n };
    } else if (campo === 'email') {
      const e = email.trim();
      if (!e) return aviso('Informe seu email.', true);
      body = { email: e };
    } else {
      const { atual, nova, conf } = senhas;
      if (!atual) return aviso('Informe a senha atual.', true);
      if (!nova) return aviso('Informe a nova senha.', true);
      if (nova.length < 8) return aviso('A nova senha deve ter pelo menos 8 caracteres.', true);
      if (conf !== nova) return aviso('A confirmação não corresponde à nova senha.', true);
      body = { currentPassword: atual, newPassword: nova };
    }

    aviso('');
    atualizar.mutate(body, {
      onSuccess: () => { setEditando(null); aviso('Alterações salvas.'); },
      onError: (err) => aviso(traduzErroConta(err), true),
    });
  }

  function escolherFoto(e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!/^image\//.test(file.type)) return aviso('Selecione um arquivo de imagem.', true);
    if (file.size > 5 * 1024 * 1024) return aviso('A imagem deve ter no máximo 5 MB.', true);

    redimensionar(file)
      .then((dataUrl) => {
        aviso('');
        // A foto é persistida no backend e relida de lá — nada de base64 local.
        atualizar.mutate({ avatarUrl: dataUrl }, { onError: (err) => aviso(traduzErroConta(err), true) });
      })
      .catch((err) => aviso(err.message || 'Não foi possível processar a imagem.', true));
  }

  function removerFoto() {
    // O backend só remove quando avatarUrl chega como string vazia (null é ignorado).
    atualizar.mutate({ avatarUrl: '' }, { onError: (err) => aviso(traduzErroConta(err), true) });
  }

  const campoInline = (campo, rotulo, valor, setValor, tipo, autoComplete) => (
    <div className={`acc-field ${editando === campo ? 'is-editing' : ''}`}>
      <label className="acc-field__label" htmlFor={`pf-${campo}`}>{rotulo}</label>
      <input
        id={`pf-${campo}`}
        className="acc-field__input"
        type={tipo}
        autoComplete={autoComplete}
        value={valor}
        readOnly={editando !== campo}
        onChange={(e) => setValor(e.target.value)}
        onKeyDown={(e) => {
          if (editando !== campo) return;
          if (e.key === 'Enter') { e.preventDefault(); salvar(campo); }
          if (e.key === 'Escape') { e.preventDefault(); cancelar(); }
        }}
      />
      {editando === campo ? (
        <span className="acc-field__btns">
          <button className="btn btn--secondary btn--sm" type="button" onClick={cancelar}>Cancelar</button>
          <button className="btn btn--primary btn--sm" type="button" onClick={() => salvar(campo)} disabled={atualizar.isPending}>
            Salvar
          </button>
        </span>
      ) : (
        <button className="acc-field__edit" type="button" onClick={() => abrir(campo)}>Editar</button>
      )}
    </div>
  );

  return (
    <div className="set-section">
      <div className="set-section__title">Conta</div>
      <div className="card set-acc-card">
        <div className="set-acc__photo">
          <button type="button" className="set-acc__avatar" onClick={() => arquivoRef.current?.click()} aria-label="Alterar foto de perfil">
            {avatarUrl
              ? <img className="set-acc__avatar-img" src={avatarUrl} alt="" />
              : <span className="set-acc__avatar-initials">{iniciais(nomeAtual)}</span>}
            <span className="set-acc__avatar-cam" aria-hidden="true">
              <Ic d='<path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/>' />
            </span>
          </button>
          <input ref={arquivoRef} type="file" accept="image/*" hidden onChange={escolherFoto} />
          <div className="set-acc__photo-actions">
            <button type="button" className="set-acc__photo-link" onClick={() => arquivoRef.current?.click()}>
              Alterar foto
            </button>
            {avatarUrl && (
              <button type="button" className="set-acc__photo-link set-acc__photo-link--danger" onClick={removerFoto}>
                Remover foto
              </button>
            )}
          </div>
        </div>

        <div className="set-acc">
          {campoInline('name', 'Nome', nome, setNome, 'text', 'name')}
          {campoInline('email', 'Email', email, setEmail, 'email', 'email')}

          {editando !== 'password' && (
            <div className="acc-field">
              <label className="acc-field__label">Senha</label>
              <input className="acc-field__input" type="password" value="********" readOnly tabIndex={-1} aria-hidden="true" />
              <button className="acc-field__edit" type="button" onClick={() => abrir('password')}>Editar</button>
            </div>
          )}

          {editando === 'password' && (
            <form className="acc-pwd" onSubmit={(e) => { e.preventDefault(); salvar('password'); }}>
              <div className="set-field">
                <label className="set-field__label" htmlFor="pfCurrentPwd">Senha atual</label>
                <input
                  id="pfCurrentPwd" className="set-field__input" type="password" autoComplete="current-password"
                  value={senhas.atual} onChange={(e) => setSenhas((s) => ({ ...s, atual: e.target.value }))}
                />
              </div>
              <div className="set-field">
                <label className="set-field__label" htmlFor="pfNewPwd">Nova senha</label>
                <input
                  id="pfNewPwd" className="set-field__input" type="password" autoComplete="new-password"
                  placeholder="Mínimo de 8 caracteres"
                  value={senhas.nova} onChange={(e) => setSenhas((s) => ({ ...s, nova: e.target.value }))}
                />
              </div>
              <div className="set-field">
                <label className="set-field__label" htmlFor="pfConfirmPwd">Confirmar nova senha</label>
                <input
                  id="pfConfirmPwd" className="set-field__input" type="password" autoComplete="new-password"
                  value={senhas.conf} onChange={(e) => setSenhas((s) => ({ ...s, conf: e.target.value }))}
                />
              </div>
              <div className="set-acc__actions">
                <button className="btn btn--secondary btn--sm" type="button" onClick={cancelar}>Cancelar</button>
                <button className="btn btn--primary btn--sm" type="submit" disabled={atualizar.isPending}>Salvar</button>
              </div>
            </form>
          )}

          {msg.texto && (
            <div className={`set-profile-edit__msg ${msg.erro ? 'set-profile-edit__msg--error' : ''}`}>
              {msg.texto}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

/* ─── Categorias ────────────────────────────────────────────────────────── */
function SecaoCategorias() {
  const { data: categorias } = useCategorias();
  const { data: tarefas } = useTarefas(categorias);
  const criar = useCriarCategoria();
  const remover = useRemoverCategoria();

  const [nova, setNova] = useState('');
  const [cor, setCor] = useState(CORES_CATEGORIA[0]);
  const [erro, setErro] = useState('');
  const [editando, setEditando] = useState(null);
  const [excluindo, setExcluindo] = useState(null);

  // Tarefas por nome de categoria — mesma associação por nome da sidebar.
  const contagem = useMemo(() => {
    const mapa = {};
    (tarefas || []).forEach((t) => {
      const nome = t.cat || CAT_GENERICO.nome;
      mapa[nome] = (mapa[nome] || 0) + 1;
    });
    return mapa;
  }, [tarefas]);

  function adicionar() {
    const nome = nova.trim();
    if (!nome) return;
    setErro('');
    criar.mutate({ nome, cor }, {
      onSuccess: () => setNova(''),
      onError: (e) => setErro(e.message || 'Não foi possível criar a categoria.'),
    });
  }

  function confirmarExclusao() {
    remover.mutate(excluindo.id, {
      onSuccess: () => setExcluindo(null),
      onError: (e) => setErro(e.message || 'Não foi possível excluir a categoria.'),
    });
  }

  const linhas = (categorias || []).filter((c) => c.id !== CAT_GENERICO.id);
  const nExcluindo = excluindo ? (contagem[excluindo.nome] || 0) : 0;

  return (
    <div className="set-section">
      <div className="set-section__title">Categorias</div>
      <div className="card">
        <div className="cat-list">
          {/* "Genérico" é a categoria padrão de todo usuário: sempre no topo e
              não pode ser excluída (no backend ela é categoryId null). */}
          <div className="cat-row">
            <span className="cat-row__dot" style={{ background: CAT_GENERICO.cor }} />
            <span className="cat-row__name">{CAT_GENERICO.nome}</span>
            <span className="cat-row__count">
              {contagem[CAT_GENERICO.nome] || 0} {(contagem[CAT_GENERICO.nome] || 0) === 1 ? 'tarefa' : 'tarefas'}
            </span>
            <span className="cat-row__tag">padrão</span>
          </div>

          {linhas.map((c) => {
            const n = contagem[c.nome] || 0;
            return (
              <div className="cat-row" key={c.id}>
                <span className="cat-row__dot" style={{ background: c.cor }} />
                <span className="cat-row__name">{c.nome}</span>
                <span className="cat-row__count">{n} {n === 1 ? 'tarefa' : 'tarefas'}</span>
                <button className="cat-row__edit" type="button" aria-label="Editar nome da categoria" onClick={() => setEditando(c)}>
                  <Ic d={ICONS.edit} />
                </button>
                <button className="cat-row__del" type="button" aria-label="Excluir categoria" onClick={() => setExcluindo(c)}>
                  <Ic d={ICONS.trash} />
                </button>
              </div>
            );
          })}
        </div>

        <div className="divider" />

        <div className="cat-add">
          <div className="cat-add__colors">
            {CORES_CATEGORIA.map((c) => (
              <span
                key={c}
                className={`cat-add__swatch ${c === cor ? 'is-sel' : ''}`}
                style={{ background: c }}
                role="button"
                tabIndex={0}
                aria-label="Selecionar cor"
                onClick={() => setCor(c)}
              />
            ))}
          </div>
          <input
            className="cat-add__input"
            placeholder="Nova categoria… pressione Enter"
            value={nova}
            onChange={(e) => setNova(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') adicionar(); }}
          />
          <button className="btn btn--primary btn--sm" type="button" onClick={adicionar} disabled={criar.isPending}>
            Adicionar
          </button>
        </div>

        {erro && <div className="cat-msg cat-msg--error">{erro}</div>}
      </div>

      <CategoryModal aberto={Boolean(editando)} categoria={editando} onFechar={() => setEditando(null)} />

      <ConfirmModal
        aberto={Boolean(excluindo)}
        titulo="Excluir categoria"
        processando={remover.isPending}
        onConfirmar={confirmarExclusao}
        onFechar={() => setExcluindo(null)}
      >
        <p className="cat-del__text">
          {nExcluindo > 0 ? (
            <>
              A categoria <strong>{excluindo?.nome}</strong> possui{' '}
              <strong>{nExcluindo} {nExcluindo === 1 ? 'tarefa' : 'tarefas'}</strong>. Ao excluir,{' '}
              {nExcluindo === 1 ? 'ela será movida' : 'elas serão movidas'} para <strong>Genérico</strong>.
              Deseja excluir mesmo assim?
            </>
          ) : (
            <>Deseja excluir a categoria <strong>{excluindo?.nome}</strong>?</>
          )}
        </p>
      </ConfirmModal>
    </div>
  );
}

/* ─── Dados ─────────────────────────────────────────────────────────────── */
function SecaoDados() {
  const exportar = useExportarDados();
  const [aberto, setAberto] = useState(false);
  const [erro, setErro] = useState('');
  const [baixado, setBaixado] = useState('');

  function confirmar(formato) {
    setErro('');
    exportar.mutate(formato, {
      onSuccess: (nome) => { setAberto(false); setBaixado(nome); },
      onError: (e) => setErro(e.message || 'Não foi possível exportar seus dados. Tente novamente.'),
    });
  }

  return (
    <div className="set-section">
      <div className="set-section__title">Dados</div>
      <div className="card">
        <div className="set-row">
          <div className="set-row__main">
            <div className="set-row__label">Exportar meus dados</div>
            <div className="set-row__desc">
              Baixe todas as suas tarefas em CSV ou JSON para backup ou análise externa
            </div>
          </div>
          <button
            className="btn btn--secondary btn--sm"
            type="button"
            onClick={() => { setBaixado(''); setAberto(true); }}
          >
            Exportar meus dados
          </button>
        </div>
        {baixado && <div className="cat-msg">Arquivo <strong>{baixado}</strong> baixado.</div>}
      </div>

      <ExportModal
        aberto={aberto}
        processando={exportar.isPending}
        erro={erro}
        onExportar={confirmar}
        onFechar={() => { setAberto(false); setErro(''); }}
      />
    </div>
  );
}

/* ─── Página ────────────────────────────────────────────────────────────── */
export default function Configuracoes() {
  const navigate = useNavigate();
  const excluirConta = useExcluirConta();

  const [tema, setTema] = useState(preferenciaTema);
  const [inicioSemana, setInicioSemana] = useState(() => {
    try { return JSON.parse(localStorage.getItem(INICIO_SEMANA_KEY)) || 'seg'; } catch { return 'seg'; }
  });
  const [confirmandoConta, setConfirmandoConta] = useState(false);
  const [erroConta, setErroConta] = useState('');

  function trocarTema(valor) {
    definirTema(valor);
    setTema(valor);
  }

  // Mesma chave do app antigo — o WeeklyCalendar lê daqui.
  function trocarInicioSemana(valor) {
    localStorage.setItem(INICIO_SEMANA_KEY, JSON.stringify(valor));
    setInicioSemana(valor);
  }

  function confirmarExclusaoConta() {
    setErroConta('');
    excluirConta.mutate(undefined, {
      onSuccess: () => navigate('/', { replace: true }),
      onError: (e) => setErroConta(e.message || 'Não foi possível excluir a conta. Tente novamente.'),
    });
  }

  return (
    <div className="app">
      <Sidebar ativa="settings" />

      <main className="app__main">
        <div className="set">
          <header className="page__head" style={{ marginBottom: 'var(--space-xl)' }}>
            <div>
              <div className="page__eyebrow">Preferências</div>
              <h1 className="page__title">Configurações</h1>
            </div>
          </header>

          <SecaoConta />

          <div className="set-section">
            <div className="set-section__title">Aparência</div>
            <div className="card">
              <div className="set-row">
                <div className="set-row__main">
                  <div className="set-row__label">Tema</div>
                  <div className="set-row__desc">Claro, escuro ou conforme o sistema</div>
                </div>
                <div className="set-seg">
                  {TEMAS.map((t) => (
                    <button
                      key={t.valor}
                      type="button"
                      className={tema === t.valor ? 'is-active' : ''}
                      onClick={() => trocarTema(t.valor)}
                    >
                      {t.rotulo}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          <div className="set-section">
            <div className="set-section__title">Preferências</div>
            <div className="card">
              {[
                ['Lembretes diários', 'Receba um resumo das tarefas do dia pela manhã', true],
                ['Notificações de prazo', 'Avisar quando uma tarefa estiver perto de vencer', true],
                ['Som ao concluir ciclo de foco', 'Toque sutil ao fim de cada Pomodoro', false],
              ].map(([label, desc, ligado]) => (
                <div className="set-row" key={label}>
                  <div className="set-row__main">
                    <div className="set-row__label">{label} <span className="set-soon">(em breve)</span></div>
                    <div className="set-row__desc">{desc}</div>
                  </div>
                  <label className="switch is-disabled">
                    <input type="checkbox" defaultChecked={ligado} disabled aria-label={label} />
                    <span className="switch__track"><span className="switch__thumb" /></span>
                  </label>
                </div>
              ))}

              <div className="set-row">
                <div className="set-row__main">
                  <div className="set-row__label">Início da semana</div>
                  <div className="set-row__desc">Primeiro dia exibido no calendário</div>
                </div>
                <div className="set-seg">
                  <button type="button" className={inicioSemana === 'seg' ? 'is-active' : ''} onClick={() => trocarInicioSemana('seg')}>
                    Segunda
                  </button>
                  <button type="button" className={inicioSemana === 'dom' ? 'is-active' : ''} onClick={() => trocarInicioSemana('dom')}>
                    Domingo
                  </button>
                </div>
              </div>
            </div>
          </div>

          <SecaoCategorias />

          <SecaoDados />

          <div className="set-section">
            <div className="set-section__title">Excluir conta</div>
            <div className="card">
              <div className="set-row">
                <div className="set-row__main">
                  <div className="set-row__label">Excluir minha conta</div>
                  <div className="set-row__desc">
                    Remove permanentemente sua conta e seus dados de acesso. Esta ação não pode ser desfeita.
                  </div>
                </div>
                <button className="btn btn--danger btn--sm" type="button" onClick={() => setConfirmandoConta(true)}>
                  Excluir conta
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>

      <ConfirmModal
        aberto={confirmandoConta}
        titulo="Excluir conta"
        rotuloConfirmar="Excluir conta"
        exigeTexto="EXCLUIR"
        erro={erroConta}
        processando={excluirConta.isPending}
        onConfirmar={confirmarExclusaoConta}
        onFechar={() => { setConfirmandoConta(false); setErroConta(''); }}
      >
        <p className="cat-del__text">
          Esta ação é <strong>permanente</strong> e remove sua conta e seus dados de acesso. Não é possível desfazer.
        </p>
        <p className="cat-del__text">Para confirmar, digite <strong>EXCLUIR</strong> abaixo.</p>
      </ConfirmModal>
    </div>
  );
}
