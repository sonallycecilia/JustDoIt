package com.justdoit.task.feature.note;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Anotação livre do usuário. Um usuário tem várias notas (aba "Anotações"), e
 * no máximo UMA marcada como {@code pinned} — a nota fixada, exibida no topo da
 * página To Do e servida pelo endpoint de compatibilidade {@code /me/note}.
 * A unicidade da nota fixada é garantida na camada de serviço (ver NoteService),
 * já que o MySQL não cria índice único parcial pelo ddl-auto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "note", indexes = @Index(name = "idx_note_user", columnList = "user_id"))
public class Note {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean pinned;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
