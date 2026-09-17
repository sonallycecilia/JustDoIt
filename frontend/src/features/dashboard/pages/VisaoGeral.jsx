// Aba "Visão geral" (home semanal): saudação, resumo da semana, tarefas de
// hoje priorizadas e atalhos.
//
// As estatísticas vêm do backend via useAnaliseSemanal — o dashboard antigo
// somava totais guardados em localStorage (FOCO_DIARIO / TEMPO_DIARIO), que a
// regra de ouro do app React não permite.
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Ic, { ICONS } from '@/components/Ic';
import Sidebar from '@/components/Sidebar';
import { useAnaliseSemanal } from '@/features/dashboard/hooks/useAnalytics';
import { useCategorias } from '@/features/categories/hooks/useCategories';
import { useConta } from '@/features/auth/hooks/useConta';
import { useRemoverTarefa, useTarefas, useToggleDone } from '@/features/tasks/hooks/useTasks';
import { lerSessao } from '@/api/session';
import * as Priority from '@/features/tasks/lib/priority';
import RecurringDeleteModal from '@/features/tasks/components/RecurringDeleteModal';
import { capitalizarNome, dataCurta, horas, saudacao } from '@/lib/utils';

const ATALHOS = [
  { to: '/calendario', icon: ICONS.calendar, titulo: 'Calendário', desc: 'Blocos da semana' },
  { to: '/todo', icon: ICONS.todo, titulo: 'To Do', desc: 'Lista priorizada' },
  { to: '/analise', icon: ICONS.analytics, titulo: 'Análise', desc: 'Desvio semanal' },
  { to: '/configuracoes', icon: ICONS.settings, titulo: 'Configurações', desc: 'Perfil e categorias' },
];

// De onde veio o tempo de hoje. As duas formas de registrar trabalho (Pomodoro e
// cronômetro) somam no total, então dizer só "em N ciclos" seria falso para quem
// usa o cronômetro.
export function origemDoTempo({ ciclos, cronometroMinutos }) {
  const partes = [];
  if (ciclos) partes.push(`${ciclos} ${ciclos === 1 ? 'ciclo' : 'ciclos'} de Pomodoro`);
  if (cronometroMinutos > 0) partes.push(`${horas(cronometroMinutos / 60)} no cronômetro`);
  if (!partes.length) return 'nenhum tempo registrado hoje';
  return partes.join(' · ');
}

export default function VisaoGeral() {
  const navigate = useNavigate();

  const { data: categorias } = useCategorias();
  const { data: tarefas, isLoading, isError, refetch } = useTarefas(categorias);
  const { data: usuario } = useConta();
  const analise = useAnaliseSemanal(tarefas);

  const toggleDone = useToggleDone();
  const remover = useRemoverTarefa();
  const [excluindo, setExcluindo] = useState(null);
  const [erroExclusao, setErroExclusao] = useState('');
  const [semDataAberta, setSemDataAberta] = useState(false);

  // Atrasadas entram junto das de hoje — são pendências do dia na prática.
  const doDia = useMemo(
    () => (tarefas || []).filter((t) => ['today', 'past'].includes(t.quando)).sort(Priority.comparar),
    [tarefas],
  );
  const tarefasSemData = useMemo(
    () => (tarefas || []).filter((t) => !t.dataIso).sort(Priority.comparar),
    [tarefas],
  );

  const primeiroNome = capitalizarNome(usuario?.name || lerSessao()?.name || '').split(' ')[0];
  // Aqui a referência é o ESTIMADO das tarefas, não o agendado do calendário:
  // a Visão Geral é sobre tarefas, e a estimativa existe mesmo para quem não usa
  // a agenda. A Análise mostra as duas grandezas lado a lado.
  const execPct = analise.totalEstimado ? Math.round((analise.totalExecutado / analise.totalEstimado) * 100) : 0;
  const diff = analise.totalExecutado - analise.totalEstimado;

  return (
    <div className="app">
      <Sidebar ativa="dashboard" />

      <main className="app__main">
        <div className="page">
          <header className="page__head">
            <div>
              <div className="page__eyebrow">{dataCurta()} · semana de {analise.semana.rotulo}</div>
              <h1 className="page__title">
                {saudacao()}{primeiroNome ? <>, <em>{primeiroNome}.</em></> : '.'}
              </h1>
            </div>
            <div className="page__head-actions">
              <Link className="btn btn--secondary btn--md" to="/calendario">Ver semana</Link>
              <Link className="btn btn--primary btn--md" to="/tasks/nova">+ Nova tarefa</Link>
            </div>
          </header>

          <section className="section">
            {analise.erro && (
              <div className="card" style={{ padding: 'var(--space-md)', marginBottom: 'var(--space-md)' }}>
                Não foi possível carregar as estatísticas da semana.{' '}
                <button className="link" onClick={analise.recarregar}>Tentar de novo</button>
              </div>
            )}
            <div className="stats-grid">
              <div className="stat">
                <span className="stat__label"><Ic d={ICONS.checkCircle} /> Concluídas</span>
                <span className="stat__value">
                  {analise.conclusao.feitas} <small>/ {analise.conclusao.total}</small>
                </span>
                <span className="stat__hint">tarefas desta semana</span>
              </div>

              <div className="stat">
                <span className="stat__label"><Ic d={ICONS.clock} /> Tempo hoje</span>
                <span className="stat__value">{horas(analise.hoje.minutos / 60)}</span>
                <span className="stat__hint">{origemDoTempo(analise.hoje)}</span>
              </div>

              <div className="stat stat--wide">
                <span className="stat__label"><Ic d={ICONS.analytics} /> Tempo executado</span>
                <div className="progress" style={{ marginTop: 6 }}>
                  <div className="progress__head">
                    <span className="progress__label">
                      {horas(analise.totalEstimado)} estimadas · {horas(analise.totalExecutado)} executadas
                    </span>
                    <span className="progress__value">{execPct}%</span>
                  </div>
                  <div className="progress__track">
                    <div className="progress__fill" style={{ width: `${Math.min(execPct, 100)}%` }} />
                  </div>
                </div>
                <div className="stat__hint stat__hint--undated">
                  {analise.totalEstimado
                    ? `desvio de ${diff >= 0 ? '+' : '−'}${horas(Math.abs(diff))} esta semana`
                    : 'defina o tempo estimado das tarefas para acompanhar o desvio'}
                  {analise.totalInferido > 0 && (
                    <> · {horas(analise.totalMedido)} medidas e {horas(analise.totalInferido)} inferidas ao concluir</>
                  )}
                  {/* Tarefa sem data conta no total, mas não tem dia no gráfico
                      da Análise. Dizer de onde vem essa parte evita a impressão
                      de que os dois números se contradizem. */}
                  {analise.tarefasSemData > 0 && (
                    <> · {analise.tarefasSemData}{' '}
                      {analise.tarefasSemData === 1 ? 'tarefa sem data' : 'tarefas sem data'}
                      {analise.estimadoSemData > 0 && ` (${horas(analise.estimadoSemData)} estimadas)`}</>
                  )}
                  {analise.tarefasSemData > 0 && (
                    <button
                      type="button"
                      className={`stat__expand${semDataAberta ? ' is-open' : ''}`}
                      aria-label={semDataAberta ? 'Ocultar tarefas sem data' : 'Ver tarefas sem data'}
                      aria-expanded={semDataAberta}
                      onClick={() => setSemDataAberta((aberta) => !aberta)}
                    >
                      <Ic d={ICONS.plus} />
                    </button>
                  )}
                </div>
                {semDataAberta && tarefasSemData.length > 0 && (
                  <div className="stat__undated-list">
                    {tarefasSemData.map((t) => (
                      <button
                        type="button"
                        className="stat__undated-task"
                        key={t.id}
                        onClick={() => navigate(`/tasks/${t.id}`)}
                      >
                        <span className={`stat__undated-status${t.done ? ' is-done' : ''}`} />
                        <span className={`stat__undated-title${t.done ? ' is-done' : ''}`}>{t.titulo}</span>
                        {t.duracaoMin > 0 && <small>{horas(t.duracaoMin / 60)}</small>}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </section>

          <section className="section">
            <div className="row row--between" style={{ marginBottom: 'var(--space-md)' }}>
              <h2 className="section-title" style={{ margin: 0 }}>Hoje, priorizadas</h2>
              <Link className="link" to="/todo">Ver todas →</Link>
            </div>
            <div className="card">
              <div className="tasklist">
                {isLoading && (
                  <div style={{ padding: 'var(--space-md)', color: 'var(--color-text-subtle)' }}>
                    Carregando tarefas…
                  </div>
                )}

                {isError && (
                  <div style={{ padding: 'var(--space-md)', color: 'var(--color-text-subtle)' }}>
                    Sem conexão com o servidor.{' '}
                    <button className="link" type="button" onClick={() => refetch()}>Tentar de novo</button>
                  </div>
                )}

                {!isLoading && !isError && doDia.length === 0 && (
                  <div style={{ padding: 'var(--space-md)', color: 'var(--color-text-subtle)' }}>
                    Nenhuma tarefa para hoje.
                  </div>
                )}

                {doDia.map((t) => (
                  <div className={`task ${t.done ? 'is-done' : ''}`} key={t.id}>
                    <button
                      className="task__check"
                      aria-label="Concluir tarefa"
                      onClick={() => toggleDone.mutate({ id: t.id, concluir: !t.done })}
                    >
                      <Ic d={ICONS.check} strokeWidth={3} />
                    </button>
                    <div className="task__main" style={{ cursor: 'pointer' }} onClick={() => navigate(`/tasks/${t.id}`)}>
                      <div className="task__title">{t.titulo}</div>
                      <div className="task__meta">
                        <span className="task__cat">
                          <span className="task__cat-dot" style={{ background: t.catCor }} />
                          {t.cat}
                        </span>
                        {t.hora && <span className="task__time">{t.hora}</span>}
                        {/* Estimativa da tarefa (vem no próprio GET /tasks, via
                            estimatedMinutes: nenhuma requisição a mais). É a
                            grandeza que alimenta o card "Tempo executado" ali em
                            cima, então vê-la por tarefa mostra de onde vem o
                            total da semana. */}
                        {t.duracaoMin > 0 && (
                          <span className="task__est" title="Tempo estimado">
                            tempo {horas(t.duracaoMin / 60)}
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="task__right">
                      <span className={`badge badge--${Priority.normalizar(t.prioridade)}`}>
                        {Priority.ROTULO[Priority.normalizar(t.prioridade)]}
                      </span>
                      <button
                        className="task__del"
                        aria-label="Excluir tarefa"
                        title="Excluir tarefa"
                        disabled={remover.isPending}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (t.seriesId || t.cycleType) {
                            setErroExclusao('');
                            setExcluindo(t);
                          } else {
                            remover.mutate({ id: t.id });
                          }
                        }}
                      >
                        <Ic d={ICONS.trash} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </section>

          <section className="section">
            <h2 className="section-title">Atalhos</h2>
            <div className="shortcut-grid">
              {ATALHOS.map((a) => (
                <Link className="shortcut" key={a.to} to={a.to}>
                  <span className="shortcut__ic"><Ic d={a.icon} /></span>
                  <span>
                    <span className="shortcut__title">{a.titulo}</span><br />
                    <span className="shortcut__desc">{a.desc}</span>
                  </span>
                </Link>
              ))}
            </div>
          </section>
        </div>
      </main>
      <RecurringDeleteModal
        tarefa={excluindo}
        processando={remover.isPending}
        erro={erroExclusao}
        onFechar={() => !remover.isPending && setExcluindo(null)}
        onEscolher={(scope) => remover.mutate({
          id: excluindo.id, scope, seriesId: excluindo.seriesId,
        }, {
          onSuccess: () => setExcluindo(null),
          onError: (e) => setErroExclusao(e.message || 'Não foi possível excluir a tarefa.'),
        })}
      />
    </div>
  );
}
