package com.yeka.bandapp.support;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 테스트 간 상태 격리. {@code webEnvironment=RANDOM_PORT}는 서버가 별도 스레드에서 돌아
 * {@code @Transactional} 롤백이 통하지 않으므로, 매 테스트 전에 명시적으로 비운다.
 * Phase가 늘어 테이블이 추가돼도 수정할 필요가 없다(public 스키마 전체 대상).
 */
@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public void clean() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
                String.class);
        if (!tables.isEmpty()) {
            String joined = tables.stream().map(t -> '"' + t + '"').collect(Collectors.joining(", "));
            jdbcTemplate.execute("TRUNCATE TABLE " + joined + " RESTART IDENTITY CASCADE");
        }
        try (RedisConnection connection = redisTemplate.getRequiredConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
