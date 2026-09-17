// Painel lateral do calendário: renderiza o MESMO editor da página de detalhe
// (<TaskEditor compacto />), então chips, módulos e painéis são idênticos aos
// de /tasks/:id e tudo persiste sozinho no backend.
//
// A única responsabilidade extra daqui é manter o BLOCO de tempo do calendário
// em sincronia quando a hora/data da tarefa muda pelo editor.
import { Link } from 'react-router-dom';
import Ic, { ICONS } from '@/components/Ic';
import TaskEditor from '@/features/tasks/components/TaskEditor';
import { fmtHora, useAtualizarBloco } from '@/features/calendar/hooks/useTimeBlocks';
import { toast } from '@/lib/toast';
import { dataRelativa, deIso } from '@/lib/utils';

function ehBlocoPersistido(id) {
  return typeof id === 'string' && id.indexOf('ext-') !== 0 && id.indexOf('task-') !== 0;
}

// "HH:mm" → horas decimais (o modelo do calendário usa fração de hora).
function horaDecimal(hhmm) {
  if (!hhmm) return null;
  const [h, m] = hhmm.split(':').map(Number);
  return h + m / 60;
}

export default function EventSummary({ ev, dias }) {
  const atualizarBloco = useAtualizarBloco();

  const diaEv = ev.iso ? null : (dias && dias[ev.d]);
  const iso = ev.iso || (diaEv && diaEv.iso);

 // Depois que o editor persistiu a tarefa, move o bloco para bater com ela.
  function sincronizarBloco(dados) {
    if (!ehBlocoPersistido(ev.id)) return;
    const novoIso = dados.dataIso || iso;
    const novoIni = horaDecimal(dados.hora);
    if (!novoIso || novoIni === null) return;
    const duracao = (ev.fim ?? 0) - (ev.ini ?? 0);
    atualizarBloco
      .mutateAsync({ ...ev, iso: novoIso, ini: novoIni, fim: novoIni + (duracao > 0 ? duracao : 1) })
      .catch(() => {
        toast('Não foi possível sincronizar o bloco no calendário.', 'error');
      });
  }

  return (
    <div className="evsum">
      {/* O chip mostra a posição do BLOCO na grade — é a única informação que
          o editor não tem, já que ele fala da tarefa, não do agendamento. */}
      <div className="evsum__topbar">
        <span className="evsum__chip">
          <Ic d={ICONS.calendar} />
          {iso ? dataRelativa(deIso(iso)) : 'Sem data'}
          {!ev.semHora && ev.ini != null && ` · ${fmtHora(ev.ini)}–${fmtHora(ev.fim)}`}
        </span>
        <div className="evsum__topbar-actions">
          {ev.taskId && (
            <Link className="btn btn--secondary btn--sm" to={`/tasks/${ev.taskId}`}>Ver tarefa completa</Link>
          )}
        </div>
      </div>

      {ev.taskId ? (
        <TaskEditor taskId={ev.taskId} compacto onSalvo={sincronizarBloco} />
      ) : (
        <div className="evsum__card card">
          <section className="evsum__section">
            <div className="evsum__section-head">Bloco sem tarefa vinculada</div>
            <p className="evsum__section-text">
              Este bloco de tempo não está ligado a nenhuma tarefa — os campos da
              tarefa ficam disponíveis quando há uma vinculada.
            </p>
          </section>
        </div>
      )}
    </div>
  );
}
