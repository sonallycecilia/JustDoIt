package com.justdoit.task.feature.task;

import com.justdoit.task.shared.Priority;
import com.justdoit.task.shared.TaskStatus;
import com.justdoit.task.feature.category.Category;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task")
public class Task {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Vínculo de série cíclica: nas ocorrências GERADAS por uma tarefa recorrente,
    // aponta para o id da tarefa-modelo. Null em tarefas normais e no próprio modelo.
    // Permite contar/limitar quantas ocorrências futuras existem e limpá-las.
    @Column(name = "series_id")
    private UUID seriesId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Quando a tarefa foi concluída (null se nunca/reaberta). Diferente de
    // updatedAt (que muda em qualquer edição), permite relatórios por período
    // ("concluídas na semana") consumidos pelo schedule-service.
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SubTask> subTasks = new ArrayList<>();

    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TaskModuleConfig moduleConfig;

    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TaskTimer timer;

    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TaskNote note;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<FocusSession> focusSessions = new ArrayList<>();

    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CycleConfig cycleConfig;
}
