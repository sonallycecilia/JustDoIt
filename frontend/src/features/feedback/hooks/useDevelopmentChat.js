import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';

const SUPPORT_MESSAGES_QUERY_KEY = ['support-messages'];

export function useSupportMessages(enabled) {
  return useQuery({
    queryKey: SUPPORT_MESSAGES_QUERY_KEY,
    queryFn: () => api.get(endpoints.support.messages),
    enabled,
    refetchInterval: enabled ? 30_000 : false,
  });
}

export function useSendSupportMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (message) => api.post(endpoints.support.messages, message),
    onSuccess: (created) => {
      queryClient.setQueryData(SUPPORT_MESSAGES_QUERY_KEY, (current = []) => [...current, created]);
    },
  });
}
