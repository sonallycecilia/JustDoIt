import { useQuery } from '@tanstack/react-query';
// CORREÇÃO: Padronizado para a pasta "api" (ou mude ambos para "services" se preferir)
import { closureService } from '../api/closureService'; 

export function useCycleSnapshots(cycleId) {
  return useQuery({
    queryKey: ['cycle-snapshots', cycleId],
    queryFn: () => closureService.getCycleSnapshots(cycleId),
    enabled: !!cycleId, 
  });
}