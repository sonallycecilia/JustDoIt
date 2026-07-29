package com.justdoit.task.feature.timer;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cronômetro em curso. Existe UMA linha por usuário enquanto ele estiver cronometrando
 * alguma tarefa — a linha é criada no start e apagada no stop.
 *
 * <p>A regra de negócio "um usuário só pode ter um cronômetro ativo por vez" é o índice
 * único em {@code user_id}, e não um {@code if} no serviço: entre uma verificação de
 * aplicação e o insert cabe outra thread, e o objetivo aqui é justamente resistir a
 * acionamentos simultâneos (duas abas, duplo clique, retry do cliente).
 *
 * <p>Tabela separada, em vez de um campo {@code started_at} em {@link TaskTimer}, porque
 * a exclusividade é por <b>usuário</b> e o MySQL não tem índice único parcial — não há
 * como declarar "único quando está rodando". Com uma linha por cronômetro ativo, a
 * constraint vira um simples UNIQUE, que se comporta igual no H2 dos testes e no MySQL
 * de produção, e vale inclusive entre instâncias do serviço.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "active_timer")
public class ActiveTimer {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** Dono do cronômetro. UNIQUE: é o que garante um único cronômetro ativo por usuário. */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /** Instante do start, medido pelo servidor. O stop usa isto para calcular o decorrido. */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
}
