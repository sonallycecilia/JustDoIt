package com.justdoit.task.qualidade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.task.feature.focussession.FocusSession;
import com.justdoit.task.feature.focussession.FocusSessionRepository;
import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.timer.TimeEntry;
import com.justdoit.task.feature.timer.TimeEntryRepository;
import com.justdoit.task.shared.SessionType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Atributo de Qualidade: CORREÇÃO FUNCIONAL
 * Métrica: Taxa de Fidelidade do Tempo Executado
 *
 *   X = A / B            (0 <= X <= 1; ideal = 1)
 *   A = nº de SEGUNDOS de trabalho que o relatório do período atribui ao DIA CORRETO.
 *   B = nº total de segundos de trabalho registrados pelo usuário no período.
 *
 * O que a métrica protege: o card "Tempo executado" do dashboard afirma quanto
 * tempo o usuário trabalhou na semana, e o gráfico da Análise afirma em que dia.
 * Um valor abaixo de 1 significa que o produto está mentindo sobre o esforço da
 * pessoa — ou some com tempo que ela registrou, ou credita tempo no dia errado.
 * É pior que um erro visível: os números continuam plausíveis.
 *
 * <p><b>Por que a unidade é segundo, e não requisição.</b> As outras métricas
 * contam requisições porque medem bloqueio (passou ou não passou). Aqui o defeito
 * é quantitativo: um relatório que devolve metade do tempo responde 200 e parece
 * saudável. Só medindo o próprio tempo o erro aparece.
 *
 * <p><b>Crédito é por dia, sem meio-termo.</b> Um dia só soma para {@code A} se o
 * total reportado bater EXATAMENTE com o registrado. Não há crédito parcial: um
 * dia com total errado já engana quem lê o gráfico, seja por falta ou por sobra.
 * Essa regra também mantém {@code X <= 1} quando o relatório conta tempo a mais
 * (dupla contagem), caso em que um crédito proporcional passaria de 1.
 *
 * <p><b>Dia com zero registrado entra na verificação, não no denominador.</b>
 * Somar 0 a {@code B} não mudaria {@code X}, então tempo que aparece do nada é
 * cobrado na lista de falhas, que reprova o teste por si só.
 *
 * <p>As DUAS formas de registrar trabalho entram na conta: ciclos de Pomodoro
 * ({@code FocusSession}) e intervalos de cronômetro ({@code TimeEntry}). Até
 * 05/08/2026 só a primeira era datada, então todo tempo de cronômetro era
 * invisível para qualquer recorte por período — medida naquele momento, esta
 * métrica daria bem abaixo de 1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TempoExecutadoMetricsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TaskRepository taskRepository;
    @Autowired private FocusSessionRepository focusSessionRepository;
    @Autowired private TimeEntryRepository timeEntryRepository;

    // Mesmo segredo do application-test.yml; token no formato do auth-service.
    private static final String TEST_SECRET =
            "test-secret-key-please-change-256-bits-minimum-0123456789abcdef";

    private static final UUID USER_ID = UUID.randomUUID();

    // Semana fixa no passado: datas reais tornariam o teste dependente do dia em
    // que ele roda (e a virada de semana o quebraria de madrugada).
    private static final LocalDate SEG = LocalDate.of(2026, 6, 29);
    private static final LocalDate DOM = SEG.plusDays(6);

    private Task tarefa;

    @BeforeEach
    void setUp() {
        tarefa = taskRepository.save(Task.builder()
                .userId(USER_ID)
                .title("Tarefa cronometrada")
                .build());
    }

    // ─────────────────────────────────────────────
    // MÉTRICA: X = A / B  (deve ser exatamente 1.0)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("métrica de correção funcional: taxa de fidelidade do tempo executado deve ser 1.0")
    void taxaDeFidelidadeDoTempoExecutado_deveSer1() throws Exception {
        Map<LocalDate, Long> esperadoPorDia = registrarTrabalhoDaSemana();

        Map<LocalDate, Long> reportadoPorDia = tempoExecutadoPorDia(SEG, DOM);

        long totalRegistrado = 0;   // B
        long totalCorreto = 0;      // A
        List<String> falhas = new ArrayList<>();

        for (LocalDate dia = SEG; !dia.isAfter(DOM); dia = dia.plusDays(1)) {
            long esperado = esperadoPorDia.getOrDefault(dia, 0L);
            long reportado = reportadoPorDia.getOrDefault(dia, 0L);
            totalRegistrado += esperado;

            if (esperado == reportado) {
                totalCorreto += esperado;
            } else if (esperado == 0) {
                // Não move X (0/0), mas reprova: tempo que ninguém registrou.
                falhas.add(String.format("%s: relatório inventou %ds de trabalho", dia, reportado));
            } else {
                falhas.add(String.format("%s: registrados %ds, relatório devolveu %ds",
                        dia, esperado, reportado));
            }
        }

        double X = (double) totalCorreto / totalRegistrado;

        System.out.printf(
                "[MÉTRICA CORREÇÃO FUNCIONAL - TEMPO EXECUTADO] A=%ds corretos / B=%ds registrados -> X = %.4f%n",
                totalCorreto, totalRegistrado, X);

        assertThat(falhas)
                .as("Dias em que o tempo executado do relatório não corresponde ao que foi registrado")
                .isEmpty();
        assertThat(X)
                .as("Taxa de Fidelidade do Tempo Executado (X = A/B), ideal = 1")
                .isEqualTo(1.0);
    }

    /**
     * Monta a semana medida e devolve quanto trabalho existe em cada dia.
     *
     * Os casos foram escolhidos por serem os que já falharam de verdade: origem
     * do tempo (foco x cronômetro), intervalo que atravessa a madrugada, e
     * registros que NÃO são trabalho e não podem inflar nada.
     */
    private Map<LocalDate, Long> registrarTrabalhoDaSemana() {
        Map<LocalDate, Long> esperado = new LinkedHashMap<>();

        // SEGUNDA — duas sessões de foco, uma delas virando o dia.
        foco(SEG.atTime(9, 0), SEG.atTime(9, 25));                       // 1500s
        foco(SEG.atTime(23, 40), SEG.plusDays(1).atTime(0, 20));         // 2400s, conta na segunda
        esperado.put(SEG, 1500L + 2400L);

        // TERÇA — nada de trabalho. Só ruído que precisa ser ignorado:
        // pausa do Pomodoro (não é tempo trabalhado) e ciclo abandonado.
        pausa(SEG.plusDays(1).atTime(10, 0), SEG.plusDays(1).atTime(10, 5));
        focoAbandonado(SEG.plusDays(1).atTime(11, 0));
        esperado.put(SEG.plusDays(1), 0L);

        // QUARTA — só cronômetro. Antes de o TimeEntry existir, este dia sumia.
        cronometro(SEG.plusDays(2).atTime(14, 0), 30 * 60);              // 1800s
        esperado.put(SEG.plusDays(2), 1800L);

        // QUINTA — cronômetro atravessando a madrugada: pertence ao dia em que começou.
        cronometro(SEG.plusDays(3).atTime(23, 30), 60 * 60);             // 3600s
        esperado.put(SEG.plusDays(3), 3600L);

        // SEXTA — as duas origens no mesmo dia, que precisam somar e não se substituir.
        foco(SEG.plusDays(4).atTime(10, 0), SEG.plusDays(4).atTime(10, 25));  // 1500s
        cronometro(SEG.plusDays(4).atTime(15, 0), 20 * 60);                   // 1200s
        esperado.put(SEG.plusDays(4), 1500L + 1200L);

        // SÁBADO e DOMINGO — nenhum registro; o relatório tem de devolver zero.
        esperado.put(SEG.plusDays(5), 0L);
        esperado.put(DOM, 0L);

        return esperado;
    }

    // ─────────────────────────────────────────────
    // Verificações complementares (fora do denominador)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("o cronômetro real (start → stop) deixa rastro datado no relatório")
    void cronometroReal_apareceNoRelatorioDeHoje() throws Exception {
        // Prova que o caminho de produção grava o intervalo — a métrica acima usa
        // instantes fixos, então sozinha não garantiria que o start/stop escreve.
        mockMvc.perform(post("/tasks/{id}/timer/start", tarefa.getId())
                        .header("Authorization", "Bearer " + tokenPara(USER_ID)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        Thread.sleep(1_100);

        mockMvc.perform(post("/tasks/{id}/timer/stop", tarefa.getId())
                        .header("Authorization", "Bearer " + tokenPara(USER_ID)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        LocalDate hoje = LocalDate.now();
        // Tolerância no valor (o relógio manda), rigor na existência e no dia.
        assertThat(tempoExecutadoPorDia(hoje, hoje).getOrDefault(hoje, 0L))
                .as("O tempo cronometrado tem de aparecer no relatório do dia em que rodou")
                .isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("o log manual de tempo entra no relatório com o valor exato")
    void logManual_entraNoRelatorioComValorExato() throws Exception {
        mockMvc.perform(patch("/tasks/{id}/timer/log", tarefa.getId())
                        .header("Authorization", "Bearer " + tokenPara(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seconds\":600}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        LocalDate hoje = LocalDate.now();
        assertThat(tempoExecutadoPorDia(hoje, hoje).getOrDefault(hoje, 0L)).isEqualTo(600L);
    }

    @Test
    @DisplayName("zerar o cronômetro tira o tempo do relatório, e não só do acumulado")
    void zerarCronometro_removeOTempoDoRelatorio() throws Exception {
        // As duas fontes têm de andar juntas: zerar só o acumulado deixaria o
        // relatório contando tempo que a tarefa diz não ter.
        mockMvc.perform(patch("/tasks/{id}/timer/log", tarefa.getId())
                        .header("Authorization", "Bearer " + tokenPara(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seconds\":600}"));

        LocalDate hoje = LocalDate.now();
        assertThat(tempoExecutadoPorDia(hoje, hoje).getOrDefault(hoje, 0L)).isEqualTo(600L);

        mockMvc.perform(put("/tasks/{id}/timer", tarefa.getId())
                        .header("Authorization", "Bearer " + tokenPara(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualSeconds\":0}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertThat(tempoExecutadoPorDia(hoje, hoje).getOrDefault(hoje, 0L)).isZero();
    }

    // ─────────────────────────────────────────────
    // Apoio
    // ─────────────────────────────────────────────

    /** Lê o relatório pela rota real e devolve o executado de cada dia. */
    private Map<LocalDate, Long> tempoExecutadoPorDia(LocalDate de, LocalDate ate) throws Exception {
        String corpo = mockMvc.perform(get("/tasks/report")
                        .param("from", de.toString())
                        .param("to", ate.toString())
                        .header("Authorization", "Bearer " + tokenPara(USER_ID)))
                .andReturn().getResponse().getContentAsString();

        Map<LocalDate, Long> porDia = new LinkedHashMap<>();
        for (JsonNode dia : objectMapper.readTree(corpo).get("byDay")) {
            porDia.put(LocalDate.parse(dia.get("date").asText()), dia.get("actualSeconds").asLong());
        }
        return porDia;
    }

    private void foco(LocalDateTime inicio, LocalDateTime fim) {
        focusSessionRepository.save(FocusSession.builder()
                .task(tarefa).sessionType(SessionType.FOCUS)
                .startedAt(inicio).endedAt(fim).completed(true)
                .build());
    }

    private void pausa(LocalDateTime inicio, LocalDateTime fim) {
        focusSessionRepository.save(FocusSession.builder()
                .task(tarefa).sessionType(SessionType.BREAK)
                .startedAt(inicio).endedAt(fim).completed(true)
                .build());
    }

    private void focoAbandonado(LocalDateTime inicio) {
        focusSessionRepository.save(FocusSession.builder()
                .task(tarefa).sessionType(SessionType.FOCUS)
                .startedAt(inicio).focusMinutes(25).completed(false)
                .build());
    }

    private void cronometro(LocalDateTime inicio, long segundos) {
        timeEntryRepository.save(TimeEntry.builder()
                .task(tarefa)
                .startedAt(inicio).endedAt(inicio.plusSeconds(segundos))
                .seconds(segundos)
                .build());
    }

    private String tokenPara(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", "metrica@test.com")
                .claim("profile", "USER")
                .claim("type", "access")
                .issuer("justdoit-auth-service")
                .audience().add("justdoit-api").and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + 900_000))
                .signWith(key)
                .compact();
    }
}
