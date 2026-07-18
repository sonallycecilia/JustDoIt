package com.justdoit.task.feature.note;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Usa um banco H2 dedicado (isolado do usado pelos demais testes de integração)
 * para poder criar a tabela legada {@code user_note} sem contaminar outros
 * contextos.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:notemigtest;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class UserNoteMigrationRunnerTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserNoteMigrationRunner runner;
    @Autowired private NoteRepository noteRepository;

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_note");
        noteRepository.deleteAll();
    }

    private void createLegacyUserNote(UUID id, UUID userId, String content) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_note (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL,
                    content TEXT,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )""");
        jdbcTemplate.update(
                "INSERT INTO user_note (id, user_id, content, created_at, updated_at) VALUES (?,?,?,?,?)",
                id, userId, content,
                Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));
    }

    @Test
    void migratesLegacyBlockAsPinnedNote_keepingSameId() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        createLegacyUserNote(id, userId, "Meu bloco");

        runner.run(null);
        runner.run(null); // idempotência: segunda execução não duplica

        List<Note> notes = noteRepository.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId);
        assertEquals(1, notes.size(), "deve migrar exatamente uma nota");
        Note migrated = notes.get(0);
        assertEquals(id, migrated.getId(), "reusa o mesmo id do bloco antigo");
        assertEquals("Meu bloco", migrated.getContent());
        assertTrue(migrated.isPinned(), "a nota migrada deve ser a fixada");
    }

    @Test
    void doesNothing_whenLegacyTableAbsent() {
        // Sem criar user_note: o runner deve apenas retornar sem erro.
        assertDoesNotThrow(() -> runner.run(null));
        assertTrue(noteRepository.findAll().isEmpty());
    }
}
