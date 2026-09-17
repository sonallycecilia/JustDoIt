// Overlays da landing: Recursos, Sobre e Legal (Termos/Privacidade).
// Port de landing.js + scripts/features/auth/legal.js — mesmo conteúdo e mesmas
// classes CSS, só que como componentes controlados.
import { useRef } from 'react';
import Ic, { ICONS } from '@/components/Ic';
import { useModalA11y } from '@/hooks/useModalA11y';

const RECURSOS = [
  {
    icone: ICONS.flag,
    titulo: 'Prioridade',
    texto: 'Classifique a urgência de cada tarefa para saber exatamente no que focar primeiro.',
    tags: ['Urgente', 'Importante', 'Normal', 'Baixa'],
  },
  {
    icone: ICONS.layers,
    titulo: 'Categoria',
    texto: 'Agrupe tarefas por contexto com categorias coloridas. O painel lateral mostra o progresso de cada grupo de um jeito claro.',
    tags: ['Estudos', 'Casa', 'Genérico'],
  },
  {
    icone: ICONS.notes,
    titulo: 'Notas',
    texto: 'Escreva detalhes, contexto ou lembretes diretamente na tarefa. As notas ficam sempre visíveis na tela de detalhe.',
  },
  {
    icone: ICONS.clock,
    titulo: 'Cronômetro de foco',
    texto: 'Inicie uma sessão de foco diretamente da tarefa. Acompanhe quanto tempo você dedicou a cada atividade ao longo do dia.',
  },
  {
    icone: ICONS.cycle,
    titulo: 'Ciclos de repetição',
    texto: 'Ative o ciclo para que a tarefa se redefina automaticamente em um intervalo que você mesmo define.',
  },
  {
    icone: ICONS.calendar,
    titulo: 'Visualização semanal',
    texto: 'Veja todas as suas tarefas distribuídas ao longo da semana no Calendário para planejar com mais visibilidade.',
  },
  {
    icone: ICONS.analytics,
    titulo: 'Análise de produtividade',
    texto: 'Acompanhe métricas de tarefas concluídas, tempo de foco e tendências semanais no painel de Análise.',
  },
];

const EQUIPE = [
  { iniciais: 'SC', nome: 'Sonally Cecília' },
  { iniciais: 'JP', nome: 'Julia Pamplona' },
  { iniciais: 'LC', nome: 'Laryssa Costa' },
];

function Overlay({ aberto, onFechar, titulo, subtitulo, largo = true, children }) {
  const dialogRef = useRef(null);
  const closeRef = useRef(null);
  useModalA11y({ aberto, containerRef: dialogRef, initialFocusRef: closeRef, onFechar });
  if (!aberto) return null;
  return (
    <div
      className="recursos-overlay is-open"
      onClick={onFechar}
    >
      <div ref={dialogRef} className={`recursos-panel ${largo ? '' : 'sobre-panel'}`} role="dialog" aria-modal="true" aria-labelledby="landing-overlay-title" tabIndex={-1} onClick={(e) => e.stopPropagation()}>
        <div className="recursos-head">
          <h2 id="landing-overlay-title">{titulo}</h2>
          <p>{subtitulo}</p>
          <button ref={closeRef} className="recursos-close" type="button" onClick={onFechar} aria-label="Fechar">
            <Ic d={ICONS.close} />
          </button>
        </div>
        <div className="recursos-divider" />
        {children}
      </div>
    </div>
  );
}

export function RecursosOverlay({ aberto, onFechar }) {
  return (
    <Overlay
      aberto={aberto}
      onFechar={onFechar}
      titulo="Funcionalidades de uma tarefa"
      subtitulo="Tudo que você pode configurar ao criar ou editar uma tarefa no JustDoIt."
    >
      <div className="recursos-grid">
        {RECURSOS.map((r) => (
          <div className="recursos-item" key={r.titulo}>
            <div className="recursos-item__ic"><Ic d={r.icone} /></div>
            <div className="recursos-item__body">
              <strong>{r.titulo}</strong>
              <p>{r.texto}</p>
              {r.tags && (
                <div className="recursos-item__tags">
                  {r.tags.map((t) => <span className="recursos-tag" key={t}>{t}</span>)}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </Overlay>
  );
}

export function SobreOverlay({ aberto, onFechar }) {
  return (
    <Overlay
      aberto={aberto}
      onFechar={onFechar}
      largo={false}
      titulo="Sobre o JustDoIt"
      subtitulo="Um projeto criado com cuidado por três desenvolvedoras apaixonadas por produtividade."
    >
      <div className="sobre-team">
        {EQUIPE.map((p) => (
          <div className="sobre-card" key={p.iniciais}>
            <div className="sobre-card__avatar">{p.iniciais}</div>
            <div className="sobre-card__body">
              <strong>{p.nome}</strong>
              <span className="sobre-card__role">Desenvolvedora</span>
            </div>
          </div>
        ))}
      </div>
      <p className="sobre-note">Feito com foco, café e muita vontade de simplificar o dia a dia.</p>
    </Overlay>
  );
}

const ATUALIZADO = 'Última atualização: 26 de junho de 2026';

const LEGAL = {
  termos: {
    titulo: 'Termos de Uso',
    secoes: [
      ['1. Aceitação dos termos', ['Ao criar uma conta e utilizar o JustDoIt ("Serviço"), você concorda com estes Termos de Uso. Caso não concorde, não utilize o Serviço.']],
      ['2. Descrição do serviço', ['O JustDoIt é um gerenciador de tarefas que permite organizar atividades, categorias, lembretes e acompanhar sua produtividade. O Serviço é fornecido "no estado em que se encontra", podendo ser alterado ou descontinuado a qualquer momento.']],
      ['3. Cadastro e conta', [], [
        'Você deve fornecer informações verdadeiras e mantê-las atualizadas.',
        'Você é responsável por manter a confidencialidade da sua senha e por todas as atividades realizadas na sua conta.',
        'É necessário ter idade mínima de 13 anos para criar uma conta.',
      ]],
      ['4. Responsabilidades do usuário', ['Você concorda em não utilizar o Serviço para fins ilícitos, não tentar acessar contas de terceiros e não comprometer a segurança ou o funcionamento da plataforma.']],
      ['5. Conteúdo do usuário', ['As tarefas e demais informações que você insere são de sua responsabilidade e permanecem de sua propriedade. Você nos concede apenas a permissão necessária para armazenar e exibir esse conteúdo a fim de operar o Serviço.']],
      ['6. Limitação de responsabilidade', ['O JustDoIt não se responsabiliza por perdas de dados, prejuízos ou indisponibilidades decorrentes do uso do Serviço, na máxima extensão permitida pela lei.']],
      ['7. Alterações', ['Estes Termos podem ser atualizados periodicamente. Mudanças relevantes serão comunicadas, e o uso contínuo do Serviço implica aceitação da versão vigente.']],
    ],
  },
  privacidade: {
    titulo: 'Política de Privacidade',
    intro: 'Esta Política descreve como o JustDoIt trata seus dados pessoais, em conformidade com a Lei Geral de Proteção de Dados (LGPD – Lei nº 13.709/2018).',
    secoes: [
      ['1. Dados que coletamos', [], [
        'Dados de cadastro: nome, e-mail e data de nascimento.',
        'Dados de uso: tarefas, categorias, lembretes e configurações que você cria.',
        'Dados técnicos: informações de sessão necessárias para manter você conectado.',
      ]],
      ['2. Como usamos seus dados', ['Utilizamos seus dados para criar e autenticar sua conta, fornecer as funcionalidades do Serviço, salvar suas tarefas e melhorar a experiência de uso.']],
      ['3. Base legal', ['O tratamento se baseia na execução do contrato (prestação do Serviço) e no seu consentimento, conforme a LGPD.']],
      ['4. Compartilhamento', ['Não vendemos seus dados. O compartilhamento ocorre apenas quando necessário para operar o Serviço (por exemplo, provedores de infraestrutura) ou por obrigação legal.']],
      ['5. Armazenamento e segurança', ['Adotamos medidas técnicas razoáveis para proteger seus dados, incluindo o armazenamento de senhas de forma criptografada. Nenhum sistema, porém, é totalmente imune a riscos.']],
      ['6. Seus direitos', ['Você pode, a qualquer momento, solicitar acesso, correção, exclusão ou portabilidade dos seus dados, bem como revogar o consentimento, conforme o art. 18 da LGPD.']],
      ['7. Armazenamento local', ['Utilizamos o armazenamento do seu navegador (localStorage) para guardar dados de sessão e preferências, como o tema. Você pode limpá-los pelas configurações do navegador.']],
      ['8. Retenção', ['Mantemos seus dados enquanto sua conta estiver ativa. Após o encerramento, eles são removidos ou anonimizados, salvo obrigação legal de retenção.']],
      ['9. Alterações', ['Esta Política pode ser atualizada. Alterações relevantes serão comunicadas dentro do Serviço.']],
    ],
  },
};

// `chave` é 'termos' | 'privacidade' | null (fechado).
export function LegalModal({ chave, onFechar }) {
  const dialogRef = useRef(null);
  const closeRef = useRef(null);
  useModalA11y({ aberto: Boolean(chave), containerRef: dialogRef, initialFocusRef: closeRef, onFechar });
  if (!chave) return null;

  const doc = LEGAL[chave];
  return (
    <div className="legal-modal">
      <div className="legal-modal__backdrop" onClick={onFechar} />
      <div ref={dialogRef} className="legal-modal__card" role="dialog" aria-modal="true" aria-labelledby="legal-modal-title" tabIndex={-1}>
        <div className="legal-modal__head">
          <h2 id="legal-modal-title" className="legal-modal__title">{doc.titulo}</h2>
          <button ref={closeRef} type="button" className="legal-modal__close" onClick={onFechar} aria-label="Fechar">
            <Ic d={ICONS.close} />
          </button>
        </div>
        <div className="legal-modal__body">
          <p className="legal-modal__updated">{ATUALIZADO}</p>
          {doc.intro && <p>{doc.intro}</p>}
          {doc.secoes.map(([titulo, paragrafos, itens]) => (
            <div key={titulo}>
              <h3>{titulo}</h3>
              {(paragrafos || []).map((p) => <p key={p}>{p}</p>)}
              {itens && <ul>{itens.map((i) => <li key={i}>{i}</li>)}</ul>}
            </div>
          ))}
        </div>
        <div className="legal-modal__foot">
          <button type="button" className="btn btn--primary" onClick={onFechar}>Concordo</button>
        </div>
      </div>
    </div>
  );
}
