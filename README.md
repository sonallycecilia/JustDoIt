# JustDoIt — Gerenciador de Tarefas

O **JustDoIt** é uma plataforma web focada no gerenciamento de tarefas e produtividade pessoal por meio da metodologia de blocos de tempo (*time-blocking*). O sistema foi concebido para mitigar a sobrecarga mental e otimizar fluxos de trabalho através da segregação sistemática de demandas em contextos específicos e monitoramento analítico de esforço.

---

## O Conceito do Projeto

A essência do **JustDoIt** baseia-se no princípio de que a produtividade humana atinge seu ápice quando a fricção cognitiva inicial é minimizada e a troca de contexto (*context-switching*) é controlada. A aplicação substitui as tradicionais e estáticas listas de tarefas por uma interface de planejamento dinâmico orientada por três pilares fundamentais:

* **Estruturação Hierárquica:** Permite o fracionamento manual de macro-objetivos complexos em uma cadeia sequencial de micro-tarefas perfeitamente acionáveis.
* **Foco Contextual (Time-Blocking):** Centraliza a organização da rotina ao alocar atividades dentro de blocos de tempo rígidos atrelados a contextos operacionais (ex: Trabalho, Universidade, Vida Pessoal).
* **Auditoria Temporal (Time Tracking):** Instrumentação nativa de cronometragem analítica para medir e registrar quantitativamente o tempo real investido em cada entrega, promovendo um planejamento empírico contínuo.

---

## Filosofia Arquitetural

Para garantir a viabilidade técnica, estabilidade e resiliência desse ecossistema, o projeto adota os padrões mais consolidados de engenharia de software moderno:

* **Arquitetura de Microsserviços Desacoplados:** Separação estrita das responsabilidades de negócio (Autenticação, Gestão de Tarefas, Agendamento de Cronograma, Notificações e Portabilidade de Dados).
* **Arquitetura Orientada a Eventos (EDA):** Integração assíncrona entre serviços utilizando o barramento **Apache Kafka**, garantindo que operações pesadas de background e notificações não impactem a experiência em tempo real da interface.
* **Isolamento de Persistência (Database-per-Service):** Uso estratégico de bancos de dados relacionais para consistência transacional e cache em memória (**Redis**) para filas e limites de tráfego, garantindo performance e segurança perimetral.

