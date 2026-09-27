package com.yeka.bandapp.recurring;

import com.yeka.bandapp.support.IntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 테스트 컨테이너의 별도 스키마에 V18 데이터가 있는 상태로 V19를 적용한다. */
class RecurringOriginalSlotMigrationTest extends IntegrationTestSupport {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void upgrading_existing_schedules_preserves_history_and_separates_original_slots() {
        String schema = "v19_upgrade_test";
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("18").load().migrate();
            jdbc.update("insert into v19_upgrade_test.users (id, name, social_provider, social_id, created_at) "
                    + "values (1, 'migration', 'KAKAO', 'migration-test', now())");
            jdbc.update("insert into v19_upgrade_test.bands (id, name, leader_id, created_at) values (1, 'band', 1, now())");
            jdbc.update("insert into v19_upgrade_test.rooms (id, band_id, name, created_by, created_at) "
                    + "values (1, 1, 'room', 1, now())");
            jdbc.update("""
                    insert into v19_upgrade_test.recurring_rules
                        (id, band_id, room_id, frequency, day_of_week, start_time, end_time, start_date, created_by, created_at)
                    values (1, 1, 1, 'WEEKLY', 'MONDAY', '15:00', '18:00', '2026-09-07', 1, now())
                    """);
            // 첫 회차는 이미 화요일로 옮긴 기존 이력. 원래 월요일이었다고 추정해 덮어쓰면 안 된다.
            jdbc.update("""
                    insert into v19_upgrade_test.reservations
                        (id, band_id, room_id, requested_by, status, start_at, end_at, recurring_rule_id, note, created_at)
                    values
                        (1, 1, 1, 1, 'CONFIRMED', '2026-09-08T06:00Z', '2026-09-08T09:00Z', 1, 'moved', now()),
                        (2, 1, 1, 1, 'CANCELLED', '2026-09-14T06:00Z', '2026-09-14T09:00Z', 1, 'cancelled', now()),
                        (3, 1, 1, 1, 'CONFIRMED', '2026-09-07T06:00Z', '2026-09-07T09:00Z', null, 'standalone', now())
                    """);
            jdbc.update("insert into v19_upgrade_test.settlements "
                    + "(id, reservation_id, total_amount, split_type, created_at) values (1, 1, 10000, 'EQUAL', now())");
            jdbc.update("insert into v19_upgrade_test.settlement_shares "
                    + "(id, settlement_id, user_id, amount, paid, paid_at, created_at) values (1, 1, 1, 10000, true, now(), now())");
            String reservationHistory = "select id, start_at, end_at, status, note, created_at, recurring_rule_id "
                    + "from v19_upgrade_test.reservations order by id";
            var beforeReservations = jdbc.queryForList(reservationHistory);
            var beforeSettlement = jdbc.queryForList("select * from v19_upgrade_test.settlements");
            var beforeShares = jdbc.queryForList("select * from v19_upgrade_test.settlement_shares");

            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("19").load().migrate();

            assertThat(jdbc.queryForList(reservationHistory)).isEqualTo(beforeReservations);
            assertThat(jdbc.queryForList("select * from v19_upgrade_test.settlements")).isEqualTo(beforeSettlement);
            assertThat(jdbc.queryForList("select * from v19_upgrade_test.settlement_shares")).isEqualTo(beforeShares);
            assertThat(jdbc.queryForObject("select count(*) from v19_upgrade_test.reservations "
                    + "where recurring_rule_id is not null and original_start_at = start_at", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select original_start_at is null from v19_upgrade_test.reservations where id = 3",
                    Boolean.class)).isTrue();

            // 실제 시작 시각은 같아도 허용한다. 취소된 회차도 원래 슬롯은 계속 차지한다.
            assertThat(jdbc.update("update v19_upgrade_test.reservations set "
                    + "start_at = '2026-09-14T06:00Z', end_at = '2026-09-14T09:00Z' where id = 1")).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into v19_upgrade_test.reservations
                        (id, band_id, room_id, requested_by, status, start_at, end_at, recurring_rule_id, original_start_at, created_at)
                    select 4, band_id, room_id, requested_by, 'CONFIRMED', start_at + interval '1 day',
                           end_at + interval '1 day', recurring_rule_id, original_start_at, now()
                    from v19_upgrade_test.reservations where id = 2
                    """)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("drop schema if exists v19_upgrade_test cascade");
        }
    }
}
