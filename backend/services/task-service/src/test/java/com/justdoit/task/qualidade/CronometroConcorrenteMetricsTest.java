package com.justdoit.task.qualidade;

import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.timer.ActiveTimerRepository;
import com.justdoit.task.feature.timer.TaskTimer;
import com.justdoit.task.feature.timer.TaskTimerRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Atributo de Qualidade: DESEMPENHO
 * Métrica: Taxa de Bloqueio de Cronômetro Concorrente
 *
 *   X = A / B            (0 <= X <= 1; ideal = 1)
 *   A = nº de tentativas de acionamento simultâneo que foram BLOQUEADAS com sucesso,
 *       mantendo ativo apenas o primeiro cronômetro.
 *   B = nº total de tentativas de acionamento concorrente simuladas nos testes.
 *
 * Qualquer valor abaixo de 1 indica uma falha crítica onde duas tarefas acumularam tempo
 * simultaneamente, corrompendo a precisão das métricas do usuário.
 *
 * <p><b>Como B é contado.</b> Numa rodada de N acionamentos disparados ao mesmo tempo por U
 * usuários distintos, exatamente U devem passar (o primeiro de cada usuário) e N - U devem
 * ser bloqueados com 409. B soma esses N - U — as tentativas que <i>deviam</i> ser barradas.
 * Incluir os vencedores legítimos em B tornaria X = 1 inalcançável por construção: um
 * cronômetro precisa poder ser acionado.
 *
 * <p>A proteção medida é o índice único em {@code active_timer.user_id}
 * ({@code feature/timer/ActiveTimer}); o serviço traduz a violação em 409.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Sem @Transactional, ao contrário das outras classes de qualidade: a transação do teste é
// thread-local, então as threads que disparam as requisições não enxergariam os dados e o
// rollback não as alcançaria. A limpeza é manual, restrita aos dados criados aqui.
class CronometroConcorrenteMetricsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskTimerRepository timerRepository;
    @Autowired private ActiveTimerRepository activeTimerRepository;

    // Mesmo segredo do application-test.yml; token no formato do auth-service.
    private static final String TEST_SECRET =
            "test-secret-key-please-change-256-bits-minimum-0123456789abcdef";

    /** Quantas vezes cada cenário é repetido — uma corrida só não prova ausência de brecha. */
    private static final int REPETICOES = 5;

    private final List<UUID> usuariosCriados = new ArrayList<>();
    private final List<UUID> tarefasCriadas = new ArrayList<>();

    @BeforeEach
    void limparRegistros() {
        usuariosCriados.clear();
        tarefasCriadas.clear();
    }

    @AfterEach
    void limparDados() {
        usuariosCriados.forEach(this::pararCronometroDoUsuario);
        tarefasCriadas.forEach(taskId ->
                timerRepository.findByTaskId(taskId).ifPresent(timerRepository::delete));
        taskRepository.deleteAllById(tarefasCriadas);
    }

    // ─────────────────────────────────────────────
    // MÉTRICA: X = A / B  (deve ser exatamente 1.0)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("métrica de desempenho: taxa de bloqueio de cronômetro concorrente deve ser 1.0")
    void taxaDeBloqueioDeCronometroConcorrente_deveSer1() throws Exception {
        int totalConcorrentes = 0;  // B
        int totalBloqueadas = 0;    // A
        List<String> falhas = new ArrayList<>();

        for (int rodada = 1; rodada <= REPETICOES; rodada++) {
            for (Cenario cenario : cenarios()) {
                Resultado r = medir(cenario, rodada, falhas);
                totalConcorrentes += r.esperadasBloqueadas();
                totalBloqueadas += r.bloqueadas();
            }
        }

        double X = (double) totalBloqueadas / totalConcorrentes;

        System.out.printf(
                "[MÉTRICA DESEMPENHO - CRONÔMETRO CONCORRENTE] A=%d bloqueadas / B=%d concorrentes -> X = %.4f%n",
                totalBloqueadas, totalConcorrentes, X);

        assertThat(falhas)
                .as("Falha crítica: acionamentos simultâneos que NÃO foram bloqueados")
                .isEmpty();
        assertThat(X)
                .as("Taxa de Bloqueio de Cronômetro Concorrente (X = A/B), ideal = 1")
                .isEqualTo(1.0);
    }

    /**
     * Os três formatos de disputa que o produto sofre na prática.
     *
     * <p>C3 é o que impede a proteção de ser grosseira demais: se ela travasse o sistema
     * inteiro em vez de um usuário por vez, o cenário acusaria vencedores a menos.
     */
    private List<Cenario> cenarios() {
        return List.of(
                new Cenario("C1 tarefas distintas, 1 usuário", 10, false, 1),
                new Cenario("C2 mesma tarefa (duplo clique), 1 usuário", 10, true, 1),
                new Cenario("C3 dois usuários em paralelo", 10, false, 2));
    }

    private Resultado medir(Cenario cenario, int rodada, List<String> falhas) throws Exception {
        List<UUID> usuarios = new ArrayList<>();
        for (int i = 0; i < cenario.usuarios(); i++) usuarios.add(novoUsuario());

        List<Acionamento> acionamentos = new ArrayList<>();
        UUID tarefaCompartilhada = cenario.mesmaTarefa() ? novaTarefa(usuarios.get(0)) : null;
        for (int i = 0; i < cenario.disparos(); i++) {
            UUID usuario = usuarios.get(i % usuarios.size());
            acionamentos.add(new Acionamento(usuario,
                    cenario.mesmaTarefa() ? tarefaCompartilhada : novaTarefa(usuario)));
        }

        List<Integer> status = dispararSimultaneamente(acionamentos);

        long vencedores = status.stream().filter(s -> s >= 200 && s < 300).count();
        long bloqueadas = status.stream().filter(s -> s == 409).count();
        int esperadasBloqueadas = cenario.disparos() - cenario.usuarios();

        String origem = String.format("%s (rodada %d)", cenario.nome(), rodada);
        if (vencedores != cenario.usuarios()) {
            falhas.add(String.format("%s -> %d acionamentos aceitos, esperado %d; status=%s",
                    origem, vencedores, cenario.usuarios(), status));
        }
        for (UUID usuario : usuarios) {
            long ativos = activeTimerRepository.countByUserId(usuario);
            if (ativos != 1) {
                falhas.add(String.format("%s -> usuário ficou com %d cronômetros ativos, esperado 1",
                        origem, ativos));
            }
        }

        usuarios.forEach(this::pararCronometroDoUsuario);
        return new Resultado(esperadasBloqueadas, (int) bloqueadas);
    }

    // ─────────────────────────────────────────────
    // Nenhuma tarefa acumula tempo em paralelo
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("apenas a tarefa do cronômetro vencedor acumula tempo — as demais ficam em zero")
    void somenteUmaTarefaAcumulaTempo() throws Exception {
        UUID usuario = novoUsuario();
        UUID tarefaA = novaTarefa(usuario);
        UUID tarefaB = novaTarefa(usuario);

        List<Acionamento> acionamentos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            acionamentos.add(new Acionamento(usuario, i % 2 == 0 ? tarefaA : tarefaB));
        }
        dispararSimultaneamente(acionamentos);

        UUID vencedora = activeTimerRepository.findByUserId(usuario).orElseThrow().getTaskId();
        UUID perdedora = vencedora.equals(tarefaA) ? tarefaB : tarefaA;

        // Tempo de relógio: a asserção é "acumulou algo", nunca um valor exato.
        Thread.sleep(1_300);
        mockMvc.perform(post("/tasks/{id}/timer/stop", vencedora)
                        .header("Authorization", "Bearer " + tokenPara(usuario)))
                .andReturn();

        assertThat(segundosAcumulados(vencedora))
                .as("a tarefa cronometrada deveria ter acumulado tempo")
                .isGreaterThan(0L);
        assertThat(segundosAcumulados(perdedora))
                .as("tarefa bloqueada acumulou tempo em paralelo — é a corrupção que a métrica proíbe")
                .isZero();
    }

    // ─────────────────────────────────────────────
    // Guarda contra falso positivo
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("o bloqueio não gruda: depois do stop o usuário volta a cronometrar normalmente")
    void usoLegitimoDoCronometroContinuaFuncionando() throws Exception {
        UUID usuario = novoUsuario();
        UUID tarefaA = novaTarefa(usuario);
        UUID tarefaB = novaTarefa(usuario);
        List<String> bloqueiosIndevidos = new ArrayList<>();

        registrar(bloqueiosIndevidos, "start inicial em A", start(usuario, tarefaA));
        registrar(bloqueiosIndevidos, "stop em A", stop(usuario, tarefaA));
        registrar(bloqueiosIndevidos, "start em B depois do stop", start(usuario, tarefaB));
        registrar(bloqueiosIndevidos, "stop em B", stop(usuario, tarefaB));
        registrar(bloqueiosIndevidos, "start de novo na mesma tarefa A", start(usuario, tarefaA));

        assertThat(bloqueiosIndevidos)
                .as("Falso positivo: uso sequencial legítimo do cronômetro foi barrado")
                .isEmpty();
    }

    @Test
    @DisplayName("a trava é por usuário: dois usuários cronometram ao mesmo tempo sem se bloquear")
    void usuariosDiferentesCronometramEmParalelo() throws Exception {
        UUID usuarioA = novoUsuario();
        UUID usuarioB = novoUsuario();

        assertThat(start(usuarioA, novaTarefa(usuarioA))).isEqualTo(200);
        assertThat(start(usuarioB, novaTarefa(usuarioB))).isEqualTo(200);
        assertThat(activeTimerRepository.countByUserId(usuarioA)).isEqualTo(1);
        assertThat(activeTimerRepository.countByUserId(usuarioB)).isEqualTo(1);
    }

    // ─────────────────────────────────────────────
    // Soma concorrente sem perda (fora do denominador)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("logs concorrentes somam sem perder tempo: 10 requisições de 1s resultam em 10s")
    void logsConcorrentesNaoPerdemTempo() throws Exception {
        UUID usuario = novoUsuario();
        UUID tarefa = novaTarefa(usuario);

        List<Callable<Integer>> disparos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            disparos.add(() -> mockMvc.perform(patch("/tasks/{id}/timer/log", tarefa)
                            .header("Authorization", "Bearer " + tokenPara(usuario))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"seconds\":1}"))
                    .andReturn().getResponse().getStatus());
        }

        assertThat(executarJuntos(disparos)).allMatch(s -> s == 200);
        // Com leitura-modificação-escrita em memória, parte dos incrementos se perderia aqui.
        assertThat(segundosAcumulados(tarefa))
                .as("segundos perdidos por atualização concorrente")
                .isEqualTo(10L);
    }

    // ─────────────────────────────────────────────
    // Infra de concorrência (só JDK)
    // ─────────────────────────────────────────────

    private record Cenario(String nome, int disparos, boolean mesmaTarefa, int usuarios) {}

    private record Acionamento(UUID userId, UUID taskId) {}

    private record Resultado(int esperadasBloqueadas, int bloqueadas) {}

    private List<Integer> dispararSimultaneamente(List<Acionamento> acionamentos) throws Exception {
        List<Callable<Integer>> disparos = acionamentos.stream()
                .map(a -> (Callable<Integer>) () -> start(a.userId(), a.taskId()))
                .toList();
        return executarJuntos(disparos);
    }

    /**
     * Executa os disparos de fato ao mesmo tempo: cada thread se declara pronta e fica
     * bloqueada na largada, que só é liberada quando todas já estão esperando. Sem isso, a
     * primeira requisição terminaria antes de a última thread nascer e não haveria disputa.
     */
    private List<Integer> executarJuntos(List<Callable<Integer>> disparos) throws Exception {
        int n = disparos.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch prontas = new CountDownLatch(n);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Callable<Integer> disparo : disparos) {
                futuros.add(pool.submit(() -> {
                    prontas.countDown();
                    largada.await();
                    return disparo.call();
                }));
            }
            assertThat(prontas.await(30, TimeUnit.SECONDS))
                    .as("as threads de disparo não ficaram prontas a tempo").isTrue();
            largada.countDown();

            List<Integer> status = new ArrayList<>();
            for (Future<Integer> futuro : futuros) status.add(futuro.get(60, TimeUnit.SECONDS));
            return status;
        } finally {
            pool.shutdownNow();
        }
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private int start(UUID userId, UUID taskId) throws Exception {
        return mockMvc.perform(post("/tasks/{id}/timer/start", taskId)
                        .header("Authorization", "Bearer " + tokenPara(userId)))
                .andReturn().getResponse().getStatus();
    }

    private int stop(UUID userId, UUID taskId) throws Exception {
        return mockMvc.perform(post("/tasks/{id}/timer/stop", taskId)
                        .header("Authorization", "Bearer " + tokenPara(userId)))
                .andReturn().getResponse().getStatus();
    }

    private void registrar(List<String> falhas, String passo, int status) {
        if (status < 200 || status >= 300) falhas.add(passo + " -> status " + status);
    }

    private long segundosAcumulados(UUID taskId) {
        return timerRepository.findByTaskId(taskId).map(TaskTimer::getActualSeconds).orElse(0L);
    }

    /** Encerra o cronômetro do usuário direto no repositório, para não medir o tempo do stop. */
    private void pararCronometroDoUsuario(UUID userId) {
        activeTimerRepository.findByUserId(userId).ifPresent(activeTimerRepository::delete);
    }

    private UUID novoUsuario() {
        UUID userId = UUID.randomUUID();
        usuariosCriados.add(userId);
        return userId;
    }

    private UUID novaTarefa(UUID userId) {
        Task tarefa = taskRepository.save(
                Task.builder().userId(userId).title("Tarefa cronometrada").build());
        tarefasCriadas.add(tarefa.getId());
        return tarefa.getId();
    }

    private String tokenPara(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer("justdoit-auth-service")
                .audience().add("justdoit-api").and()
                .claim("email", "metrica@test.com")
                .claim("profile", "USER")
                .claim("type", "access")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 900_000))
                .signWith(key)
                .compact();
    }
}
