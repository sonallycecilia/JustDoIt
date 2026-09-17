// O rótulo do tempo de hoje tem de dizer de onde ele veio. Enquanto só o
// Pomodoro era contado, o texto fixo "em N ciclos" bastava; agora que o
// cronômetro também soma, ele mentiria para quem usa o cronômetro.
import { describe, expect, it } from 'vitest';
import { origemDoTempo } from '@/features/dashboard/pages/VisaoGeral';

describe('origemDoTempo', () => {
  it('descreve só o Pomodoro quando o tempo veio de ciclos', () => {
    expect(origemDoTempo({ ciclos: 3, cronometroMinutos: 0 }))
      .toBe('3 ciclos de Pomodoro');
  });

  it('descreve só o cronômetro quando não houve ciclo nenhum', () => {
    expect(origemDoTempo({ ciclos: 0, cronometroMinutos: 90 }))
      .toBe('1h30 no cronômetro');
  });

  it('junta as duas origens quando as duas foram usadas', () => {
    expect(origemDoTempo({ ciclos: 2, cronometroMinutos: 30 }))
      .toBe('2 ciclos de Pomodoro · 0h30 no cronômetro');
  });

  it('usa o singular com um único ciclo', () => {
    expect(origemDoTempo({ ciclos: 1, cronometroMinutos: 0 }))
      .toBe('1 ciclo de Pomodoro');
  });

  it('avisa quando não há tempo registrado', () => {
    expect(origemDoTempo({ ciclos: 0, cronometroMinutos: 0 }))
      .toBe('nenhum tempo registrado hoje');
  });
});
