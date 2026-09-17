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
 * Script de migração de dados.
 *  pega tudo que estava na tabela velha (user_note) e insere na nova (note)
 *  transformando a nota antiga na nota "Fixada" atual.
 */
@Slf4j 
@Component 
@RequiredArgsConstructor
public class UserNoteMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        
        // 1. CHECAGEM DE SEGURANÇA: Se for uma instalação nova do zero, 
        // a tabela velha nem existe. Então não faz nada e aborta a missão.
        if (!userNoteTableExists()) {
            return; 
        }
        
        // 2. A MIGRAÇÃO: Pega tudo que estava na tabela velha (user_note) e insere na nova (note)
        int migrated = jdbcTemplate.update("""
                INSERT INTO note (id, user_id, title, content, pinned, created_at, updated_at)
                SELECT un.id, un.user_id, NULL, un.content, TRUE, un.created_at, un.updated_at
                FROM user_note un
                
                -- IDEMPOTÊNCIA (A cláusula salva-vidas): 
                -- O "WHERE NOT EXISTS" garante que se esse código rodar 50 vezes 
                -- (ex: toda vez que o servidor reiniciar), ele NÃO vai duplicar as notas. 
                -- Ele só migra a nota se o usuário já não tiver uma nota fixada lá na tabela nova.
                WHERE NOT EXISTS (
                    SELECT 1 FROM note n WHERE n.user_id = un.user_id AND n.pinned = TRUE
                )
                """);
                
        // 3. LOGGING: Se ele migrou pelo menos 1 nota, avisa no console do servidor.
        if (migrated > 0) {
            log.info("Migradas {} anotacao(oes) de user_note para note (como nota fixada)", migrated);
        }
    }

    /**
     * Função para checar se uma tabela existe no banco de dados.
     */
    private boolean userNoteTableExists() {
        try {
            // Tenta contar as linhas da tabela velha.
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_note", Integer.class);
            return true; // Se não der erro, a tabela existe!
        } catch (DataAccessException e) {
            // Se o banco gritar "Tabela não existe!" (BadSqlGrammarException), 
            // a gente captura o erro silenciosamente e retorna falso.
            return false;
        }
    }
}