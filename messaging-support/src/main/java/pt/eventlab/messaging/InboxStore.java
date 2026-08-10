package pt.eventlab.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class InboxStore {

    private final JdbcTemplate jdbcTemplate;

    InboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claim(UUID messageId, String handler) {
        return jdbcTemplate.update("""
                insert into inbox_messages (message_id, handler_name, processed_at)
                values (?, ?, ?)
                on conflict (message_id, handler_name) do nothing
                """, messageId, handler, Timestamp.from(Instant.now())) == 1;
    }
}
