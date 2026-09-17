// Aba "Análise": agendado vs. estimado vs. executado, alocação por categoria,
// taxa de conclusão e insights — tudo derivado de dados reais do backend.
//
// O usuário escolhe qualquer semana desde a criação da conta. As semanas são
// calendários fixos de segunda a domingo, iguais aos planos do schedule-service.
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Ic, { ICONS } from '@/components/Ic';
import Sidebar from '@/components/Sidebar';
import CategoryChart from '@/features/dashboard/components/CategoryChart';
import CategoryExecution from '@/features/dashboard/components/CategoryExecution';
import DeviationChart, { SERIES } from '@/features/dashboard/components/DeviationChart';
import RateRing from '@/features/dashboard/components/RateRing';
import { useAnaliseSemanal } from '@/features/dashboard/hooks/useAnalytics';
import { useConta } from '@/features/auth/hooks/useConta';
import { useCategorias } from '@/features/categories/hooks/useCategories';
import { useTarefas } from '@/features/tasks/hooks/useTasks';
import { deIso, horas, intervaloSemana } from '@/lib/utils';

function Vazio({ children }) {
  return <div style={{ color: 'var(--color-text-subtle)', padding: 'var(--space-md)' }}>{children}</div>;
}

// Erro precisa aparecer com a causa. Um 404 aqui quase sempre é serviço parado ou
// não reiniciado, e 401/403 é sessão; sem dizer isso, "não funciona" vira adivinhação.
function explicarErro(erro) {
  if (erro?.status === 404) {
    return 'Endpoint não encontrado (404). O task-service ou o schedule-service pode estar desatualizado: reinicie os dois.';
  }
  if (erro?.status === 401 || erro?.status === 403) {
    return 'Sua sessão não foi aceita (401/403). Entre novamente.';
  }
  if (erro?.status) return `O servidor respondeu ${erro.status}.`;
  return 'Sem conexão com o servidor. Confira se o task-service (8081) e o schedule-service (8082) estão de pé.';
}

function dataDaConta(usuario, tarefas) {
  const valor = usuario?.createdAt || usuario?.createdDate || usuario?.created_at;
  const conta = valor ? new Date(valor) : null;
  if (conta && !Number.isNaN(conta.getTime())) return conta;

  const datasDasTarefas = (tarefas || [])
    .map((t) => t.criadaEm && new Date(t.criadaEm))
    .filter((d) => d && !Number.isNaN(d.getTime()));
  if (datasDasTarefas.length) return new Date(Math.min(...datasDasTarefas));
  return new Date();
}

function listarSemanas(desde) {
  const primeira = intervaloSemana(desde);
  const atual = intervaloSemana();
  const semanas = [];
  const cursor = new Date(atual.inicio);

  while (cursor >= primeira.inicio) {
    semanas.push(intervaloSemana(cursor));
    cursor.setDate(cursor.getDate() - 7);
  }
  return semanas;
}

// Frases geradas a partir dos números da semana.
function montarInsights({ conclusao, categorias, totalAgendado, totalEstimado, totalExecutado, geral }) {
  const itens = [];

  if (conclusao.total) {
    const pct = Math.round((conclusao.feitas / conclusao.total) * 100);
    itens.push({
      tipo: pct >= 60 ? 'up' : 'down',
      icone: pct >= 60 ? ICONS.checkCircle : ICONS.flag,
      texto: (
        <>
          Você concluiu <strong>{conclusao.feitas} de {conclusao.total}</strong>{' '}
          {geral ? 'tarefas no período' : 'tarefas da semana'}
          (<strong>{pct}%</strong>).{' '}
          {pct >= 60 ? 'Bom ritmo, siga assim.' : 'Vale revisar o que ficou para trás.'}
        </>
      ),
    });
  }

  if (totalAgendado > 0) {
    const diff = totalExecutado - totalAgendado;
    const excedeu = diff > 0;
    itens.push({
      tipo: excedeu ? 'down' : 'up',
      icone: ICONS.clock,
      texto: (
        <>
          Você agendou <strong>{horas(totalAgendado)}</strong> e executou <strong>{horas(totalExecutado)}</strong>,{' '}
          {excedeu
            ? <><strong>{horas(diff)}</strong> a mais que o previsto. Suas estimativas podem estar curtas.</>
            : <><strong>{horas(Math.abs(diff))}</strong> a menos que o previsto.</>}
        </>
      ),
    });
  }

  // A distância entre estimado e agendado é o que não saiu do papel: tarefas com
  // duração definida que nunca ganharam horário no calendário.
  if (totalEstimado > 0 && totalEstimado - totalAgendado > 0.25) {
    itens.push({
      tipo: 'info',
      icone: ICONS.calendar,
      texto: (
        <>
          Você estimou <strong>{horas(totalEstimado)}</strong> em tarefas, mas só{' '}
          <strong>{horas(totalAgendado)}</strong> foram para a agenda.{' '}
          <strong>{horas(totalEstimado - totalAgendado)}</strong> ainda não têm horário marcado.
        </>
      ),
    });
  }

  if (categorias.length) {
    const top = categorias[0];
    itens.push({
      tipo: 'info',
      icone: ICONS.target,
      texto: (
        <>
          A maior fatia do seu tempo estimado está em <strong>{top.nome}</strong>
          {' '}(<strong>{top.horas}h</strong> de {horas(totalEstimado)}).
        </>
      ),
    });
  }

  return itens;
}

export default function Analise() {
  const contaQuery = useConta();
  const categoriasQuery = useCategorias();
  const tarefasQuery = useTarefas(categoriasQuery.data);
  const usuario = contaQuery.data;
  const categorias = categoriasQuery.data;
  const tarefas = tarefasQuery.data;
  const erroBase = contaQuery.error || categoriasQuery.error || tarefasQuery.error || null;
  const [semanaSelecionada, setSemanaSelecionada] = useState(() => intervaloSemana().inicioIso);
  const [menuSemanasAberto, setMenuSemanasAberto] = useState(false);
  const [categoriaModo, setCategoriaModo] = useState('estimado');
  const menuSemanasRef = useRef(null);
  const inicioConta = useMemo(() => dataDaConta(usuario, tarefas), [usuario, tarefas]);
  const semanas = useMemo(
    () => listarSemanas(inicioConta),
    [inicioConta],
  );
  const geral = semanaSelecionada === 'overall';
  const analise = useAnaliseSemanal(
    tarefas,
    geral ? undefined : deIso(semanaSelecionada),
    {
      geral,
      inicio: inicioConta,
      erroBase,
      recarregarBase: () => {
        contaQuery.refetch();
        categoriasQuery.refetch();
        tarefasQuery.refetch();
      },
    },
  );

  useEffect(() => {
    if (!menuSemanasAberto) return undefined;
    const fecharAoClicarFora = (e) => {
      if (!menuSemanasRef.current?.contains(e.target)) setMenuSemanasAberto(false);
    };
    const fecharComEscape = (e) => {
      if (e.key === 'Escape') setMenuSemanasAberto(false);
    };
    document.addEventListener('mousedown', fecharAoClicarFora);
    document.addEventListener('keydown', fecharComEscape);
    return () => {
      document.removeEventListener('mousedown', fecharAoClicarFora);
      document.removeEventListener('keydown', fecharComEscape);
    };
  }, [menuSemanasAberto]);

  const carregando = contaQuery.isLoading
    || categoriasQuery.isLoading
    || tarefasQuery.isLoading
    || analise.carregando;
  // "Pronto" é o único estado em que um card pode afirmar algo sobre a semana:
  // carregando ainda não sabe, e com erro os zeros não significam "vazio".
  const pronto = !carregando && !analise.erro;
  const insights = montarInsights(analise);
  const fechada = analise.fechada;

  return (
    <div className="app">
      <Sidebar ativa="analytics" />

      <main className="app__main">
        <div className="page">
          <header className="page__head">
            <div>
              <div className="page__eyebrow">
                {geral ? 'Desde a criação da conta' : `Semana de ${analise.semana.rotulo}${fechada ? ' · fechada' : ''}`}
              </div>
              <h1 className="page__title">{geral ? 'Visão geral' : 'Análise semanal'}</h1>
            </div>
            <div className="page__head-actions">
              <div className="an-week-pick" ref={menuSemanasRef}>
                <button
                  className="btn btn--secondary btn--md an-week-pick__button"
                  type="button"
                  aria-haspopup="listbox"
                  aria-expanded={menuSemanasAberto}
                  onClick={() => setMenuSemanasAberto((aberto) => !aberto)}
                >
                  <Ic d={ICONS.calendar} />
                  {geral ? 'Visão geral' : 'Escolher semana'}
                  <Ic d={ICONS.chevron} />
                </button>
                {menuSemanasAberto && (
                  <div className="an-week-pick__menu" role="listbox" aria-label="Semanas disponíveis">
                    <button
                      type="button"
                      role="option"
                      aria-selected={geral}
                      className={geral ? 'is-selected' : ''}
                      onClick={() => {
                        setSemanaSelecionada('overall');
                        setMenuSemanasAberto(false);
                      }}
                    >
                      <span>Visão geral</span>
                      <small>Todo o período</small>
                    </button>
                    {semanas.map((semana, indice) => (
                      <button
                        type="button"
                        role="option"
                        aria-selected={semana.inicioIso === semanaSelecionada}
                        className={semana.inicioIso === semanaSelecionada ? 'is-selected' : ''}
                        key={semana.inicioIso}
                        onClick={() => {
                          setSemanaSelecionada(semana.inicioIso);
                          setMenuSemanasAberto(false);
                        }}
                      >
                        <span>{semana.rotulo}</span>
                        {indice === 0 && <small>Semana atual</small>}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              {!geral && !fechada && (
                <Link className="btn btn--secondary btn--md" to="/todo">
                  Encerrar semana no To Do
                </Link>
              )}
            </div>
          </header>

          {!geral && analise.dataStatus === 'PARTIAL' && (
            <div className="card an-card" style={{ marginBottom: 'var(--space-md)' }}>
              Esta semana foi encerrada antes do novo histórico analítico. Os dados disponíveis
              foram reconstruídos e podem estar incompletos.
            </div>
          )}

          {analise.erro && (
            <div className="card an-card" style={{ marginBottom: 'var(--space-md)' }}>
              <div className="an-card__head">
                <span className="an-card__title">Não foi possível carregar a análise</span>
                <button className="btn btn--secondary btn--sm" onClick={analise.recarregar}>
                  Tentar de novo
                </button>
              </div>
              {explicarErro(analise.erro)}
            </div>
          )}

          <div className="an-grid">
            <div className="card an-card an-wide">
              <div className="an-card__head">
                <span className="an-card__title">
                  Agendado, estimado e executado{geral && ' por dia da semana'}
                </span>
                <div className="an-legend">
                  {SERIES.map((s) => (
                    <span className="an-legend__item" key={s.chave}>
                      <span className="an-legend__sw" style={{ background: s.cor }} />{s.rotulo}
                    </span>
                  ))}
                </div>
              </div>
              {carregando && <Vazio>Calculando…</Vazio>}
              {!carregando && analise.erro && <Vazio>Dados indisponíveis.</Vazio>}
              {pronto && !analise.temDados && (
                <Vazio>Nada agendado nem executado {geral ? 'neste período' : 'nesta semana'}.</Vazio>
              )}
              {pronto && analise.temDados && <DeviationChart dados={analise.desvio} />}
              {/* O gráfico é por DIA, então tarefa sem data não cabe nele — mas
                  entra no total estimado. Sem esta linha, o total pareceria não
                  bater com as barras. */}
              {pronto && analise.estimadoSemData > 0 && (
                <p className="an-card__hint" style={{ marginTop: 'var(--space-sm)' }}>
                  Mais {horas(analise.estimadoSemData)} estimadas em{' '}
                  {analise.tarefasSemDataComEstimativa}{' '}
                  {analise.tarefasSemDataComEstimativa === 1 ? 'tarefa sem data' : 'tarefas sem data'},
                  que contam no total mas não têm dia no gráfico.
                </p>
              )}
            </div>

            <div className="card an-card an-wide">
              <div className="an-card__head">
                <span className="an-card__title">Execução por categoria</span>
                <span className="an-card__hint">
                  {geral ? 'tarefas desde a criação da conta' : 'tarefas previstas para a semana'}
                </span>
              </div>
              {carregando && <Vazio>Calculando…</Vazio>}
              {!carregando && analise.erro && <Vazio>Dados indisponíveis.</Vazio>}
              {pronto && !analise.execucaoPorCategoria.length && (
                <Vazio>Não há tarefas com data {geral ? 'neste período' : 'nesta semana'}.</Vazio>
              )}
              {pronto && analise.execucaoPorCategoria.length > 0 && (
                <CategoryExecution categorias={analise.execucaoPorCategoria} />
              )}
            </div>

            <div className="card an-card">
              <div className="an-card__head">
                <span className="an-card__title">Tempo por categoria</span>
                <div className="an-card__switch" role="group" aria-label="Grandeza do tempo por categoria">
                  <button className={categoriaModo === 'estimado' ? 'is-on' : ''}
                          onClick={() => setCategoriaModo('estimado')}>Estimado</button>
                  <button className={categoriaModo === 'executado' ? 'is-on' : ''}
                          onClick={() => setCategoriaModo('executado')}>Executado</button>
                </div>
              </div>
              {carregando && <Vazio>Calculando…</Vazio>}
              {!carregando && analise.erro && <Vazio>Dados indisponíveis.</Vazio>}
              {pronto && !(categoriaModo === 'estimado' ? analise.categorias : analise.categoriasExecutadas).length && (
                <Vazio>Defina o tempo estimado das tarefas para ver a distribuição.</Vazio>
              )}
              {pronto && (categoriaModo === 'estimado' ? analise.categorias : analise.categoriasExecutadas).length > 0 && (
                <CategoryChart dados={categoriaModo === 'estimado' ? analise.categorias : analise.categoriasExecutadas} />
              )}
            </div>

            <div className="card an-card">
              <div className="an-card__head">
                <span className="an-card__title">{geral ? 'Conclusão geral' : 'Conclusão da semana'}</span>
              </div>
              {carregando && <Vazio>Calculando…</Vazio>}
              {!carregando && analise.erro && <Vazio>Dados indisponíveis.</Vazio>}
              {pronto && !analise.temTarefas && <Vazio>Sem tarefas {geral ? 'neste período' : 'nesta semana'}.</Vazio>}
              {pronto && analise.temTarefas && <RateRing conclusao={analise.conclusao} />}
            </div>

            <div className="card an-card an-wide">
              <div className="an-card__head">
                <span className="an-card__title">Cobertura do planejamento</span>
                <span className="an-card__hint">agenda ÷ estimativa</span>
              </div>
              {pronto && analise.totalEstimado <= 0 && <Vazio>Defina estimativas para medir a cobertura.</Vazio>}
              {pronto && analise.totalEstimado > 0 && (
                <div className="progress">
                  <div className="progress__head">
                    <span className="progress__label">
                      {horas(analise.totalAgendado)} agendadas de {horas(analise.totalEstimado)} estimadas
                    </span>
                    <span className="progress__value">{Math.round(analise.coberturaPlanejamento * 100)}%</span>
                  </div>
                  <div className="progress__track">
                    <div className="progress__fill" style={{ width: `${Math.min(100, analise.coberturaPlanejamento * 100)}%` }} />
                  </div>
                </div>
              )}
            </div>

            {pronto && analise.maioresDesvios.length > 0 && (
              <div className="card an-card an-wide">
                <div className="an-card__head">
                  <span className="an-card__title">Maiores desvios por tarefa</span>
                  <span className="an-card__hint">executado − estimado</span>
                </div>
                <div className="an-deviations">
                  {analise.maioresDesvios.map((t) => (
                    <a className="an-deviation" href={`/tasks/${t.id}`} key={t.id}>
                      <span>{t.titulo}</span>
                      <small>{horas(t.estimado)} estimadas · {horas(t.executado)} executadas</small>
                      <strong className={t.desvio > 0 ? 'is-over' : ''}>
                        {t.desvio >= 0 ? '+' : '−'}{horas(Math.abs(t.desvio))}
                      </strong>
                    </a>
                  ))}
                </div>
              </div>
            )}

            <div className="card an-card an-wide">
              <div className="an-card__head">
                <span className="an-card__title">Resumo e insights</span>
                <span className="an-card__hint">
                  {geral ? 'Gerado a partir de todo o período' : 'Gerado a partir da sua semana'}
                </span>
              </div>
              {carregando && <Vazio>Calculando…</Vazio>}
              {!carregando && analise.erro && <Vazio>Dados indisponíveis.</Vazio>}
              {pronto && !insights.length && (
                <Vazio>Ainda não há dados suficientes {geral ? 'neste período' : 'nesta semana'}.</Vazio>
              )}
              {pronto && insights.length > 0 && (
                <div className="an-insights">
                  {insights.map((i, idx) => (
                    <div className="an-insight" key={idx}>
                      <span className={`an-insight__ic an-insight__ic--${i.tipo}`}><Ic d={i.icone} /></span>
                      <span className="an-insight__text">{i.texto}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
