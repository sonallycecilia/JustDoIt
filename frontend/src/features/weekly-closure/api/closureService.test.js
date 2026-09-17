import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/api/client';
import { closureService } from '@/features/weekly-closure/api/closureService';

vi.mock('@/api/client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}));

describe('closureService.submitClosure', () => {
  beforeEach(() => vi.clearAllMocks());

  it('fecha o ciclo de tarefas e congela a mesma semana no schedule-service', async () => {
    api.post
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({ id: 'plan-1' });
    api.patch.mockResolvedValue({ id: 'plan-1', status: 'CLOSED' });

    await closureService.submitClosure({
      cycleId: 'cycle-1',
      weekStartDate: '2026-08-10',
      weekEndDate: '2026-08-16',
      tasksToMigrate: ['task-1'],
      tasksToArchive: ['task-2'],
    });

    expect(api.post.mock.calls[0][0]).toContain('/weekly-cycles/current/closure');
    expect(api.post.mock.calls[0][1]).toEqual({
      cycleId: 'cycle-1',
      tasksToMigrate: ['task-1'],
      tasksToArchive: ['task-2'],
    });
    expect(api.post.mock.calls[1][0]).toContain('/weekly-plans');
    expect(api.post.mock.calls[1][1]).toEqual({
      weekStartDate: '2026-08-10',
      weekEndDate: '2026-08-16',
    });
    expect(api.patch).toHaveBeenCalledWith(expect.stringContaining('/weekly-plans/plan-1/close'), {});
  });

  it('não tenta congelar a agenda quando a triagem de tarefas falha', async () => {
    api.post.mockRejectedValueOnce(new Error('task-service indisponível'));

    await expect(closureService.submitClosure({
      cycleId: 'cycle-1',
      weekStartDate: '2026-08-10',
      weekEndDate: '2026-08-16',
      tasksToMigrate: [],
      tasksToArchive: [],
    })).rejects.toThrow('task-service indisponível');

    expect(api.post).toHaveBeenCalledTimes(1);
    expect(api.patch).not.toHaveBeenCalled();
  });
});
