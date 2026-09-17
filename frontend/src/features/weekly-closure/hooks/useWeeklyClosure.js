import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { closureService } from '../api/closureService';

export function useWeeklyClosure(onSuccessCallback) {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [previewData, setPreviewData] = useState(null);

  // 1. Busca a prévia real do backend
  const loadPreview = useCallback(async () => {
    setIsLoading(true);
    try {
      // closureService.getClosurePreview() já devolve o JSON puro (não um
      // envelope `{ data }` estilo axios) — atribuir direto em vez de
      // '.data', que sempre resultava em `undefined` e fazia o modal nunca
      // renderizar mesmo com o backend respondendo corretamente.
      const data = await closureService.getClosurePreview();
      setPreviewData(data);
      setIsModalOpen(true);
    } catch (error) {
      console.error("Erro ao carregar prévia do ciclo:", error);
      alert('Não foi possível carregar a prévia do ciclo. Verifique se há um ciclo aberto.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  // 2. Envia o fechamento real para o backend
  const handleSubmittingClosure = async ({ cycleId, tasksToMigrate, tasksToArchive }) => {
    setIsLoading(true);
    try {
      await closureService.submitClosure({
        cycleId,
        tasksToMigrate,
        tasksToArchive,
        weekStartDate: previewData.weekStartDate,
        weekEndDate: previewData.weekEndDate,
      });

      queryClient.invalidateQueries({ queryKey: ['analytics-week'] });
      queryClient.invalidateQueries({ queryKey: ['plano-semanal'] });
      queryClient.invalidateQueries({ queryKey: ['resumo-semanal'] });
      queryClient.invalidateQueries({ queryKey: ['closed-cycles'] });

      setIsModalOpen(false);

      // Atualiza a listagem de tarefas e histórico na tela
      if (onSuccessCallback) onSuccessCallback();

      alert('Ciclo encerrado e consolidado com sucesso!');
    } catch (error) {
      console.error("Erro ao encerrar ciclo:", error);
      alert('Ocorreu um erro ao finalizar o ciclo no servidor.');
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  return {
    previewData,
    isModalOpen,
    setIsModalOpen,
    loadPreview,
    handleSubmittingClosure,
    isLoading
  };
}
