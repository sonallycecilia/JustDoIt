import { Link } from 'react-router-dom';
import Ic, { ICONS } from '@/components/Ic';

export default function CategoryExecution({ categorias }) {
  return (
    <div className="an-category-execution">
      {categorias.map((categoria) => {
        const percentual = categoria.total
          ? Math.round((categoria.concluidas / categoria.total) * 100)
          : 0;

        const cabecalho = (
          <>
            <span className="an-category-group__dot" style={{ background: categoria.cor }} />
            <span className="an-category-group__name">{categoria.nome}</span>
            <span className="an-category-group__counts">
              {categoria.concluidas} concluídas · {categoria.pendentes} pendentes
            </span>
            <strong>{percentual}%</strong>
            {!categoria.somenteResumo && <Ic d={ICONS.chevron} />}
          </>
        );

        if (categoria.somenteResumo) {
          return (
            <div className="an-category-group" key={categoria.id}>
              <div className="an-category-group__summary">{cabecalho}</div>
            </div>
          );
        }

        return (
          <details className="an-category-group" key={categoria.id}>
            <summary>{cabecalho}</summary>
            <div className="an-category-group__tasks">
              {categoria.tarefas.map((tarefa) => (
                <Link className={`an-category-task${tarefa.concluida ? ' is-done' : ''}`}
                      to={`/tasks/${tarefa.id}`} key={tarefa.id}>
                  <span className="an-category-task__status">
                    {tarefa.concluida && <Ic d={ICONS.check} strokeWidth={3} />}
                  </span>
                  <span>{tarefa.titulo}</span>
                  <small>{tarefa.concluida ? 'Concluída' : 'Pendente'}</small>
                </Link>
              ))}
            </div>
          </details>
        );
      })}
    </div>
  );
}
