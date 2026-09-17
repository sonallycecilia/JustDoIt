// Página /tasks/:id e /tasks/nova — casca em volta do <TaskEditor />, que é o
// mesmo corpo usado pelo drawer lateral do calendário.
//
// Em edição não há botão de salvar: o editor persiste cada mudança sozinho.
// Em criação ainda existe o botão, porque sem id não há o que atualizar — o
// POST acontece ali e só depois os módulos configurados são gravados.
import { useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import ConfirmModal from '@/components/ConfirmModal';
import Ic, { ICONS } from '@/components/Ic';
import Sidebar from '@/components/Sidebar';
import TaskEditor from '@/features/tasks/components/TaskEditor';
import { useRemoverTarefa } from '@/features/tasks/hooks/useTasks';

export default function TaskDetail() {
  const { id: taskId } = useParams(); // undefined em /tasks/nova
  const navigate = useNavigate();
  const editorRef = useRef(null);
  const [criando, setCriando] = useState(false);
  const [confirmandoExclusao, setConfirmandoExclusao] = useState(false);
  const [erroExclusao, setErroExclusao] = useState('');
  const removerTarefa = useRemoverTarefa();

  async function registrar() {
    setCriando(true);
    const novoId = await editorRef.current?.criar();
    setCriando(false);
    if (novoId) navigate('/todo');
  }

  function excluir() {
    setErroExclusao('');
    removerTarefa.mutate(taskId, {
      onSuccess: () => navigate('/todo'),
      onError: (erro) => setErroExclusao(erro.message || 'Não foi possível excluir a tarefa.'),
    });
  }

  return (
    <div className="app">
      <Sidebar ativa="todo" />

      <main className="app__main">
        <div className="detail">
          <div className="detail__topbar">
            <Link className="detail__back" to="/todo">
              <Ic d={ICONS.back} /> Ir para To Do
            </Link>
            <div className="detail__topbar-actions">
              {taskId ? (
                <>
                  <span className="text-soft" style={{ fontSize: 'var(--font-size-sm)' }}>
                    As mudanças salvam automaticamente
                  </span>
                  <button
                    className="btn btn--danger btn--sm"
                    type="button"
                    onClick={() => {
                      setErroExclusao('');
                      setConfirmandoExclusao(true);
                    }}
                  >
                    Excluir tarefa
                  </button>
                </>
              ) : (
                <>
                  {/* Contraponto explícito ao aviso de autosave da edição: aqui
                      o que existe é rascunho local, e só o botão grava. */}
                  <span className="text-soft" style={{ fontSize: 'var(--font-size-sm)' }}>
                    Rascunho guardado neste navegador — registre para salvar
                  </span>
                  <button className="btn btn--primary btn--md" onClick={registrar} disabled={criando}>
                    {criando ? 'Salvando…' : 'Registrar tarefa'}
                  </button>
                </>
              )}
            </div>
          </div>

          <TaskEditor ref={editorRef} taskId={taskId} />
        </div>
      </main>

      <ConfirmModal
        aberto={confirmandoExclusao}
        titulo="Excluir tarefa"
        processando={removerTarefa.isPending}
        erro={erroExclusao}
        onConfirmar={excluir}
        onFechar={() => {
          setConfirmandoExclusao(false);
          setErroExclusao('');
        }}
      >
        <p>Tem certeza que deseja excluir esta tarefa? Essa ação não pode ser desfeita.</p>
      </ConfirmModal>
    </div>
  );
}
