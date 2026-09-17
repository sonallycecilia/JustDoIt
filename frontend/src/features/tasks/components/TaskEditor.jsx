// Editor de tarefa — corpo compartilhado entre a página /tasks/:id e o drawer
// lateral do calendário. Antes cada um tinha a sua versão; agora existe uma
// fonte da verdade só, então o que muda aqui aparece nos dois.
//
// SALVAMENTO: em modo edição (taskId definido) TUDO persiste sozinho, sem botão
// — título/descrição com debounce, o resto na hora. Em modo criação não há id
// para gravar, então os campos ficam locais e o caller salva via `salvar()`
// exposto por `onPronto`.
import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import Ic, { ICONS } from '@/components/Ic';
import DatePicker from '@/components/DatePicker';
import TimePicker from '@/components/TimePicker';
import CategorySelect from '@/features/categories/components/CategorySelect';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { useCategorias } from '@/features/categories/hooks/useCategories';
import { invalidarMetricas, useAtualizarTarefa, useCriarTarefa, useTarefa, useToggleDone } from '@/features/tasks/hooks/useTasks';
import {
  MODULOS_PADRAO, cicloParaApi,
  useCiclo, useCriarSubtarefa, useCronometroAtivo, useIniciarCronometro, useModulos, useNota,
  usePararCronometro,
  useRegistrarCicloFoco, useRemoverSubtarefa, useSalvarCiclo, useSalvarModulos,
  useApagarNota, useSalvarNota, useSalvarTempoEstimado, useSubtarefas, useTimer,
  useToggleSubtarefa, useZerarTempo,
} from '@/features/tasks/hooks/useTaskDetail';
import { useRascunhoTarefa } from '@/features/tasks/hooks/useRascunhoTarefa';
import * as Priority from '@/features/tasks/lib/priority';
import { TIPOS, customValido, fimPrevisto, rotuloCiclo } from '@/features/tasks/lib/cycle';
import { dataIso, dataRelativa, deIso, fmtHoraMin, formatarMinSeg, formatarTempo, hoje, pct } from '@/lib/utils';
import { toast } from '@/lib/toast';

const FOCO_MIN = 25;
const PAUSA_MIN = 5;
const LEMBRETES = [
  { minutos: 15, rotulo: '15 min antes' },
  { minutos: 30, rotulo: '30 min antes' },
  { minutos: 60, rotulo: '1 hora antes' },
  { minutos: 120, rotulo: '2 horas antes' },
  { minutos: 1440, rotulo: '1 dia antes' },
  { minutos: 10080, rotulo: '1 semana antes' },
];
const LABEL_MOD = { foco: 'Foco', ciclo: 'Ciclo', prioridade: 'Prioridade', tempo: 'Tempo', notas: 'Notas', subtarefas: 'Subtarefas' };
const MOD_ICONE = {
  foco: ICONS.target, ciclo: ICONS.cycle, prioridade: ICONS.flag,
  tempo: ICONS.clock, notas: ICONS.notes, subtarefas: ICONS.list,
};

const TaskEditor = forwardRef(function TaskEditor({ taskId, compacto = false, onCriada, onSalvo, isReadOnly = false }, ref) {
  const qc = useQueryClient();
  const { data: categorias } = useCategorias();
  const { data: tarefa, isLoading: carregandoTarefa } = useTarefa(taskId, categorias);

  const { data: subsServidor } = useSubtarefas(taskId);
  const { data: notaServidor } = useNota(taskId);
  const { data: modsServidor } = useModulos(taskId);
  const { data: cicloServidor } = useCiclo(taskId);
  const { data: timerServidor } = useTimer(taskId);
  const { data: cronAtivo, isFetched: cronAtivoConsultado } = useCronometroAtivo(Boolean(taskId));
  const segundosServidor = timerServidor?.segundos;

  const criarTarefa = useCriarTarefa();
  const atualizarTarefa = useAtualizarTarefa();
  const toggleDone = useToggleDone();
  const criarSub = useCriarSubtarefa(taskId);
  const toggleSub = useToggleSubtarefa(taskId);
  const removerSub = useRemoverSubtarefa(taskId);
  const salvarNota = useSalvarNota(taskId);
  const apagarNota = useApagarNota(taskId);
  const salvarMods = useSalvarModulos(taskId);
  const salvarCiclo = useSalvarCiclo(taskId);
  const iniciarCronometro = useIniciarCronometro(taskId);
  const pararCronometro = usePararCronometro(taskId);
  const zerarTempo = useZerarTempo(taskId);
  const salvarTempoEstimado = useSalvarTempoEstimado(taskId);
  const registrarFoco = useRegistrarCicloFoco(taskId);

  const tituloRef = useRef(null);
  const descRef = useRef(null);
  const [done, setDone] = useState(false);
  const [dataSel, setDataSel] = useState(hoje());
  const [dataAberta, setDataAberta] = useState(false);
  const [hora, setHora] = useState(null); // { h, m } | null
  const [lembreteMinutosAntes, setLembreteMinutosAntes] = useState(null);
  const [horaAberta, setHoraAberta] = useState(false);
  const [prioridade, setPrioridade] = useState('normal');
  const [prioridadeAberta, setPrioridadeAberta] = useState(false);
  const [catId, setCatId] = useState(null);
  const [salvo, setSalvo] = useState(''); // '', 'ok', 'erro'

  const [modsLocal, setModsLocal] = useState({ ...MODULOS_PADRAO });
  const [subTogglelocal, setSubToggleLocal] = useState(false); // sem flag no backend
  const [cicloLocal, setCicloLocal] = useState('none');
  const [cicloCustom, setCicloCustom] = useState({ count: 12, unit: 'horas', occurrences: 7, startIso: null });
  const [dur, setDur] = useState({ h: 0, m: 0 });
  const [nota, setNota] = useState('');
  const [subsLocal, setSubsLocal] = useState([]);
  const [subInput, setSubInput] = useState('');

  // Guarda DE QUAL tarefa os campos não controlados foram preenchidos. Antes
  // era um booleano zerado por um segundo effect, e como effects rodam na ordem
  // de declaração, trocar de taskId com a tarefa nova já em cache preenchia
  // nada: o primeiro saía cedo (flag ainda true) e o segundo só limpava depois.
  const preenchidoDe = useRef(null);

  useEffect(() => {
    if (!taskId || !tarefa || preenchidoDe.current === taskId) return;
    preenchidoDe.current = taskId;
    if (tituloRef.current) tituloRef.current.textContent = tarefa.titulo;
    if (descRef.current) descRef.current.textContent = tarefa.descricao || '';
    setDone(tarefa.done);
    if (tarefa.dataIso) setDataSel(deIso(tarefa.dataIso));
    if (tarefa.hora) {
      const [h, m] = tarefa.hora.split(':').map(Number);
      setHora({ h, m });
    } else setHora(null);
    setLembreteMinutosAntes(tarefa.lembreteMinutosAntes ?? null);
    setPrioridade(tarefa.prioridade || 'normal');
  }, [taskId, tarefa]);

  useEffect(() => {
    if (tarefa && categorias) setCatId(tarefa.categoriaId || 'generico');
    else if (categorias && !catId) setCatId(categorias[0]?.id ?? null);
  }, [tarefa, categorias]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { if (modsServidor) setModsLocal(modsServidor); }, [modsServidor]);
  useEffect(() => {
    if (!cicloServidor) return;
    setCicloLocal(cicloServidor.tipo);
    if (cicloServidor.custom) setCicloCustom(cicloServidor.custom);
  }, [cicloServidor]);
  useEffect(() => {
    const min = timerServidor?.estimadoMin;
    if (min != null) setDur({ h: Math.floor(min / 60), m: min % 60 });
  }, [timerServidor?.estimadoMin]);
  useEffect(() => { if (notaServidor !== undefined) setNota(notaServidor); }, [notaServidor]);
  useEffect(() => { if ((subsServidor || []).length > 0) setSubToggleLocal(true); }, [subsServidor]);

  // ── Rascunho (só no modo criação) ─────────────────────────────────────────
  const rascunho = useRascunhoTarefa(!taskId);
  const rascunhoLido = useRef(false);

  // Restaura uma única vez, no monte. Roda ANTES de `categorias` chegar, então
  // o effect de categoria acima não sobrescreve o catId restaurado (ele só
  // preenche quando ainda não há nenhum).
  useEffect(() => {
    if (taskId || rascunhoLido.current) return;
    rascunhoLido.current = true;
    const r = rascunho.ler();
    if (!r) return;
    if (tituloRef.current) tituloRef.current.textContent = r.titulo || '';
    if (descRef.current) descRef.current.textContent = r.descricao || '';
    if (r.dataIso) setDataSel(deIso(r.dataIso));
    if (r.hora) {
      const [h, m] = r.hora.split(':').map(Number);
      setHora({ h, m });
    }
    if (r.lembreteMinutosAntes) setLembreteMinutosAntes(r.lembreteMinutosAntes);
    if (r.prioridade) setPrioridade(r.prioridade);
    if (r.categoriaId) setCatId(r.categoriaId);
    if (r.dur) setDur(r.dur);
    if (r.mods) setModsLocal(r.mods);
    if (r.ciclo) setCicloLocal(r.ciclo);
    if (r.cicloCustom) setCicloCustom(r.cicloCustom);
    if (r.nota) setNota(r.nota);
    if (r.subs?.length) { setSubsLocal(r.subs); setSubToggleLocal(true); }
    else if (r.subtarefasAberto) setSubToggleLocal(true);
  }, [taskId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Título e descrição vivem no DOM (contentEditable), por isso são lidos das
  // refs em vez de virem do estado.
  function montarRascunho() {
    return {
      titulo: (tituloRef.current?.textContent || '').trim(),
      descricao: (descRef.current?.textContent || '').trim(),
      categoriaId: catId,
      prioridade,
      dataIso: dataIso(dataSel),
      hora: hora ? fmtHoraMin(hora.h, hora.m) : null,
      lembreteMinutosAntes,
      dur,
      mods: modsLocal,
      ciclo: cicloLocal,
      cicloCustom,
      nota,
      subs: subsLocal,
      subtarefasAberto: subTogglelocal,
    };
  }

  function salvarRascunho() {
    if (!taskId) rascunho.agendar(montarRascunho());
  }

  // Campos controlados: o rascunho acompanha o estado. O texto não entra aqui
  // (não é estado) — ele chama salvarRascunho() direto no onInput.
  useEffect(() => {
    if (taskId || !rascunhoLido.current) return;
    salvarRascunho();
  }, [taskId, dataSel, hora, lembreteMinutosAntes, prioridade, catId, dur, modsLocal, cicloLocal, cicloCustom, nota, subsLocal, subTogglelocal]); // eslint-disable-line react-hooks/exhaustive-deps

  const subs = taskId ? (subsServidor || []) : subsLocal;
  const cats = categorias || [];
  const cat = cats.find((c) => c.id === catId) || cats[0];

  function flashSalvo() {
    setSalvo('ok');
    setTimeout(() => setSalvo(''), 1600);
  }

  function aoErro(e) {
    toast(e?.error || e?.message || 'Não foi possível salvar.', 'error');
    setSalvo('erro');
    setTimeout(() => setSalvo(''), 2500);
  }

  // ── Persistência automática (modo edição) ─────────────────────────────────
  // O estado do React só atualiza no próximo render, então quem chama passa a
  // mudança em `mudancas` em vez de confiar no state recém-setado.
  function persistir(mudancas = {}) {
    if (isReadOnly || !taskId) return;
    const titulo = (tituloRef.current?.textContent || '').trim();
    if (!titulo) return; // backend exige título; espera o usuário digitar
    const dados = {
      titulo,
      descricao: (descRef.current?.textContent || '').trim(),
      categoriaId: cat?.id,
      prioridade,
      dataIso: dataIso(dataSel),
      hora: hora ? fmtHoraMin(hora.h, hora.m) : null,
      lembreteMinutosAntes,
      ...mudancas,
    };
    atualizarTarefa.mutate({ id: taskId, dados }, {
      // `onSalvo` deixa o caller reagir ao que foi persistido — o drawer do
      // calendário usa para mover o bloco de tempo junto com a tarefa.
      onSuccess: () => { flashSalvo(); onSalvo?.(dados); },
      onError: aoErro,
    });
  }

  // Título e descrição são contentEditable não controlados: salvam com debounce.
  const textoTimer = useRef(null);
  function aoDigitarTexto() {
    if (isReadOnly) return;
    // Sem id não há o que atualizar no backend; o que foi digitado vai para o
    // rascunho local para não se perder se a página for deixada antes do POST.
    if (!taskId) { salvarRascunho(); return; }
    clearTimeout(textoTimer.current);
    textoTimer.current = setTimeout(() => persistir(), 700);
  }
  useEffect(() => () => clearTimeout(textoTimer.current), [isReadOnly]);

  function alternarDone() {
    if (isReadOnly) return;
    const novo = !done;
    setDone(novo);
    if (taskId) toggleDone.mutate({ id: taskId, concluir: novo }, { onSuccess: flashSalvo, onError: aoErro });
  }

  function mudarData(d) {
    if (isReadOnly) return;
    setDataSel(d);
    persistir({ dataIso: dataIso(d) });
  }

  function mudarHora(h, m) {
    if (isReadOnly) return;
    if (h === null || h === undefined) return;
    setHora({ h, m });
    persistir({ hora: fmtHoraMin(h, m) });
  }

  function limparHora() {
    if (isReadOnly) return;
    setHora(null);
    setLembreteMinutosAntes(null);
    persistir({ hora: null, lembreteMinutosAntes: null });
  }

  function mudarLembrete(valor) {
    const minutos = valor ? Number(valor) : null;
    setLembreteMinutosAntes(minutos);
    persistir({ lembreteMinutosAntes: minutos });
    if (minutos && typeof window.Notification !== 'undefined' && window.Notification.permission === 'default') {
      window.Notification.requestPermission().catch(() => {});
    }
  }

  function mudarCategoria(c) {
    if (isReadOnly) return;
    if (!c) return;
    setCatId(c.id);
    persistir({ categoriaId: c.id });
  }

  function mudarPrioridade(n) {
    if (isReadOnly) return;
    setPrioridade(n);
    setPrioridadeAberta(false);
    persistir({ prioridade: n });
  }

  // Duração estimada vive no /timer (o TaskRequest descarta o campo).
  function salvarDuracao(novo) {
    if (isReadOnly) return;
    salvarTempoEstimado.mutate(novo.h * 60 + novo.m, { onSuccess: flashSalvo, onError: aoErro });
  }

  function mudarDur(novo) {
    if (isReadOnly) return;
    setDur(novo);
    if (!taskId) return;
    salvarDuracao(novo);
  }

  const notaTimer = useRef(null);
  function aoDigitarNota(valor) {
    if (isReadOnly) return;
    setNota(valor);
    if (!taskId) return;
    clearTimeout(notaTimer.current);
    // Esvaziar a nota é DELETE: o PUT recusa content em branco (@NotBlank), e
    // com o `return` que havia aqui apagar o texto não chegava ao backend —
    // a nota voltava no próximo carregamento.
    notaTimer.current = setTimeout(() => (valor.trim()
      ? salvarNota.mutate(valor, { onSuccess: flashSalvo })
      : apagarNota.mutate(undefined, { onSuccess: flashSalvo, onError: aoErro })
    ), 800);
  }
  useEffect(() => () => clearTimeout(notaTimer.current), [isReadOnly]);

  function toggleModulo(mod) {
    if (isReadOnly) return;
    if (mod === 'subtarefas') { setSubToggleLocal((v) => !v); return; }
    const novos = { ...modsLocal, [mod]: !modsLocal[mod] };
    setModsLocal(novos);
    if (taskId) salvarMods.mutate(novos, { onSuccess: flashSalvo, onError: aoErro });
  }

  function valorCiclo(tipo = cicloLocal, custom = cicloCustom) {
    if (tipo === 'none') return 'none';
    if (tipo !== 'custom') return tipo;
    return { tipo: 'custom', custom, startTime: hora ? fmtHoraMin(hora.h, hora.m) : null };
  }

  function escolherCiclo(tipo) {
    if (isReadOnly) return;
    setCicloLocal(tipo);
    if (!taskId) return;
    if (tipo === 'custom') {
      if (customValido(cicloCustom)) salvarCiclo.mutate(valorCiclo('custom'), { onSuccess: flashSalvo, onError: aoErro });
      return;
    }
    salvarCiclo.mutate(valorCiclo(tipo), { onSuccess: flashSalvo, onError: aoErro });
  }

  const customTimer = useRef(null);
  function mudarCustom(mudancas) {
    if (isReadOnly) return;
    const novo = { ...cicloCustom, ...mudancas };
    setCicloCustom(novo);
    if (!taskId || cicloLocal !== 'custom' || !customValido(novo)) return;
    clearTimeout(customTimer.current);
    customTimer.current = setTimeout(() => salvarCiclo.mutate(valorCiclo('custom', novo), { onSuccess: flashSalvo }), 600);
  }
  useEffect(() => () => clearTimeout(customTimer.current), [isReadOnly]);

  // ── Cronômetro de execução ────────────────────────────────────────────────
  const [cronSegundos, setCronSegundos] = useState(0);
  const [cronRodando, setCronRodando] = useState(false);
  const [inicioPersistido, setInicioPersistido] = useState(null);
  const [cronErro, setCronErro] = useState('');
  const deltaNaoLogado = useRef(0);

  // `taskId` entra nas deps para ressincronizar ao trocar de tarefa: sem ele,
  // duas tarefas com o mesmo total de segundos não disparavam o efeito e o
  // mostrador ficava com o valor da anterior.
  useEffect(() => {
    if (!taskId || !cronAtivoConsultado) return;
    if (cronAtivo?.taskId === taskId) {
      setInicioPersistido(cronAtivo.startedAt);
      setCronRodando(true);
    } else {
      setInicioPersistido(null);
      setCronRodando(false);
      setCronSegundos(Number(segundosServidor ?? 0));
    }
  }, [cronAtivo, cronAtivoConsultado, segundosServidor, taskId]);

  useEffect(() => {
    if (taskId && !cronRodando && segundosServidor !== undefined) {
      setCronSegundos(Number(segundosServidor));
    }
  }, [cronRodando, segundosServidor, taskId]);

  useEffect(() => {
    if (!cronRodando) return;
    const inicioMs = inicioPersistido ? new Date(inicioPersistido).getTime() : null;
    const atualizar = () => {
      if (taskId && Number.isFinite(inicioMs)) {
        const decorridos = Math.max(0, Math.floor((Date.now() - inicioMs) / 1000));
        setCronSegundos(Number(segundosServidor ?? 0) + decorridos);
        return;
      }
      deltaNaoLogado.current += 1;
      setCronSegundos((s) => s + 1);
    };
    atualizar();
    const h = setInterval(() => {
      atualizar();
    }, 1000);
    return () => clearInterval(h);
  }, [cronRodando, inicioPersistido, segundosServidor, taskId]);

  async function toggleCron() {
    if (isReadOnly) return;
    setCronErro('');
    if (!taskId) {
      setCronRodando((rodando) => !rodando);
      return;
    }
    try {
      if (cronRodando) {
        const timer = await pararCronometro.mutateAsync();
        setCronSegundos(Number(timer?.actualSeconds ?? cronSegundos));
        setInicioPersistido(null);
        setCronRodando(false);
      } else {
        const ativo = await iniciarCronometro.mutateAsync();
        setInicioPersistido(ativo.startedAt);
        setCronRodando(true);
      }
    } catch (erro) {
      setCronErro(erro?.status === 409
        ? 'Já existe um cronômetro ativo em outra tarefa.'
        : 'Não foi possível sincronizar o cronômetro. Tente novamente.');
    }
  }

  async function resetCron() {
    if (isReadOnly) return;
    setCronErro('');
    if (!taskId) {
      setCronRodando(false);
      deltaNaoLogado.current = 0;
      setCronSegundos(0);
      return;
    }
    try {
      if (cronRodando) await pararCronometro.mutateAsync();
      await zerarTempo.mutateAsync();
      setInicioPersistido(null);
      setCronRodando(false);
      setCronSegundos(0);
    } catch {
      setCronErro('Não foi possível zerar o cronômetro.');
    }
  }

  // ── Pomodoro ──────────────────────────────────────────────────────────────
  // `focos` conta fases de foco concluídas — é o gatilho da gravação, não um
  // número exibido. Só cresce; o reset do timer não mexe nele.
  const [pomo, setPomo] = useState({ fase: 'foco', ciclo: 1, restante: FOCO_MIN * 60, rodando: false, focos: 0 });
  const inicioFocoRef = useRef(null);
  const focosGravados = useRef(0);
  // Ciclos concluídos em /tasks/nova. NÃO é contador de exibição (isso seria
  // dado de negócio no cliente) — é fila de escrita pendente: o endpoint é
  // /tasks/{id}/focus-sessions e o id só existe depois do POST, então o
  // `criar()` esvazia isto logo em seguida, igual faz com subtarefas e nota.
  const focoPendente = useRef([]);

  // O updater abaixo é PURO de propósito: só calcula o próximo estado. O
  // StrictMode invoca updaters duas vezes para expor impureza, e enquanto o
  // POST da sessão de foco morava aqui dentro cada ciclo gravava DUAS sessões
  // (inflando o foco da Análise). Quem tem efeito colateral é o effect abaixo,
  // que reage ao contador `focos` e é idempotente via `focosGravados`.
  useEffect(() => {
    if (!pomo.rodando) return;
    const h = setInterval(() => {
      setPomo((p) => {
        if (p.restante > 0) return { ...p, restante: p.restante - 1 };
        if (p.fase === 'foco') return { ...p, fase: 'pausa', restante: PAUSA_MIN * 60, focos: p.focos + 1 };
        return { ...p, fase: 'foco', ciclo: Math.min(p.ciclo + 1, 4), restante: FOCO_MIN * 60 };
      });
    }, 1000);
    return () => clearInterval(h);
  }, [pomo.rodando]);

  useEffect(() => {
    if (pomo.focos <= focosGravados.current) return;
    focosGravados.current = pomo.focos;
    const sessao = {
      focusMinutes: FOCO_MIN,
      startedAt: (inicioFocoRef.current || new Date(Date.now() - FOCO_MIN * 60_000)).toISOString().slice(0, 19),
      endedAt: new Date().toISOString().slice(0, 19),
    };
    inicioFocoRef.current = null; // a próxima fase de foco define o seu início
    // Com id vai direto; sem id entra na fila e o criar() grava depois.
    if (taskId) registrarFoco.mutate(sessao);
    else focoPendente.current.push(sessao);
  }, [pomo.focos]); // eslint-disable-line react-hooks/exhaustive-deps

  function togglePomo() {
    if (isReadOnly) return;
    // Fora do updater: marcar o início é efeito colateral (ver comentário acima).
    if (!pomo.rodando && pomo.fase === 'foco' && inicioFocoRef.current === null) {
      inicioFocoRef.current = new Date();
    }
    setPomo((p) => ({ ...p, rodando: !p.rodando }));
  }
  function pularFase() {
    if (isReadOnly) return;
    // Pular NÃO registra sessão: o ciclo não foi cumprido.
    setPomo((p) => (p.fase === 'foco'
      ? { ...p, fase: 'pausa', restante: PAUSA_MIN * 60 }
      : { ...p, fase: 'foco', ciclo: Math.min(p.ciclo + 1, 4), restante: FOCO_MIN * 60 }));
  }
  function resetPomo() {
    if (isReadOnly) return;
    inicioFocoRef.current = null;
    setPomo((p) => ({ fase: 'foco', ciclo: 1, restante: FOCO_MIN * 60, rodando: false, focos: p.focos }));
  }

  const pomoDur = (pomo.fase === 'foco' ? FOCO_MIN : PAUSA_MIN) * 60;
  const pomoPct = ((pomoDur - pomo.restante) / pomoDur) * 100;

  // ── Subtarefas ────────────────────────────────────────────────────────────
  const feitas = subs.filter((s) => s.done).length;
  const pctSubs = pct(feitas, subs.length);

  function adicionarSub() {
    if (isReadOnly) return;
    const titulo = subInput.trim();
    if (!titulo) return;
    setSubInput('');
    setSubToggleLocal(true);
    if (taskId) criarSub.mutate({ titulo, position: subs.length });
    else setSubsLocal((l) => [...l, { id: `tmp-${Date.now()}`, titulo, done: false }]);
  }

  function alternarSub(s) {
    if (isReadOnly) return;
    if (taskId) toggleSub.mutate(s.id);
    else setSubsLocal((l) => l.map((x) => (x.id === s.id ? { ...x, done: !x.done } : x)));
  }

  function excluirSub(s) {
    if (isReadOnly) return;
    if (taskId) removerSub.mutate(s.id);
    else setSubsLocal((l) => l.filter((x) => x.id !== s.id));
  }

  // ── Criação (só no modo /tasks/nova) ──────────────────────────────────────
  // Exposta ao caller via ref: não há id para autosalvar antes do POST.
  async function criar() {
    if (isReadOnly) return null;
    const titulo = (tituloRef.current?.textContent || '').trim();
    // Sem título não há POST — o backend exige o campo. Só mover o cursor para
    // o campo passava despercebido: o clique em "Registrar tarefa" não gerava
    // aviso, não navegava e nada aparecia na To Do, então parecia que o botão
    // estava quebrado em vez de faltar um dado.
    if (!titulo) {
      toast('Dê um nome à tarefa antes de registrar.', 'error');
      tituloRef.current?.focus();
      return null;
    }
    const dados = {
      titulo,
      descricao: (descRef.current?.textContent || '').trim(),
      categoriaId: cat?.id,
      prioridade,
      dataIso: dataIso(dataSel),
      hora: hora ? fmtHoraMin(hora.h, hora.m) : null,
      lembreteMinutosAntes,
    };
    const durMin = dur.h * 60 + dur.m;
    setCronRodando(false); // congela o cronômetro: o que for salvo é o que está na tela

    // O POST da tarefa fica SOZINHO no try: só ele decide se houve criação.
    // Enquanto os extras dividiam este bloco, um PUT de módulo que falhasse
    // levava tudo para o catch — a tarefa ficava no banco, a tela dizia que não
    // salvou e o usuário reenviava, criando uma segunda tarefa igual.
    let novoId;
    try {
      novoId = (await criarTarefa.mutateAsync(dados))?.id;
    } catch (e) {
      aoErro(e);
      return null;
    }

    // Resposta 2xx sem id: não há para onde navegar nem em que tarefa gravar os
    // extras. Antes isto caía no mesmo silêncio do título vazio — a tela ficava
    // parada em /tasks/nova sem dizer nada. Vira erro visível, e o rascunho
    // continua intacto para uma nova tentativa.
    if (!novoId) {
      aoErro(new Error('A tarefa foi enviada, mas o servidor não devolveu o identificador dela.'));
      return null;
    }

    // A tarefa existe no banco: o rascunho perdeu a razão de ser. Sai antes
    // dos extras de propósito — mesmo que algum falhe, não há mais rascunho
    // a restaurar, e sim uma tarefa a editar.
    rascunho.limpar();
    // Com o id definitivo, persiste o que foi configurado antes do POST —
    // nada fica só neste navegador.
    const pendencias = [];
    for (const [i, s] of subsLocal.entries()) {
      pendencias.push(
        api.post(endpoints.tasks.subtasks.create(novoId), { title: s.titulo, position: i })
          .then((criada) => (s.done && criada?.id
            ? api.patch(endpoints.tasks.subtasks.toggle(novoId, criada.id))
            : null)),
      );
    }
    if (nota.trim()) pendencias.push(api.put(endpoints.tasks.note(novoId), { content: nota }));
    if (cicloLocal !== 'none' && (cicloLocal !== 'custom' || customValido(cicloCustom))) {
      pendencias.push(api.put(endpoints.tasks.cycleConfig(novoId), cicloParaApi(valorCiclo())));
    }
    // UM PUT só carregando os dois campos do timer. Nada de somar um
    // PATCH /timer/log aqui: upsertTimer e logSeconds fazem find-or-create,
    // e dois writes concorrentes na espera abaixo criariam duas linhas de
    // timer para a mesma tarefa. O PUT faz merge parcial campo a campo.
    const segCron = deltaNaoLogado.current;
    if (durMin > 0 || segCron > 0) {
      const timer = {};
      if (durMin > 0) timer.estimatedMinutes = durMin;
      if (segCron > 0) timer.actualSeconds = segCron; // tarefa nova: define, não incrementa
      pendencias.push(api.put(endpoints.tasks.timer(novoId), timer));
    }
    for (const s of focoPendente.current) {
      pendencias.push(api.post(endpoints.tasks.focusSessions(novoId), { ...s, sessionType: 'FOCUS', completed: true }));
    }
    if (Object.values(modsLocal).some(Boolean)) {
      pendencias.push(api.put(endpoints.tasks.moduleConfig(novoId), {
        focusEnabled: modsLocal.foco,
        cycleEnabled: modsLocal.ciclo,
        priorityEnabled: modsLocal.prioridade,
        timerEnabled: modsLocal.tempo,
        notesEnabled: modsLocal.notas,
      }));
    }

    // allSettled, não all: a tarefa já está no banco: um extra que falhe não
    // pode transformar "criada" em "não criada" aos olhos de quem usa. O que
    // não gravou vira aviso, e o fluxo segue para a To Do.
    const resultados = await Promise.allSettled(pendencias);
    // Esvaziados aqui porque o editor desmonta na navegação que vem a seguir;
    // não há segunda tentativa em que pudessem ser reenviados.
    focoPendente.current = [];
    deltaNaoLogado.current = 0;
    if (resultados.some((r) => r.status === 'rejected')) {
      toast('Tarefa criada, mas alguns detalhes não foram salvos.', 'error');
    }

    // O tempo estimado e as sessões de foco desta tarefa foram gravados aqui em
    // cima com `api` direto, fora de qualquer mutation — então nada invalidaria o
    // relatório da semana por conta própria, e o dashboard só veria a tarefa nova
    // um minuto depois. É este o "criei a tarefa e não computou nada".
    invalidarMetricas(qc);

    // O POST inicial invalida a lista antes de os detalhes terminarem. Quando um
    // ciclo é salvo, o backend cria as ocorrências futuras nesse segundo passo;
    // invalide novamente para a To Do e o calendário receberem essas ocorrências.
    if (cicloLocal !== 'none') qc.invalidateQueries({ queryKey: ['tarefas'] });

    onCriada?.(novoId);
    return novoId;
  }

  useImperativeHandle(ref, () => ({
    criar,
    criando: criarTarefa.isPending,
    salvo,
  }));

  const modAtivo = (m) => (m === 'subtarefas' ? subTogglelocal : Boolean(modsLocal[m]));

  if (taskId && carregandoTarefa) {
    return <div className="empty"><p>Carregando tarefa…</p></div>;
  }

  return (
    // O `is-done` fica aqui (e não no .detail da página) porque o drawer do
    // calendário não tem esse wrapper — sem isso o check não pintava em lugar
    // nenhum, mesmo com o PATCH indo pro backend.
    <div className={`task-editor ${done ? 'is-done' : ''} ${isReadOnly ? 'task-editor--readonly' : ''}`}>
      <div className="detail__head">
        <button className="detail__check" aria-label="Concluir tarefa" disabled={isReadOnly} onClick={alternarDone}>
          <Ic d={ICONS.check} strokeWidth={3} />
        </button>
        <h1
          ref={tituloRef}
          className="edit-title"
          contentEditable={!isReadOnly}
          suppressContentEditableWarning
          spellCheck={false}
          data-placeholder="Nome da tarefa…"
          onInput={aoDigitarTexto}
          onBlur={() => !isReadOnly && taskId && persistir()}
        />
      </div>
      <div
        ref={descRef}
        className="edit-desc"
        contentEditable={!isReadOnly}
        suppressContentEditableWarning
        data-placeholder="Adicione uma descrição…"
        onInput={aoDigitarTexto}
        onBlur={() => !isReadOnly && taskId && persistir()}
      />

      <div className="detail__chips">
        <div className="date-pick">
          <button className={`badge badge--info date-pick__btn ${dataAberta ? 'is-open' : ''}`} type="button" disabled={isReadOnly} onClick={() => !isReadOnly && setDataAberta((v) => !v)}>
            <Ic d={ICONS.calendar} />
            <span>{dataRelativa(dataSel)}</span>
            <Ic d={ICONS.chevron} size={10} strokeWidth={2.5} className="date-pick__chevron" />
          </button>
          {!isReadOnly && (
            <DatePicker
              aberto={dataAberta}
              onFechar={() => setDataAberta(false)}
              onSelect={mudarData}
              selecionada={dataSel}
            />
          )}
        </div>

        <div className="time-pick">
          <button
            className={`badge badge--info time-pick__btn ${horaAberta ? 'is-open' : ''}`}
            type="button"
            disabled={isReadOnly}
            data-empty={hora ? undefined : ''}
            onClick={() => !isReadOnly && setHoraAberta((v) => !v)}
          >
            <Ic d={ICONS.clock} />
            <span>{hora ? fmtHoraMin(hora.h, hora.m) : 'Hora'}</span>
            <Ic d={ICONS.chevron} size={10} strokeWidth={2.5} className="time-pick__chevron" />
          </button>
          {!isReadOnly && (
            <TimePicker
              aberto={horaAberta}
              onFechar={() => setHoraAberta(false)}
              hora={hora?.h ?? null}
              min={hora?.m ?? 0}
              onChange={mudarHora}
              onClear={limparHora}
            />
          )}
        </div>

        {/* Lembrete só é habilitado quando a tarefa possui um horário. */}
        <label className={`reminder-pick ${hora ? '' : 'is-disabled'}`}>
          <Ic d={ICONS.bell} size={14} />
          <select
            aria-label="Lembrete da tarefa"
            value={lembreteMinutosAntes ?? ''}
            disabled={!hora}
            onChange={(event) => mudarLembrete(event.target.value)}
          >
            <option value="">Sem lembrete</option>
            {LEMBRETES.map((item) => (
              <option key={item.minutos} value={item.minutos}>{item.rotulo}</option>
            ))}
          </select>
        </label>

        {/* Duração estimada — persiste no /timer; base do teto biológico */}
        <div className="dur-pick">
          <Ic d={ICONS.clock} size={14} />
          <input
            type="number" min={0} value={dur.h} aria-label="Horas estimadas" disabled={isReadOnly}
            onChange={(e) => mudarDur({ ...dur, h: Math.max(0, parseInt(e.target.value, 10) || 0) })}
          />h
          <input
            type="number" min={0} max={55} step={5} value={dur.m} aria-label="Minutos estimados" disabled={isReadOnly}
            onChange={(e) => mudarDur({ ...dur, m: Math.max(0, Math.min(55, parseInt(e.target.value, 10) || 0)) })}
          />min
        </div>

        <CategorySelect
          categorias={cats}
          valor={cat?.nome}
          onChange={mudarCategoria}
          desabilitado={isReadOnly}
        />

        <div className="priority-pick">
          <button
            className={`badge badge--${prioridade} priority-pick__btn ${prioridadeAberta ? 'is-open' : ''}`}
            type="button"
            disabled={isReadOnly}
            aria-haspopup="listbox"
            aria-expanded={prioridadeAberta}
            onClick={() => !isReadOnly && setPrioridadeAberta((v) => !v)}
          >
            <Ic d={ICONS.flag} size={12} />
            <span>{Priority.ROTULO[prioridade]}</span>
            <Ic d={ICONS.chevron} size={10} strokeWidth={2.5} />
          </button>
          {!isReadOnly && prioridadeAberta && (
            <div className="priority-pick__menu" role="listbox" aria-label="Prioridade da tarefa">
              {Priority.NIVEIS.map((n) => (
                <button
                  key={n}
                  className={`prio-opt ${n === prioridade ? 'is-on' : ''}`}
                  style={{ color: Priority.COR[n] }}
                  type="button"
                  role="option"
                  aria-selected={n === prioridade}
                  onClick={() => mudarPrioridade(n)}
                >
                  <span className="prio-opt__dot" style={{ background: Priority.COR[n] }} />
                  <span style={{ color: 'var(--color-text-soft)' }}>{Priority.ROTULO[n]}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {salvo === 'ok' && <span className="badge badge--info">Salvo ✓</span>}

        {subs.length > 0 && (
          <div className="chips-progress">
            <Ic d={ICONS.list} size={12} style={{ flex: 'none' }} />
            <div className="chips-progress__bar">
              <div className={`chips-progress__fill ${pctSubs === 100 ? 'chips-progress__fill--success' : ''}`} style={{ width: `${pctSubs}%` }} />
            </div>
            <span className="chips-progress__count">{pctSubs}%</span>
          </div>
        )}
      </div>

      {/* Não existe painel de "especificações" aqui de propósito: cada linha
          dele repetia algo já visível na tela (status = o check do título,
          recorrência/tempo/pomodoro = os próprios módulos, subtarefas = a
          barra nos chips, módulos ativos = a grade logo abaixo). */}

      <div className="modules">
        <div className="modules__title">Módulos</div>
        {/* No drawer as colunas fluem conforme a largura; na página ficam as 6 do CSS. */}
        <div className="modules__grid" style={compacto ? { gridTemplateColumns: 'repeat(auto-fit, minmax(84px, 1fr))' } : undefined}>
          {Object.keys(LABEL_MOD).map((mod) => (
            <button key={mod} className={`module-toggle ${modAtivo(mod) ? 'is-on' : ''}`} disabled={isReadOnly} onClick={() => toggleModulo(mod)}>
              <span className="module-toggle__ic"><Ic d={MOD_ICONE[mod]} /></span>
              <span className="module-toggle__name">{LABEL_MOD[mod]}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="module-panels">
        {modAtivo('foco') && (
          <div className="card module-panel">
            <div className="module-panel__head">
              <Ic d={ICONS.target} />
              <span className="module-panel__title">Foco — Pomodoro</span>
            </div>
            <div className="pomo">
              <div
                className="pomo__ring"
                style={{
                  '--pct': pomoPct.toFixed(1),
                  ...(pomo.fase === 'pausa' ? { '--color-accent': 'var(--color-success)' } : {}),
                }}
              >
                <div className="pomo__inner">
                  <div className="pomo__time">{formatarMinSeg(pomo.restante)}</div>
                  <div className="pomo__phase">{pomo.fase === 'foco' ? 'Foco' : 'Pausa'} · ciclo {pomo.ciclo}/4</div>
                </div>
              </div>
              <div className="pomo__side">
                <p className="text-soft" style={{ margin: 0 }}>
                  Trabalhe em blocos de 25 minutos com pausas curtas. Os ciclos concluídos contam para o seu tempo de foco do dia.
                </p>
                <div className="pomo__actions">
                  <button className="btn btn--primary btn--md" disabled={isReadOnly} onClick={togglePomo}>
                    {pomo.rodando ? 'Pausar' : pomo.restante === FOCO_MIN * 60 && pomo.fase === 'foco' && pomo.ciclo === 1 ? 'Iniciar' : 'Continuar'}
                  </button>
                  <button className="btn btn--ghost btn--md" disabled={isReadOnly} onClick={pularFase}>Pular fase</button>
                  <button className="btn btn--ghost btn--md" disabled={isReadOnly} onClick={resetPomo}>Reiniciar</button>
                </div>
              </div>
            </div>
          </div>
        )}

        {modAtivo('prioridade') && (
          <div className="card module-panel">
            <div className="module-panel__head">
              <Ic d={ICONS.flag} />
              <span className="module-panel__title">Prioridade</span>
            </div>
            <div className="prio-picker">
              {Priority.NIVEIS.map((n) => (
                <button
                  key={n}
                  className={`prio-opt ${n === prioridade ? 'is-on' : ''}`}
                  style={{ color: Priority.COR[n] }}
                  disabled={isReadOnly}
                  onClick={() => mudarPrioridade(n)}
                >
                  <span className="prio-opt__dot" style={{ background: Priority.COR[n] }} />
                  <span style={{ color: 'var(--color-text-soft)' }}>{Priority.ROTULO[n]}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {modAtivo('ciclo') && (
          <div className="card module-panel">
            <div className="module-panel__head">
              <Ic d={ICONS.cycle} />
              <span className="module-panel__title">Recorrência</span>
            </div>
            <div className="cycle-opts">
              {TIPOS.map((t) => (
                <button key={t} className={`cycle-opt ${t === cicloLocal ? 'is-on' : ''}`} disabled={isReadOnly} onClick={() => escolherCiclo(t)}>
                  {rotuloCiclo(t)}
                </button>
              ))}
            </div>
            {cicloLocal === 'custom' && <CicloCustom custom={cicloCustom} onChange={mudarCustom} isReadOnly={isReadOnly} />}
          </div>
        )}

        {modAtivo('tempo') && (
          <div className="card module-panel">
            <div className="module-panel__head">
              <Ic d={ICONS.clock} />
              <span className="module-panel__title">Cronômetro de execução</span>
            </div>
            <div className="timer">
              <div className="timer__display">{formatarTempo(cronSegundos)}</div>
              {cronErro && <div className="text-danger" role="alert">{cronErro}</div>}
              <div className="timer__actions">
                <button
                  className="btn btn--primary btn--md"
                  onClick={toggleCron}
                  disabled={isReadOnly || iniciarCronometro.isPending || pararCronometro.isPending}
                >
                  {cronRodando ? 'Pausar' : cronSegundos > 0 ? 'Continuar' : 'Iniciar'}
                </button>
                <button
                  className="btn btn--secondary btn--md"
                  onClick={resetCron}
                  disabled={isReadOnly || zerarTempo.isPending || pararCronometro.isPending}
                >
                  Zerar
                </button>
              </div>
            </div>
          </div>
        )}

        {modAtivo('notas') && (
          <div className="card module-panel">
            <div className="module-panel__head">
              <Ic d={ICONS.notes} />
              <span className="module-panel__title">Notas</span>
            </div>
            <textarea
              className="notes-area"
              placeholder="Anote lembretes, links ou ideias para esta tarefa…"
              value={nota}
              disabled={isReadOnly}
              onChange={(e) => aoDigitarNota(e.target.value)}
            />
          </div>
        )}

        {modAtivo('subtarefas') && (
          <div className="card module-panel">
            <div className="module-panel__head">
              <Ic d={ICONS.list} />
              <span className="module-panel__title">Subtarefas</span>
            </div>
            <div className="subtasks__progress">
              <div className="progress">
                <div className="progress__track">
                  <div
                    className={`progress__fill ${pctSubs === 100 && subs.length > 0 ? 'progress__fill--success' : ''}`}
                    style={{ width: `${pctSubs}%` }}
                  />
                </div>
              </div>
              <span className="subtasks__count">{feitas}/{subs.length}</span>
            </div>
            <div>
              {subs.map((s) => (
                <div className={`subtask ${s.done ? 'is-done' : ''}`} key={s.id}>
                  <button className="subtask__check" aria-label="Concluir subtarefa" disabled={isReadOnly} onClick={() => alternarSub(s)}>
                    <Ic d={ICONS.check} strokeWidth={3} />
                  </button>
                  <span className="subtask__label">{s.titulo}</span>
                  <button className="subtask__del" aria-label="Remover subtarefa" disabled={isReadOnly} onClick={() => excluirSub(s)}>
                    <Ic d={ICONS.close} />
                  </button>
                </div>
              ))}
            </div>
            {!isReadOnly && (
              <div className="subtask__add">
                <Ic d={ICONS.plus} size={18} style={{ color: 'var(--color-text-muted)' }} />
                <input
                  placeholder="Adicionar subtarefa… pressione Enter"
                  value={subInput}
                  onChange={(e) => setSubInput(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') adicionarSub(); }}
                />
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
});

export default TaskEditor;

// Controles do ciclo personalizado: data de início, "a cada N horas|dias" e
// número de repetições, com resumo do fim previsto. `onChange` recebe patches
// parciais do objeto custom ({count, unit, occurrences, startIso}).
function CicloCustom({ custom, onChange, isReadOnly }) {
  const [dataAberta, setDataAberta] = useState(false);
  const [unitAberta, setUnitAberta] = useState(false);

  const inicio = custom.startIso ? deIso(custom.startIso) : hoje();
  const u = custom.unit === 'horas' ? 'h' : (Number(custom.count) === 1 ? ' dia' : ' dias');
  const fim = fimPrevisto(custom, inicio);

  return (
    <div className="cycle-custom">
      <div className="cycle-custom__row">
        <span className="cycle-custom__label">Início</span>
        <div className="date-pick">
          <button className={`badge badge--info date-pick__btn ${dataAberta ? 'is-open' : ''}`} type="button" disabled={isReadOnly} onClick={() => !isReadOnly && setDataAberta((v) => !v)}>
            <Ic d={ICONS.calendar} />
            <span>{dataRelativa(inicio)}</span>
            <Ic d={ICONS.chevron} size={10} strokeWidth={2.5} className="date-pick__chevron" />
          </button>
          {!isReadOnly && (
            <DatePicker
              aberto={dataAberta}
              onFechar={() => setDataAberta(false)}
              onSelect={(d) => onChange({ startIso: dataIso(d) })}
              selecionada={inicio}
            />
          )}
        </div>
      </div>
      <div className="cycle-custom__row">
        <span className="cycle-custom__label">A cada</span>
        <div className="dur-pick cycle-custom__interval">
          <input
            type="number" min={1} max={999} value={custom.count} aria-label="Valor do intervalo" disabled={isReadOnly}
            onChange={(e) => onChange({ count: Math.max(1, Math.min(999, parseInt(e.target.value, 10) || 1)) })}
          />
          <div className="cycle-unit-pick">
            <button type="button" className={`cycle-unit ${unitAberta ? 'is-open' : ''}`} disabled={isReadOnly} aria-haspopup="listbox" aria-expanded={unitAberta} onClick={() => !isReadOnly && setUnitAberta((v) => !v)}>
              <span>{custom.unit}</span>
              <Ic d={ICONS.chevron} size={10} strokeWidth={2.5} className="cycle-unit__chevron" />
            </button>
            {!isReadOnly && unitAberta && (
              <>
                <div className="cycle-unit__overlay" onClick={() => setUnitAberta(false)} />
                <div className="cycle-unit__menu" role="listbox">
                  {['horas', 'dias'].map((op) => (
                    <button key={op} type="button" role="option" className={`cycle-unit__opt ${custom.unit === op ? 'is-on' : ''}`}
                      onClick={() => { onChange({ unit: op }); setUnitAberta(false); }}>
                      {op}
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
        <span className="cycle-custom__label">Repetir</span>
        <div className="dur-pick cycle-custom__reps">
          <input
            type="number" min={2} max={365} value={custom.occurrences} aria-label="Número de repetições" disabled={isReadOnly}
            onChange={(e) => onChange({ occurrences: Math.max(2, Math.min(365, parseInt(e.target.value, 10) || 2)) })}
          />
          <span className="cycle-custom__times">×</span>
        </div>
      </div>
      <p className="cycle-custom__summary">
        {custom.occurrences} ocorrências · a cada {custom.count}{u} · termina {dataRelativa(fim)}
      </p>
    </div>
  );
}
