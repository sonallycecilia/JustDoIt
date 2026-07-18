package com.justdoit.task.feature.note;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migração única, idempotente, dos dados do antigo bloco de anotações
 * ({@code user_note}) para a nova tabela {@code note}, como nota FIXADA.
 *
 * <p>Roda no boot. É seguro porque:
 * <ul>
 *   <li>o Hibernate cria {@code note} antes do runner rodar e NUNCA dropa
 *       {@code user_note} — os dados antigos ficam como backup;</li>
 *   <li>reusa o MESMO id de cada linha, então {@code GET /me/note} devolve o
 *       mesmo id de antes;</li>
 *   <li>o {@code WHERE NOT EXISTS} torna reexecuções e usuários que já criaram
 *       nota fixada nova inofensivos.</li>
 * </ul>
 *
 * <p>Após confirmar a migração em produção, a tabela {@code user_note} pode ser
 * removida manualmente ({@code DROP TABLE user_note}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserNoteMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!userNoteTableExists()) {
            return; // instalações novas e o contexto de teste padrão não têm user_note
        }
        int migrated = jdbcTemplate.update("""
                INSERT INTO note (id, user_id, title, content, pinned, created_at, updated_at)
                SELECT un.id, un.user_id, NULL, un.content, TRUE, un.created_at, un.updated_at
                FROM user_note un
                WHERE NOT EXISTS (
                    SELECT 1 FROM note n WHERE n.user_id = un.user_id AND n.pinned = TRUE
                )
                """);
        if (migrated > 0) {
            log.info("Migradas {} anotacao(oes) de user_note para note (como nota fixada)", migrated);
        }
    }

    /**
     * Checagem de existência portável (H2 e MySQL): tenta um count; se a tabela
     * não existe, o driver lança BadSqlGrammarException (subtipo de
     * DataAccessException) e tratamos como ausente — evita a incompatibilidade
     * de case/schema do information_schema entre bancos.
     */
    private boolean userNoteTableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_note", Integer.class);
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }
}
