import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import ConfirmModal from '@/components/ConfirmModal';
import Ic, { ICONS, Mark } from '@/components/Ic';
import CategoryModal from '@/features/categories/components/CategoryModal';
import DevelopmentChatModal from '@/features/feedback/components/DevelopmentChatModal';
import NotificationCenter from '@/features/notifications/components/NotificationCenter';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { alternarTema } from '@/lib/theme';
import { lerSessao } from '@/api/session';
import { capitalizarNome, iniciais } from '@/lib/utils';
import { useCategorias, useRemoverCategoria } from '@/features/categories/hooks/useCategories';
import { useConta } from '@/features/auth/hooks/useConta';
import { useEncerrarSessao } from '@/features/auth/hooks/useSessao';
import { useAtualizarTarefa, useRemoverTarefa, useTarefas } from '@/features/tasks/hooks/useTasks';

const SIDEBAR_WIDTH_KEY = 'jdi-sidebar-width';
const SIDEBAR_DEFAULT_WIDTH = 264;
const SIDEBAR_MIN_WIDTH = 180;
const SIDEBAR_MAX_WIDTH = 480;
const SIDEBAR_MOBILE_WIDTH_KEY = 'jdi-sidebar-mobile-width';
const SIDEBAR_MOBILE_CLOSED_WIDTH = 64;
const SIDEBAR_MOBILE_DEFAULT_WIDTH = 320;
const SIDEBAR_MOBILE_MAX_WIDTH = 360;

function limitarLarguraSidebar(largura) {
  return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, largura));
}

function larguraInicialSidebar() {
  const salva = Number.parseFloat(localStorage.getItem(SIDEBAR_WIDTH_KEY));
  return Number.isFinite(salva) ? limitarLarguraSidebar(salva) : SIDEBAR_DEFAULT_WIDTH;
}

function limitarLarguraSidebarMobile(largura) {
  const max = Math.min(SIDEBAR_MOBILE_MAX_WIDTH, window.innerWidth);
  return Math.min(max, Math.max(SIDEBAR_MOBILE_CLOSED_WIDTH, largura));
}

function larguraInicialSidebarMobile() {
  const salva = Number.parseFloat(localStorage.getItem(SIDEBAR_MOBILE_WIDTH_KEY));
  return limitarLarguraSidebarMobile(
    Number.isFinite(salva) ? salva : SIDEBAR_MOBILE_DEFAULT_WIDTH,
  );
}

export default function Sidebar({ ativa = 'dashboard' }) {
  const navigate = useNavigate();
  const location = useLocation();
  const encerrarSessao = useEncerrarSessao();
  const [colapsada, setColapsada] = useState(() => localStorage.getItem('jdi-sidebar-collapsed') === 'true');
  const [catsVisiveis, setCatsVisiveis] = useState(true);
  const [modalAberto, setModalAberto] = useState(false);
  const [busca, setBusca] = useState('');
  const [expandidas, setExpandidas] = useState({});
  const [menuContexto, setMenuContexto] = useState(null);
  const [tarefaParaExcluir, setTarefaParaExcluir] = useState(null);
  const [categoriaParaExcluir, setCategoriaParaExcluir] = useState(null);
  const [erroExclusao, setErroExclusao] = useState('');
  const [chatAberto, setChatAberto] = useState(false);
  const [largura, setLargura] = useState(larguraInicialSidebar);
  const [larguraMobile, setLarguraMobile] = useState(larguraInicialSidebarMobile);
  const [redimensionando, setRedimensionando] = useState(false);
  const inicioRedimensionamento = useRef({ pointerId: null, x: 0, largura: SIDEBAR_DEFAULT_WIDTH, mobile: false });
  const larguraAtual = useRef(largura);
  const [menuMobileAberto, setMenuMobileAberto] = useState(false);

  const { data: categorias } = useCategorias();
  const { data: tarefas } = useTarefas(categorias);
  const atualizarTarefa = useAtualizarTarefa();
  const removerTarefa = useRemoverTarefa();
  const removerCategoria = useRemoverCategoria();

  // Nome/avatar: começa com o que há na sessão e atualiza com GET /auth/me.
  const sessao = lerSessao();
  const { data: usuario } = useConta();
  const nome = capitalizarNome(usuario?.name || sessao?.name || '') || 'Usuário';

  const pendentes = useMemo(() => (tarefas || []).filter((t) => !t.done), [tarefas]);

  useEffect(() => {
    if (!menuContexto) return undefined;
    const fechar = () => setMenuContexto(null);
    const aoTeclar = (e) => { if (e.key === 'Escape') fechar(); };
    document.addEventListener('click', fechar);
    document.addEventListener('contextmenu', fechar);
    document.addEventListener('keydown', aoTeclar);
    window.addEventListener('resize', fechar);
    window.addEventListener('scroll', fechar, true);
    return () => {
      document.removeEventListener('click', fechar);
      document.removeEventListener('contextmenu', fechar);
      document.removeEventListener('keydown', aoTeclar);
      window.removeEventListener('resize', fechar);
      window.removeEventListener('scroll', fechar, true);
    };
  }, [menuContexto]);

  useEffect(() => {
    if (!redimensionando) return undefined;

    const cursorAnterior = document.body.style.cursor;
    const selecaoAnterior = document.body.style.userSelect;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    function aoMover(e) {
      if (e.pointerId !== inicioRedimensionamento.current.pointerId) return;
      const bruta = inicioRedimensionamento.current.largura
        + e.clientX
        - inicioRedimensionamento.current.x;
      const proxima = inicioRedimensionamento.current.mobile
        ? limitarLarguraSidebarMobile(bruta)
        : limitarLarguraSidebar(bruta);
      larguraAtual.current = proxima;
      if (inicioRedimensionamento.current.mobile) setLarguraMobile(proxima);
      else setLargura(proxima);
    }

    function aoTerminar(e) {
      if (e.pointerId !== inicioRedimensionamento.current.pointerId) return;
      if (inicioRedimensionamento.current.mobile) {
        if (larguraAtual.current <= SIDEBAR_MOBILE_CLOSED_WIDTH + 16) {
          setMenuMobileAberto(false);
        } else {
          localStorage.setItem(SIDEBAR_MOBILE_WIDTH_KEY, `${larguraAtual.current}px`);
        }
      } else {
        localStorage.setItem(SIDEBAR_WIDTH_KEY, `${larguraAtual.current}px`);
      }
      setRedimensionando(false);
    }

    document.addEventListener('pointermove', aoMover);
    document.addEventListener('pointerup', aoTerminar);
    document.addEventListener('pointercancel', aoTerminar);
    return () => {
      document.removeEventListener('pointermove', aoMover);
      document.removeEventListener('pointerup', aoTerminar);
      document.removeEventListener('pointercancel', aoTerminar);
      document.body.style.cursor = cursorAnterior;
      document.body.style.userSelect = selecaoAnterior;
    };
  }, [redimensionando]);

  function iniciarRedimensionamento(e) {
    const mobile = window.innerWidth <= 880;
    if (!mobile && colapsada) return;
    const larguraRenderizada = e.currentTarget.parentElement.getBoundingClientRect().width;
    const larguraInicial = mobile ? larguraRenderizada : largura;
    inicioRedimensionamento.current = {
      pointerId: e.pointerId,
      x: e.clientX,
      largura: larguraInicial,
      mobile,
    };
    larguraAtual.current = larguraInicial;
    if (mobile) {
      setLarguraMobile(larguraInicial);
      setMenuMobileAberto(true);
    }
    e.currentTarget.setPointerCapture?.(e.pointerId);
    setRedimensionando(true);
    e.preventDefault();
  }

  function redimensionarPeloTeclado(e) {
    const mobile = window.innerWidth <= 880;
    const larguraEmUso = mobile
      ? (menuMobileAberto ? larguraMobile : SIDEBAR_MOBILE_CLOSED_WIDTH)
      : largura;
    let proxima;
    if (e.key === 'ArrowLeft') proxima = larguraEmUso - 16;
    if (e.key === 'ArrowRight') proxima = larguraEmUso + 16;
    if (e.key === 'Home') proxima = mobile ? SIDEBAR_MOBILE_CLOSED_WIDTH : SIDEBAR_MIN_WIDTH;
    if (e.key === 'End') proxima = mobile ? limitarLarguraSidebarMobile(SIDEBAR_MOBILE_MAX_WIDTH) : SIDEBAR_MAX_WIDTH;
    if (proxima === undefined) return;

    e.preventDefault();
    const limitada = mobile ? limitarLarguraSidebarMobile(proxima) : limitarLarguraSidebar(proxima);
    larguraAtual.current = limitada;
    if (mobile) {
      setLarguraMobile(limitada);
      setMenuMobileAberto(limitada > SIDEBAR_MOBILE_CLOSED_WIDTH);
      if (limitada > SIDEBAR_MOBILE_CLOSED_WIDTH) {
        localStorage.setItem(SIDEBAR_MOBILE_WIDTH_KEY, `${limitada}px`);
      }
    } else {
      setLargura(limitada);
      localStorage.setItem(SIDEBAR_WIDTH_KEY, `${limitada}px`);
    }
  }

  function alternarMenuMobile() {
    if (menuMobileAberto) {
      setMenuMobileAberto(false);
      return;
    }
    setLarguraMobile(larguraInicialSidebarMobile());
    setMenuMobileAberto(true);
  }

  useEffect(() => {
    setMenuMobileAberto(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!menuMobileAberto) return undefined;

    const overflowAnterior = document.body.style.overflow;
    const aoTeclar = (event) => {
      if (event.key === 'Escape') setMenuMobileAberto(false);
    };
    const aoRedimensionar = () => {
      if (window.innerWidth > 880) setMenuMobileAberto(false);
    };

    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', aoTeclar);
    window.addEventListener('resize', aoRedimensionar);
    return () => {
      document.body.style.overflow = overflowAnterior;
      document.removeEventListener('keydown', aoTeclar);
      window.removeEventListener('resize', aoRedimensionar);
    };
  }, [menuMobileAberto]);

  function sair() {
    api.post(endpoints.auth.logout).catch(() => {}).finally(() => {
      encerrarSessao();
      navigate('/', { replace: true });
    });
  }

  function toggleColapso() {
    const novo = !colapsada;
    setColapsada(novo);
    localStorage.setItem('jdi-sidebar-collapsed', String(novo));
  }

  function tarefasDaCategoria(catNome) {
    const lista = pendentes.filter((t) => t.cat === catNome);
    if (!busca) return lista;
    return lista.filter((t) => t.titulo.toLowerCase().includes(busca.toLowerCase()));
  }

  function abrirMenu(e, tipo, item) {
    e.preventDefault();
    e.stopPropagation();
    const largura = 210;
    const altura = tipo === 'tarefa' ? 174 : 92;
    setMenuContexto({
      tipo,
      item,
      x: Math.max(8, Math.min(e.clientX, window.innerWidth - largura - 8)),
      y: Math.max(8, Math.min(e.clientY, window.innerHeight - altura - 8)),
    });
  }

  function executar(acao) {
    const { item } = menuContexto;
    setMenuContexto(null);
    if (acao === 'nova-tarefa') navigate('/tasks/nova');
    if (acao === 'editar-tarefa') navigate(`/tasks/${item.id}`);
    if (acao === 'remover-categoria') {
      atualizarTarefa.mutate({ id: item.id, dados: { ...item, categoriaId: null } });
    }
    if (acao === 'excluir-tarefa') {
      setErroExclusao('');
      setTarefaParaExcluir(item);
    }
    if (acao === 'nova-categoria') setModalAberto(true);
    if (acao === 'excluir-categoria') {
      setErroExclusao('');
      setCategoriaParaExcluir(item);
    }
  }

  function confirmarExclusao() {
    removerTarefa.mutate(tarefaParaExcluir.id, {
      onSuccess: () => setTarefaParaExcluir(null),
      onError: (erro) => setErroExclusao(erro.message || 'Não foi possível excluir a tarefa.'),
    });
  }

  function confirmarExclusaoCategoria() {
    removerCategoria.mutate(categoriaParaExcluir.id, {
      onSuccess: () => setCategoriaParaExcluir(null),
      onError: (erro) => setErroExclusao(erro.message || 'Não foi possível excluir a categoria.'),
    });
  }

  return (
    <>
      <button
        className={`sidebar__mobile-backdrop ${menuMobileAberto ? 'is-visible' : ''}`}
        type="button"
        aria-label="Fechar menu"
        tabIndex={menuMobileAberto ? 0 : -1}
        onClick={() => setMenuMobileAberto(false)}
      />
      <aside
        id="app-sidebar"
        className={`sidebar ${colapsada && !menuMobileAberto ? 'sidebar--collapsed' : ''} ${menuMobileAberto ? 'sidebar--mobile-open' : ''} ${redimensionando ? 'is-resizing' : ''}`}
        style={{
          '--sidebar-current-width': `${largura}px`,
          '--sidebar-mobile-current-width': `${larguraMobile}px`,
        }}
      >
      <div className="sidebar__brand">
        <Link className="sidebar__mark" to="/visao-geral" aria-label="Ir para a Visão geral"><Mark /></Link>
        <span className="sidebar__word">JustDoIt</span>
        <button
          className="sidebar__mobile-toggle"
          type="button"
          aria-label={menuMobileAberto ? 'Fechar menu' : 'Abrir menu'}
          aria-controls="app-sidebar"
          aria-expanded={menuMobileAberto}
          onClick={alternarMenuMobile}
        >
          <Ic d={menuMobileAberto ? ICONS.close : ICONS.list} />
        </button>
        <button className="sidebar__collapse" onClick={toggleColapso} aria-label={colapsada ? 'Expandir menu' : 'Recolher menu'}>
          <Ic d={ICONS.chevronLeft} />
        </button>
      </div>

      <div className="sidebar__scroll">
        <nav className="sidebar__nav">
          <Link className={`nav-item ${ativa === 'dashboard' ? 'is-active' : ''}`} to="/visao-geral">
            <span className="nav-item__ic"><Ic d={ICONS.dashboard} /></span>
            <span className="nav-item__label">Visão geral</span>
          </Link>
          <Link className={`nav-item ${ativa === 'calendar' ? 'is-active' : ''}`} to="/calendario">
            <span className="nav-item__ic"><Ic d={ICONS.calendar} /></span>
            <span className="nav-item__label">Calendário</span>
          </Link>
          <Link className={`nav-item ${ativa === 'todo' ? 'is-active' : ''}`} to="/todo">
            <span className="nav-item__ic"><Ic d={ICONS.todo} /></span>
            <span className="nav-item__label">To Do</span>
            <span className="nav-item__count">{pendentes.length}</span>
          </Link>
          <Link className={`nav-item ${ativa === 'notes' ? 'is-active' : ''}`} to="/anotacoes">
            <span className="nav-item__ic"><Ic d={ICONS.notes} /></span>
            <span className="nav-item__label">Anotações</span>
          </Link>
          <Link className={`nav-item ${ativa === 'analytics' ? 'is-active' : ''}`} to="/analise">
            <span className="nav-item__ic"><Ic d={ICONS.analytics} /></span>
            <span className="nav-item__label">Análise</span>
          </Link>
          {/* 
          <Link className={`nav-item ${ativa === 'history' ? 'is-active' : ''}`} to="/history">
            <span className="nav-item__ic"><Ic d={ICONS.clock} /></span> 
            <span className="nav-item__label">Histórico</span>
          </Link>
        */}
        </nav> 

        <Link className="sidebar__new-task" to="/tasks/nova" aria-label="Nova tarefa" title="Nova tarefa">
          <span className="sidebar__new-task-ic"><Ic d={ICONS.plus} /></span>
          <span className="sidebar__new-task-label">Nova tarefa</span>
        </Link>

        <div className="sidebar__section">
          <span>Categorias</span>
          <div className="sidebar__section-actions">
            <button className="sidebar__cat-toggle" onClick={() => setModalAberto(true)} aria-label="Nova categoria">
              <Ic d={ICONS.plus} />
            </button>
            <button className="sidebar__cat-toggle" onClick={() => setCatsVisiveis((v) => !v)} aria-label="Ocultar categorias" aria-expanded={catsVisiveis}>
              <Ic d={ICONS.chevron} />
            </button>
          </div>
        </div>

        {catsVisiveis && (
          <div className="sidebar__categories">
            <div className="sidebar__search">
              <span className="sidebar__search-ic"><Ic d={ICONS.search} /></span>
              <input
                className="sidebar__search-input"
                type="text"
                placeholder="Buscar tarefas…"
                aria-label="Buscar tarefas"
                autoComplete="off"
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
              />
            </div>
            <nav className="sidebar__nav sidebar__nav--cats">
              {!categorias && <div className="sidebar-cat__empty">Carregando categorias…</div>}
              {categorias?.map((c) => {
                const doGrupo = tarefasDaCategoria(c.nome);
                const aberta = Boolean(expandidas[c.id]) || Boolean(busca);
                return (
                  <div className="sidebar-cat" key={c.id}>
                    <button
                      className="sidebar-cat__header"
                      aria-expanded={aberta}
                      onClick={() => setExpandidas((m) => ({ ...m, [c.id]: !m[c.id] }))}
                      onContextMenu={(e) => abrirMenu(e, 'categoria', c)}
                    >
                      <span className="cat-dot" style={{ background: c.cor }} />
                      <span className="nav-item__label">{c.nome}</span>
                      <span className="nav-item__count">{pendentes.filter((t) => t.cat === c.nome).length}</span>
                      <span className="sidebar-cat__chevron"><Ic d={ICONS.chevron} /></span>
                    </button>
                    {aberta && (
                      <div className="sidebar-cat__tasks">
                        {doGrupo.length === 0 && (
                          <div className="sidebar-cat__empty">{busca ? 'Nenhum resultado' : 'Nenhuma tarefa pendente'}</div>
                        )}
                        {doGrupo.map((t) => {
                          const isReadOnly = t.cycleStatus === 'CLOSED';
                          return (
                            <Link
                              className={`sidebar-task ${isReadOnly ? 'sidebar-task--readonly opacity-60 pointer-events-none' : ''}`}
                              key={t.id}
                              to={`/tasks/${t.id}`}
                              title={isReadOnly ? 'Esta tarefa pertence a um ciclo encerrado (Somente leitura)' : t.titulo}
                              draggable={!isReadOnly}
                              onDragStart={(e) => {
                                if (isReadOnly) {
                                  e.preventDefault();
                                  return;
                                }
                                e.dataTransfer.setData('application/jdi-task', JSON.stringify({ id: t.id }));
                              }}
                              onContextMenu={(e) => abrirMenu(e, 'tarefa', t)}
                            >
                              <span className={`sidebar-task__prio sidebar-task__prio--${t.prioridade}`} />
                              <span className="sidebar-task__titulo">{t.titulo}</span>
                              {isReadOnly && <span className="text-[10px] text-amber-400 ml-1">🔒</span>}
                              <span className="sidebar-task__data">{t.data || ''}</span>
                            </Link>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
            </nav>
          </div>
        )}
      </div>

      <div className="sidebar__foot">
        <span className="sidebar__avatar">
          <span className="sidebar__avatar-initials">{iniciais(nome)}</span>
        </span>
        <div className="sidebar__user">
          <div className="sidebar__name">{nome}</div>
          <button className="sidebar__logout" type="button" onClick={sair} aria-label="Sair">
            <span className="sidebar__logout-ic"><Ic d={ICONS.logout} /></span>
            <span className="sidebar__logout-label">Sair</span>
          </button>
        </div>
        <div className="sidebar__actions">
          <Link className="btn-icon" to="/configuracoes" aria-label="Configurações"><Ic d={ICONS.settings} /></Link>
          <button className="btn-icon" type="button" onClick={alternarTema} aria-label="Alternar tema"><Ic d={ICONS.moon} /></button>
        </div>
      </div>

      <div
        className={`sidebar__resizer ${redimensionando ? 'is-dragging' : ''}`}
        role="separator"
        aria-label="Redimensionar menu lateral"
        aria-orientation="vertical"
        aria-valuemin={window.innerWidth <= 880 ? SIDEBAR_MOBILE_CLOSED_WIDTH : SIDEBAR_MIN_WIDTH}
        aria-valuemax={window.innerWidth <= 880 ? limitarLarguraSidebarMobile(SIDEBAR_MOBILE_MAX_WIDTH) : SIDEBAR_MAX_WIDTH}
        aria-valuenow={Math.round(window.innerWidth <= 880
          ? (menuMobileAberto ? larguraMobile : SIDEBAR_MOBILE_CLOSED_WIDTH)
          : largura)}
        tabIndex={0}
        title="Arraste para redimensionar o menu lateral"
        onPointerDown={iniciarRedimensionamento}
        onKeyDown={redimensionarPeloTeclado}
      />

      <button
        className="floating-action floating-action--chat"
        type="button"
        onClick={() => setChatAberto((current) => !current)}
        aria-label="Chat com o desenvolvimento"
        aria-expanded={chatAberto}
        title="Chat com o desenvolvimento"
      >
        <Ic d={ICONS.chat} />
      </button>
      <NotificationCenter />

      <CategoryModal aberto={modalAberto} onFechar={() => setModalAberto(false)} />
      <DevelopmentChatModal aberto={chatAberto} onFechar={() => setChatAberto(false)} />

      {menuContexto && (
        <div
          className="sidebar-context-menu"
          role="menu"
          aria-label={menuContexto.tipo === 'tarefa' ? 'Ações da tarefa' : 'Ações da categoria'}
          style={{ left: menuContexto.x, top: menuContexto.y }}
          onClick={(e) => e.stopPropagation()}
          onContextMenu={(e) => e.preventDefault()}
        >
          {menuContexto.tipo === 'tarefa' ? (
            <>
              <button type="button" role="menuitem" onClick={() => executar('nova-tarefa')}>Criar nova tarefa</button>
              <button type="button" role="menuitem" onClick={() => executar('editar-tarefa')}>Editar tarefa</button>
              <button
                type="button"
                role="menuitem"
                disabled={menuContexto.item.categoriaId === 'generico'}
                onClick={() => executar('remover-categoria')}
              >
                Remover da categoria
              </button>
              <div className="sidebar-context-menu__separator" />
              <button className="is-danger" type="button" role="menuitem" onClick={() => executar('excluir-tarefa')}>
                Excluir
              </button>
            </>
          ) : (
            <>
              <button type="button" role="menuitem" onClick={() => executar('nova-categoria')}>Criar nova categoria</button>
              <button
                className="is-danger"
                type="button"
                role="menuitem"
                disabled={menuContexto.item.id === 'generico'}
                onClick={() => executar('excluir-categoria')}
              >
                Excluir categoria
              </button>
            </>
          )}
        </div>
      )}

      <ConfirmModal
        aberto={Boolean(tarefaParaExcluir)}
        titulo="Excluir tarefa"
        processando={removerTarefa.isPending}
        erro={erroExclusao}
        onConfirmar={confirmarExclusao}
        onFechar={() => {
          setTarefaParaExcluir(null);
          setErroExclusao('');
        }}
      >
        <p>Tem certeza que deseja excluir “{tarefaParaExcluir?.titulo}”? Essa ação não pode ser desfeita.</p>
      </ConfirmModal>

      <ConfirmModal
        aberto={Boolean(categoriaParaExcluir)}
        titulo="Excluir categoria"
        processando={removerCategoria.isPending}
        erro={erroExclusao}
        onConfirmar={confirmarExclusaoCategoria}
        onFechar={() => {
          setCategoriaParaExcluir(null);
          setErroExclusao('');
        }}
      >
        <p>
          Tem certeza que deseja excluir “{categoriaParaExcluir?.nome}”?
          As tarefas dessa categoria serão movidas para “Genérico”.
        </p>
      </ConfirmModal>
      </aside>
    </>
  );
}
