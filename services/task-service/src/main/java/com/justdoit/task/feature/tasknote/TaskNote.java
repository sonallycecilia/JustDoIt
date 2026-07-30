package com.justdoit.task.feature.tasknote;

import com.justdoit.task.feature.task.Task;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade que representa a tabela "task_note" no banco de dados.
 * Esta classe serve como o molde para salvar as anotações ricas específicas de uma Tarefa.
 */

// --- Anotações do Lombok (Reduzem código boilerplate/repetitivo) ---
@Data                // Gera automaticamente Getters, Setters, toString, equals e hashCode.
@Builder             // Permite instanciar objetos de forma fluida (ex: TaskNote.builder().content("...").build()).
@NoArgsConstructor   // Cria um construtor vazio (exigência obrigatória do JPA/Hibernate).
@AllArgsConstructor  // Cria um construtor com todos os atributos (usado nos bastidores pelo @Builder).

// --- Anotações do JPA/Hibernate (Mapeamento Objeto-Relacional) ---
@Entity              // Avisa ao Spring que esta classe deve ser mapeada para uma tabela no banco de dados.
@Table(name = "task_note") // Força o nome da tabela no banco (boa prática para evitar nomes em CamelCase gerados automaticamente).
public class TaskNote {

    @Id              // Define este campo como a Chave Primária (Primary Key) da tabela.
    @GeneratedValue  // Diz ao banco que o valor deve ser gerado automaticamente.
    @UuidGenerator   // Especifica que a chave não será um número sequencial (1, 2, 3), mas sim um hash UUID seguro.
    private UUID id;

    // Configura a relação "1 para 1": Uma anotação pertence a apenas uma tarefa.
    // fetch = FetchType.LAZY: Só carrega os dados completos da Tarefa associada se o código explicitamente pedir (note.getTask()), poupando memória e CPU.
    @OneToOne(fetch = FetchType.LAZY)
    
    // Configura a coluna de Chave Estrangeira (Foreign Key).
    // unique = true: Garante que não existirão duas anotações apontando para a mesma tarefa.
    // nullable = false: Exige que toda anotação esteja obrigatoriamente vinculada a uma tarefa.
    @JoinColumn(name = "task_id", unique = true, nullable = false)
    private Task task;

    // Modifica o tipo da coluna gerada no banco. 
    // Em vez de String virar um VARCHAR(255) pequeno, força a ser do tipo TEXT, 
    // essencial para suportar o volume de dados de um Editor de Texto Rico (imagens em base64, links, HTML).
    @Column(columnDefinition = "TEXT")
    private String content;

    // Audita a criação: o Hibernate preenche a data atual automaticamente no momento do INSERT.
    // updatable = false: Trava a coluna no banco para que ela nunca mude, mesmo se a nota for editada no futuro.
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Audita a modificação: o Hibernate atualiza a data atual automaticamente sempre que houver um UPDATE nesta nota.
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}