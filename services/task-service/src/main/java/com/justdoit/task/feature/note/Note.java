package com.justdoit.task.feature.note;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade que representa a tabela "note" no banco de dados.
 * task note - anotação especifica de uma tarefa
 * note - são todas as anotações livres que um usuário pode fazer independente de tarefas
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity

// cria um index para efetuar o processo de busca 
// O banco de dados não precisa ler a tabela inteira para achar as notas de um usuário, ele vai direto ao ponto.
@Table(name = "note", indexes = @Index(name = "idx_note_user", columnList = "user_id"))
public class Note {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // relação do tipo "muitos para um": Muitas anotações podem pertencer a um mesmo usuário.
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // define um titulo para a anotação, que será exibido posteriormente na listagem de anotações do usuário.
    @Column(length = 255)
    private String title;

    //o corpo da anotação tem o tipo TEXT, que permite armazenar textos longos
    @Column(columnDefinition = "TEXT")
    private String content;

    // funcionalidade que permite fixar uma anotação no topo da lista de anotações
    @Column(nullable = false)
    private boolean pinned;

    // criação e atualização da anotação são preenchidas automaticamente pelo banco de dados.
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}