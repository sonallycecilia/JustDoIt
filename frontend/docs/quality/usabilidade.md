# Usabilidade e responsividade

Última evidência conferida: CI do commit `1b4663b`, executado em
21/08/2026 UTC no GitHub Actions.

| Verificação | Situação | Cobertura |
|---|---|---|
| axe-core em Chromium | APROVADA | 13/13 rotas auditáveis |
| Responsividade compacta | APROVADA | 13 rotas em 320 × 568 |
| Responsividade padrão | APROVADA | 13 rotas em 390 × 844 |
| Interações móveis específicas | APROVADAS | 3 cenários |

O total da suíte responsiva foi **29/29**. Os três cenários específicos verificam:

1. abrir, usar e fechar o menu em 320 px;
2. redimensionar o menu pela borda direita em 390 px;
3. rolar o calendário completo até a legenda sem cortar a grade.

A auditoria procura overflow horizontal, controles cortados e campos invisíveis.
O cadastro também é coberto em 320 px, incluindo o Turnstile compacto.

## O que ainda depende de avaliação humana

- leitor de tela e qualidade dos anúncios;
- contraste em estados não alcançados pela automação;
- compreensão dos textos e dos fluxos;
- conforto do gesto de arrastar em aparelhos físicos;
- taxa de conclusão e tempo real de uma jornada.

Existe instrumentação local `jdi_usability_flows`, mas ela não agrega dados de
usuários em produção. Portanto, taxa de conclusão e tempo por operação ainda não
são métricas implementadas do produto.

[Consultar execução](https://github.com/sonallycecilia/justdoit-frontend/actions/runs/32437634036)
