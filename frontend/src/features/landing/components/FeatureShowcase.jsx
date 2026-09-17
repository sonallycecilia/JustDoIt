// Vitrine animada da landing — substitui o mock estático "Hoje".
// Carrossel estilo menu: a cena em foco fica centralizada e as vizinhas
// aparecem menores e esmaecidas nas laterais. Passa sozinha em looping pelos
// SEIS módulos de tarefa (Foco, Subtarefas, Ciclo, Prioridade, Tempo, Notas)
// mais as Categorias.
//
// FIDELIDADE: cada cena usa a MESMA marcação e as MESMAS classes do módulo real
// do `TaskEditor` (.pomo, .subtask, .cycle-opts, .prio-picker, .timer,
// .notes-area) e do `CategorySelect` (.cat-filter*). Nada de componente paralelo
// com visual próprio: mexeu no CSS do módulo, a vitrine acompanha de graça.
// O que `landing-showcase.css` faz é só encolher isso para caber no card —
// nunca redesenhar.
//
// Os timers são só de demonstração: rodam acelerados para a cena caber em
// poucos segundos. A lógica de verdade vive em TaskEditor/useTaskDetail.
import { useEffect, useState } from 'react';
import Ic, { ICONS } from '@/components/Ic';
import * as Priority from '@/features/tasks/lib/priority';
import { TIPOS, rotuloCiclo } from '@/features/tasks/lib/cycle';
import { formatarMinSeg, formatarTempo, pct } from '@/lib/utils';

const DURACAO_CENA = 5500; // ms que cada cena fica em foco

// Respeita quem pediu menos movimento no sistema: sem auto-avanço e sem loop.
function usaMenosMovimento() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

// Avança um índice em looping enquanto `animar` estiver ligado. Todas as cenas
// que só trocam de item usam isto em vez de repetir o mesmo setInterval.
function useCiclagem(total, intervalo, animar) {
  const [i, setI] = useState(0);
  useEffect(() => {
    if (!animar) return;
    const h = setInterval(() => setI((n) => (n + 1) % total), intervalo);
    return () => clearInterval(h);
  }, [total, intervalo, animar]);
  return i;
}

/* ── Cena 1: Foco (Pomodoro) ──────────────────────────────────────────────── */
const FOCO_TOTAL = 25 * 60;

function CenaFoco({ animar }) {
  // 25 min comprimidos em ~6,5s: 12 segundos "reais" a cada tick de 50ms.
  const [restante, setRestante] = useState(FOCO_TOTAL);

  useEffect(() => {
    if (!animar) return;
    const h = setInterval(() => {
      setRestante((s) => (s <= 12 ? FOCO_TOTAL : s - 12));
    }, 50);
    return () => clearInterval(h);
  }, [animar]);

  const p = ((FOCO_TOTAL - restante) / FOCO_TOTAL) * 100;
  const ciclo = Math.min(4, Math.floor(p / 25) + 1);

  return (
    <div className="pomo">
      <div className="pomo__ring" style={{ '--pct': p.toFixed(1) }}>
        <div className="pomo__inner">
          <div className="pomo__time">{formatarMinSeg(restante)}</div>
          <div className="pomo__phase">Foco · ciclo {ciclo}/4</div>
        </div>
      </div>
      <div className="pomo__side">
        <p className="text-soft" style={{ margin: 0 }}>Blocos de 25 minutos com pausas curtas.</p>
        <div className="pomo__actions">
          <button className="btn btn--primary btn--sm" type="button" tabIndex={-1}>Pausar</button>
          <button className="btn btn--ghost btn--sm" type="button" tabIndex={-1}>Pular fase</button>
        </div>
      </div>
    </div>
  );
}

/* ── Cena 2: Subtarefas ───────────────────────────────────────────────────── */
const SUBS = ['Levantar requisitos', 'Montar o orçamento', 'Revisar com a equipe', 'Enviar para aprovação'];

function CenaSubtarefas({ animar }) {
  const [feitas, setFeitas] = useState(0);

  useEffect(() => {
    if (!animar) return;
    const h = setInterval(() => {
      // Completa uma a uma e recomeça depois de um respiro no 100%.
      setFeitas((n) => (n >= SUBS.length + 1 ? 0 : n + 1));
    }, 900);
    return () => clearInterval(h);
  }, [animar]);

  const marcadas = Math.min(feitas, SUBS.length);
  const p = pct(marcadas, SUBS.length);

  return (
    <div>
      <div className="subtasks__progress">
        <div className="progress">
          <div className="progress__track">
            <div className={`progress__fill ${p === 100 ? 'progress__fill--success' : ''}`} style={{ width: `${p}%` }} />
          </div>
        </div>
        <span className="subtasks__count">{marcadas}/{SUBS.length}</span>
      </div>
      <div>
        {SUBS.map((s, i) => (
          <div className={`subtask ${i < marcadas ? 'is-done' : ''}`} key={s}>
            <span className="subtask__check"><Ic d={ICONS.check} strokeWidth={3} /></span>
            <span className="subtask__label">{s}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ── Cena 3: Ciclo de repetição ───────────────────────────────────────────── */
const MESES_CURTOS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
// Cenas mostram só os ciclos de período fixo: "Não repete" não tem próxima data
// e "Personalizado" abre um formulário que não cabe no card.
const CICLOS_DEMO = ['DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY'];
const DIAS_DO_CICLO = { DAILY: 1, WEEKLY: 7, BIWEEKLY: 14, MONTHLY: 30 };

function rotuloData(d) {
  return `${d.getDate()} ${MESES_CURTOS[d.getMonth()]}`;
}

function CenaCiclo({ animar }) {
  const i = useCiclagem(CICLOS_DEMO.length, 1600, animar);
  const ativo = CICLOS_DEMO[i];

  const proxima = new Date();
  proxima.setDate(proxima.getDate() + DIAS_DO_CICLO[ativo]);

  return (
    <div>
      <div className="cycle-opts">
        {TIPOS.map((t) => (
          <span key={t} className={`cycle-opt ${t === ativo ? 'is-on' : ''}`}>{rotuloCiclo(t)}</span>
        ))}
      </div>
      <p className="cycle-custom__summary">
        Ao concluir, volta sozinha em <strong>{rotuloData(proxima)}</strong>.
      </p>
    </div>
  );
}

/* ── Cena 4: Prioridade ───────────────────────────────────────────────────── */
function CenaPrioridade({ animar }) {
  const i = useCiclagem(Priority.NIVEIS.length, 1400, animar);
  const ativo = Priority.NIVEIS[i];

  return (
    <div>
      <div className="sc-tarefa">
        <span className="sc-tarefa__nome">Revisar a proposta</span>
        {/* Mesma pílula que aparece nos chips do editor — troca junto com a opção. */}
        <span className={`badge badge--${ativo}`} key={ativo}>{Priority.ROTULO[ativo]}</span>
      </div>
      <div className="prio-picker">
        {Priority.NIVEIS.map((n) => (
          <span
            key={n}
            className={`prio-opt ${n === ativo ? 'is-on' : ''}`}
            style={{ color: Priority.COR[n] }}
          >
            <span className="prio-opt__dot" style={{ background: Priority.COR[n] }} />
            <span style={{ color: 'var(--color-text-soft)' }}>{Priority.ROTULO[n]}</span>
          </span>
        ))}
      </div>
    </div>
  );
}

/* ── Cena 5: Cronômetro de execução ───────────────────────────────────────── */
function CenaTempo({ animar }) {
  // ~1h50 comprimida em poucos segundos: 47 segundos "reais" por tick de 40ms.
  const [seg, setSeg] = useState(0);

  useEffect(() => {
    if (!animar) return;
    const h = setInterval(() => setSeg((s) => (s >= 6600 ? 0 : s + 47)), 40);
    return () => clearInterval(h);
  }, [animar]);

  return (
    <div>
      <div className="timer">
        <div className="timer__display">{formatarTempo(seg)}</div>
        <div className="timer__actions">
          <button className="btn btn--primary btn--sm" type="button" tabIndex={-1}>Pausar</button>
          <button className="btn btn--secondary btn--sm" type="button" tabIndex={-1}>Zerar</button>
        </div>
      </div>
      <p className="sc-legenda">O tempo executado fica guardado na tarefa.</p>
    </div>
  );
}

/* ── Cena 6: Notas ────────────────────────────────────────────────────────── */
const NOTA = 'Confirmar o orçamento com o financeiro antes de enviar para o cliente.';

function CenaNotas({ animar }) {
  const [n, setN] = useState(0);

  useEffect(() => {
    if (!animar) return;
    // Digita letra a letra, segura o texto completo por um tempo e recomeça.
    const h = setInterval(() => setN((v) => (v > NOTA.length + 22 ? 0 : v + 1)), 55);
    return () => clearInterval(h);
  }, [animar]);

  return (
    <div className="sc-notas">
      {/* readOnly: é demonstração, não campo de verdade. */}
      <textarea className="notes-area" readOnly tabIndex={-1} value={NOTA.slice(0, n)} />
    </div>
  );
}

/* ── Cena 7: Categorias ───────────────────────────────────────────────────── */
const CATS_DEMO = [
  { id: 1, nome: 'Trabalho', cor: 'var(--color-cat-teal)' },
  { id: 2, nome: 'Estudos', cor: 'var(--color-cat-purple)' },
  { id: 3, nome: 'Casa', cor: 'var(--color-cat-sage)' },
  { id: 4, nome: 'Saúde', cor: 'var(--color-cat-rust)' },
];

function CenaCategorias({ animar }) {
  const i = useCiclagem(CATS_DEMO.length, 1500, animar);
  const ativa = CATS_DEMO[i];

  return (
    // O dropdown real abre em position:absolute; aqui ele fica sempre aberto e
    // no fluxo (ver .showcase__cena .cat-filter__menu no CSS).
    <div className="cat-filter">
      <span className="cat-filter__btn is-open">
        <span className="cat-filter__dot" style={{ background: ativa.cor }} />
        <span className="cat-filter__name">{ativa.nome}</span>
        <Ic d={ICONS.chevron} className="cat-filter__chevron" size={13} strokeWidth={2} />
      </span>
      <div className="cat-filter__menu">
        {CATS_DEMO.map((c) => (
          <span key={c.id} className={`cat-filter__item ${c.id === ativa.id ? 'is-on' : ''}`}>
            <span className="cat-filter__dot" style={{ background: c.cor }} />
            <span className="cat-filter__item-name">{c.nome}</span>
            {c.id === ativa.id && <span className="cat-filter__check"><Ic d={ICONS.check} /></span>}
          </span>
        ))}
      </div>
    </div>
  );
}

/* ── Vitrine ──────────────────────────────────────────────────────────────── */
// Os seis primeiros são, na ordem, os mesmos módulos do editor de tarefa.
const CENAS = [
  { id: 'foco', titulo: 'Foco', icone: ICONS.target, hint: 'POMODORO', legenda: 'Cronômetro de 25 minutos', Cena: CenaFoco },
  { id: 'subtarefas', titulo: 'Subtarefas', icone: ICONS.list, hint: 'PASSOS', legenda: 'Quebre a tarefa em partes', Cena: CenaSubtarefas },
  { id: 'ciclo', titulo: 'Ciclo', icone: ICONS.cycle, hint: 'RECORRÊNCIA', legenda: 'A tarefa volta sozinha', Cena: CenaCiclo },
  { id: 'prioridade', titulo: 'Prioridade', icone: ICONS.flag, hint: 'NÍVEIS', legenda: 'Saiba o que fazer primeiro', Cena: CenaPrioridade },
  { id: 'tempo', titulo: 'Tempo', icone: ICONS.clock, hint: 'CRONÔMETRO', legenda: 'Meça quanto tempo levou', Cena: CenaTempo },
  { id: 'notas', titulo: 'Notas', icone: ICONS.notes, hint: 'LEMBRETES', legenda: 'Anotações junto da tarefa', Cena: CenaNotas },
  { id: 'categorias', titulo: 'Categorias', icone: ICONS.folderTree, hint: 'ORGANIZAÇÃO', legenda: 'Cada tarefa no seu lugar', Cena: CenaCategorias },
];

// Distância circular até a cena em foco: a última precisa aparecer à ESQUERDA
// da primeira (-1) e não lá na ponta direita (+6).
function distanciaCircular(n, i, total) {
  let d = n - i;
  if (d > total / 2) d -= total;
  if (d < -total / 2) d += total;
  return d;
}

// Posição de cada card em relação ao que está em foco. O deslocamento é em %
// da largura do próprio card, então acompanha o clamp() da largura sem contas.
// Com sete cenas só as duas vizinhas espiam: as demais ficam invisíveis, senão
// o viewport viraria uma fileira de cards cortados.
function estiloDoCard(distancia) {
  const longe = Math.abs(distancia) > 1;
  const escala = distancia === 0 ? 1 : 0.82;
  return {
    transform: `translateX(calc(-50% + ${distancia * 80}%)) scale(${escala})`,
    // As cenas vizinhas continuam menores, mas não recebem transparência:
    // reduzir a opacidade também reduz o contraste do texto visível abaixo de AA.
    opacity: longe ? 0 : 1,
  };
}

export default function FeatureShowcase() {
  const [i, setI] = useState(0);
  const [pausado, setPausado] = useState(false);
  const estatico = usaMenosMovimento();

  const irPara = (n) => setI(((n % CENAS.length) + CENAS.length) % CENAS.length);

  useEffect(() => {
    if (estatico || pausado) return;
    const h = setTimeout(() => setI((n) => (n + 1) % CENAS.length), DURACAO_CENA);
    return () => clearTimeout(h);
  }, [i, pausado, estatico]);

  return (
    // Passar o mouse pausa o rodízio, para dar tempo de ler a cena.
    <div className="showcase" onMouseEnter={() => setPausado(true)} onMouseLeave={() => setPausado(false)}>
      <button className="showcase__nav showcase__nav--prev" type="button" onClick={() => irPara(i - 1)} aria-label="Cena anterior">
        <Ic d={ICONS.chevronLeft} />
      </button>

      <div className="showcase__viewport">
        {CENAS.map((c, n) => {
          const ativo = n === i;
          const { Cena } = c;
          return (
            <article
              key={c.id}
              className={`showcase__card ${ativo ? 'is-active' : ''}`}
              style={estiloDoCard(distanciaCircular(n, i, CENAS.length))}
              aria-hidden={!ativo}
            >
              <div className="showcase__head">
                <span className="showcase__ic"><Ic d={c.icone} /></span>
                <span className="showcase__title">{c.titulo}</span>
                <span className="showcase__hint">{c.hint}</span>
              </div>
              {/* O palco é decorativo: o CSS corta o pointer-events e os
                  controles das cenas ficam fora da ordem de tabulação. */}
              <div className="showcase__stage" aria-hidden="true">
                {/* Só a cena em foco anima — evita sete timers rodando à toa. */}
                <div className="showcase__cena">
                  <Cena animar={ativo && !estatico} />
                </div>
              </div>
            </article>
          );
        })}
      </div>

      <button className="showcase__nav showcase__nav--next" type="button" onClick={() => irPara(i + 1)} aria-label="Próxima cena">
        <Ic d={ICONS.chevronRight} />
      </button>

      <div className="showcase__dots">
        {CENAS.map((c, n) => (
          <button
            key={c.id}
            type="button"
            className={`showcase__dot ${n === i ? 'is-on' : ''}`}
            aria-label={`Ver ${c.titulo}`}
            aria-current={n === i}
            onClick={() => irPara(n)}
          />
        ))}
      </div>
      <p className="showcase__legenda">{CENAS[i].legenda}</p>
    </div>
  );
}
