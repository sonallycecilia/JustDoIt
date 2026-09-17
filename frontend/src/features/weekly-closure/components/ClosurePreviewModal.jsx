import React, { useState } from 'react';

export function ClosurePreviewModal({ isOpen, previewData, onClose, onSubmit }) {
    const [tasksToMigrate, setTasksToMigrate] = useState(
        previewData?.pendingTasks?.map((t) => t.taskId) || []
    );
    const [tasksToArchive, setTasksToArchive] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);

    if (!isOpen || !previewData) return null;

    const handleToggleAction = (taskId, action) => {
        if (action === 'migrate') {
            if (!tasksToMigrate.includes(taskId)) {
                setTasksToMigrate([...tasksToMigrate, taskId]);
                setTasksToArchive(tasksToArchive.filter(id => id !== taskId));
            }
        } else {
            if (!tasksToArchive.includes(taskId)) {
                setTasksToArchive([...tasksToArchive, taskId]);
                setTasksToMigrate(tasksToMigrate.filter(id => id !== taskId));
            }
        }
    };

    const handleSelectAllMigrate = () => {
        setTasksToMigrate(previewData.pendingTasks.map((t) => t.taskId));
        setTasksToArchive([]);
    };

    const handleSelectAllArchive = () => {
        setTasksToArchive(previewData.pendingTasks.map((t) => t.taskId));
        setTasksToMigrate([]);
    };

    const handleSubmitClosure = async () => {
        try {
            setIsSubmitting(true);
            await onSubmit({
                cycleId: previewData.cycleId,
                tasksToMigrate,
                tasksToArchive,
            });
            onClose();
        } catch (error) {
            console.error(error);
            alert('Ocorreu um erro ao finalizar o ciclo.');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div style={{
            position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.8)', backdropFilter: 'blur(4px)',
            zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px'
        }}>
            <div style={{
                backgroundColor: '#0b1614', border: '1px solid rgba(26, 46, 43, 0.8)', width: '100%',
                maxWidth: '600px', borderRadius: '16px', boxShadow: '0 24px 48px rgba(0, 0, 0, 0.6)',
                display: 'flex', flexDirection: 'column', maxHeight: '90vh', overflow: 'hidden', color: '#e2e8f0'
            }}>
                
                {/* Header */}
                <div style={{ padding: '24px 28px', borderBottom: '1px solid rgba(26, 46, 43, 0.8)' }}>
                    <h2 style={{ fontSize: '1.25rem', fontFamily: 'serif', fontWeight: 'normal', color: '#f8fafc', margin: 0 }}>Balanço e Fechamento Semanal</h2>
                    <p style={{ fontSize: '0.85rem', color: '#94a3b8', margin: '6px 0 0 0' }}>
                        Sua semana chegou ao fim. Revise o que foi feito e decida o destino das tarefas pendentes.
                    </p>
                </div>

                {/* Conteúdo Rolável */}
                <div style={{ padding: '24px 28px', overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column', gap: '24px' }}>
                    
                    {/* Seção de Concluídas */}
                    <div>
                        <h3 style={{ fontSize: '0.75rem', fontWeight: 600, color: '#34d399', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '10px' }}>
                            ✅ Concluídas na Semana ({previewData.completedTasks?.length || 0})
                        </h3>
                        {previewData.completedTasks?.length === 0 ? (
                            <p style={{ fontSize: '0.85rem', color: '#64748b', fontStyle: 'italic' }}>Nenhuma tarefa concluída neste ciclo.</p>
                        ) : (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                {previewData.completedTasks?.map((task) => (
                                    <div key={task.taskId} style={{ backgroundColor: '#0f221f', padding: '12px 16px', borderRadius: '10px', border: '1px solid rgba(26, 46, 43, 0.5)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <span style={{ fontSize: '0.875rem', color: '#94a3b8', textDecoration: 'line-through' }}>{task.title}</span>
                                        <span style={{ fontSize: '0.7rem', backgroundColor: 'rgba(52, 211, 153, 0.1)', color: '#34d399', padding: '3px 8px', borderRadius: '6px', fontWeight: 500 }}>Entregue</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Seção de Pendentes */}
                    <div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                            <h3 style={{ fontSize: '0.75rem', fontWeight: 600, color: '#fbbf24', textTransform: 'uppercase', letterSpacing: '0.05em', margin: 0 }}>
                                ⏳ Pendências ({previewData.pendingTasks?.length || 0})
                            </h3>
                            {previewData.pendingTasks?.length > 0 && (
                                <div style={{ fontSize: '0.75rem' }}>
                                    <button type="button" onClick={handleSelectAllMigrate} style={{ color: '#38bdf8', background: 'none', border: 'none', cursor: 'pointer', textDecoration: 'underline' }}>
                                        Migrar Todas
                                    </button>
                                    <span style={{ color: '#475569', margin: '0 6px' }}>|</span>
                                    <button type="button" onClick={handleSelectAllArchive} style={{ color: '#94a3b8', background: 'none', border: 'none', cursor: 'pointer', textDecoration: 'underline' }}>
                                        Arquivar Todas
                                    </button>
                                </div>
                            )}
                        </div>

                        {previewData.pendingTasks?.length === 0 ? (
                            <p style={{ fontSize: '0.85rem', color: '#64748b', fontStyle: 'italic' }}>Parabéns! Nenhuma pendência sobrando para trás.</p>
                        ) : (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                {previewData.pendingTasks?.map((task) => {
                                    const isMigrating = tasksToMigrate.includes(task.taskId);
                                    return (
                                        <div key={task.taskId} style={{ backgroundColor: '#0f221f', padding: '12px 16px', borderRadius: '10px', border: '1px solid rgba(26, 46, 43, 0.8)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                            <span style={{ fontSize: '0.875rem', fontWeight: 500, color: '#f8fafc' }}>{task.title}</span>
                                            
                                            <div style={{ display: 'flex', backgroundColor: '#071110', padding: '3px', borderRadius: '8px', border: '1px solid rgba(26, 46, 43, 0.8)', gap: '4px' }}>
                                                <button
                                                    type="button"
                                                    onClick={() => handleToggleAction(task.taskId, 'migrate')}
                                                    style={{
                                                        padding: '5px 12px', fontSize: '0.75rem', borderRadius: '6px', fontWeight: 500, cursor: 'pointer', border: 'none',
                                                        backgroundColor: isMigrating ? '#10b981' : 'transparent',
                                                        color: isMigrating ? '#ffffff' : '#94a3b8',
                                                        transition: 'all 0.2s'
                                                    }}
                                                >
                                                    Migrar
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => handleToggleAction(task.taskId, 'archive')}
                                                    style={{
                                                        padding: '5px 12px', fontSize: '0.75rem', borderRadius: '6px', fontWeight: 500, cursor: 'pointer', border: 'none',
                                                        backgroundColor: !isMigrating ? '#334155' : 'transparent',
                                                        color: !isMigrating ? '#ffffff' : '#94a3b8',
                                                        transition: 'all 0.2s'
                                                    }}
                                                >
                                                    Arquivar
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </div>

                {/* Footer */}
                <div style={{ padding: '20px 28px', borderTop: '1px solid rgba(26, 46, 43, 0.8)', backgroundColor: '#071110', display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                    <button
                        type="button"
                        onClick={onClose}
                        style={{ backgroundColor: 'transparent', color: '#94a3b8', border: '1px solid rgba(26, 46, 43, 0.8)', padding: '10px 20px', borderRadius: '8px', fontSize: '0.85rem', fontWeight: 500, cursor: 'pointer' }}
                    >
                        Cancelar
                    </button>
                    <button
                        type="button"
                        disabled={isSubmitting}
                        onClick={handleSubmitClosure}
                        style={{ backgroundColor: '#10b981', color: '#071110', border: 'none', padding: '10px 24px', borderRadius: '8px', fontSize: '0.85rem', fontWeight: 600, cursor: 'pointer', opacity: isSubmitting ? 0.5 : 1 }}
                    >
                        {isSubmitting ? 'Processando...' : 'Confirmar e Iniciar Nova Semana'}
                    </button>
                </div>

            </div>
        </div>
    );
}