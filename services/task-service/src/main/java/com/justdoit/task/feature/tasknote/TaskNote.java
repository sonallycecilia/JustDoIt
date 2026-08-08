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
 * entidade TaskNote -> anotações de uma Tarefa.
 */

// --- Anotações do Lombok ---
@Data                // getters, setters, toString, equals e hashCode.
@Builder             // permite instanciar objetos de forma fluida
@NoArgsConstructor   // cria um construtor sem argumentos 
@AllArgsConstructor  //cria um construtor com todos os atributos

// --- jpa / hibernate ---
@Entity              // Avisa ao Spring que esta classe deve ser mapeada para uma tabela no banco de dados.
@Table(name = "task_note") 
public class TaskNote {

    @Id             
    @GeneratedValue 
    @UuidGenerator   
    private UUID id;

    // relação "1 para 1": Uma anotação pertence a apenas uma tarefa.
    @OneToOne(fetch = FetchType.LAZY)
    
    // Configura a coluna de Chave Estrangeira (Foreign Key).
    // garante que não existirão duas anotações apontando para a mesma tarefa.
    // exige que toda anotação esteja obrigatoriamente vinculada a uma tarefa.
    @JoinColumn(name = "task_id", unique = true, nullable = false)
    private Task task;

    // força o tipo TEXT, que permite armazenar textos longos
    @Column(columnDefinition = "TEXT")
    private String content;

    // preenche a data atual automaticamente no momento do INSERT.
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}