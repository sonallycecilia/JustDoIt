import { useQuery } from '@tanstack/react-query';
import { closureService } from '../api/closureService'; 

export function useClosedCycles() {
  return useQuery({
    queryKey: ['closed-cycles'],
    queryFn: () => closureService.getClosedCycles(),
  });
}