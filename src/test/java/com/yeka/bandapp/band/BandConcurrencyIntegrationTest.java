package com.yeka.bandapp.band;

import com.yeka.bandapp.band.dto.CreateBandRequest;
import com.yeka.bandapp.band.dto.UpdateBandSettingsRequest;
import com.yeka.bandapp.band.entity.BandMemberRole;
import com.yeka.bandapp.band.entity.ReservationPermission;
import com.yeka.bandapp.band.repository.BandInviteRepository;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.band.service.BandInviteService;
import com.yeka.bandapp.band.service.BandMemberService;
import com.yeka.bandapp.band.service.BandService;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.user.repository.UserRepository;
import com.yeka.bandapp.user.service.UserAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 DB 트랜잭션을 겹치고, 두 번째 요청의 잠금 대기를 확인한 뒤 첫 요청을 커밋한다. */
class BandConcurrencyIntegrationTest extends BandApiSupport {
    @Autowired BandMemberService memberService;
    @Autowired BandInviteService inviteService;
    @Autowired BandService bandService;
    @Autowired UserAccountService accounts;
    @Autowired BandMemberRepository members;
    @Autowired BandInviteRepository invites;
    @Autowired BandRepository bands;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    enum Departure { LEAVE, KICK, WITHDRAW }

    @ParameterizedTest
    @EnumSource(Departure.class)
    void departure_before_delegation_keeps_the_original_leader(Departure departure) throws Exception {
        String leader = signup("race-leader@band.app", "leader");
        String member = signup("race-member@band.app", "member");
        long band = createBand(leader, "departure first");
        long leaderId = myUserId(leader);
        long memberId = myUserId(member);
        assertThat(join(member, issueInvite(leader, band, null)).getStatusCode().value()).isEqualTo(200);

        assertThat(overlap(() -> depart(departure, band, leaderId, memberId),
                () -> memberService.delegateLeadership(band, leaderId, memberId)))
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        assertLeader(band, leaderId, 1);
    }

    @ParameterizedTest
    @EnumSource(Departure.class)
    void departure_after_delegation_checks_the_new_role(Departure departure) throws Exception {
        String leader = signup("race-leader@band.app", "leader");
        String member = signup("race-member@band.app", "member");
        long band = createBand(leader, "delegation first");
        long leaderId = myUserId(leader);
        long memberId = myUserId(member);
        assertThat(join(member, issueInvite(leader, band, null)).getStatusCode().value()).isEqualTo(200);

        ErrorCode error = overlap(() -> memberService.delegateLeadership(band, leaderId, memberId),
                () -> depart(departure, band, leaderId, memberId));

        switch (departure) {
            case LEAVE -> assertThat(error).isEqualTo(ErrorCode.LEADER_MUST_DELEGATE_BEFORE_LEAVING);
            case KICK -> assertThat(error).isEqualTo(ErrorCode.NOT_BAND_LEADER);
            case WITHDRAW -> assertThat(error).isNull();
        }
        assertLeader(band, departure == Departure.WITHDRAW ? leaderId : memberId,
                departure == Departure.WITHDRAW ? 1 : 2);
    }

    @Test
    void settings_and_delegation_do_not_overwrite_each_other() throws Exception {
        String leader = signup("race-leader@band.app", "leader");
        String member = signup("race-member@band.app", "member");
        long band = createBand(leader, "settings");
        long leaderId = myUserId(leader);
        long memberId = myUserId(member);
        assertThat(join(member, issueInvite(leader, band, null)).getStatusCode().value()).isEqualTo(200);

        assertThat(overlap(() -> bandService.updateSettings(band, leaderId,
                        new UpdateBandSettingsRequest(ReservationPermission.ANYONE)),
                () -> memberService.delegateLeadership(band, leaderId, memberId))).isNull();

        assertLeader(band, memberId, 2);
        assertThat(bands.findById(band).orElseThrow().getReservationPermission())
                .isEqualTo(ReservationPermission.ANYONE);
    }

    @Test
    void concurrent_issuance_leaves_only_the_last_code_usable() throws Exception {
        String leader = signup("race-leader@band.app", "leader");
        String joiner = signup("race-joiner@band.app", "joiner");
        long band = createBand(leader, "invites");
        long leaderId = myUserId(leader);
        var firstCode = new AtomicReference<String>();
        var secondCode = new AtomicReference<String>();

        assertThat(overlap(() -> firstCode.set(inviteService.issue(band, leaderId, null).code()),
                () -> secondCode.set(inviteService.issue(band, leaderId, null).code()))).isNull();

        assertThat(invites.findAll().stream().filter(i -> !i.isRevoked()).count()).isEqualTo(1);
        var rejected = join(joiner, firstCode.get());
        assertThat(rejected.getStatusCode().value()).isEqualTo(410);
        assertThat(errorCode(rejected)).isEqualTo("INVITE_REVOKED");
        assertThat(join(joiner, secondCode.get()).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void revocation_waits_for_an_uncommitted_issue() throws Exception {
        String leader = signup("race-leader@band.app", "leader");
        long band = createBand(leader, "revoke");
        long leaderId = myUserId(leader);

        assertThat(overlap(() -> inviteService.issue(band, leaderId, null),
                () -> inviteService.revokeCurrent(band, leaderId))).isNull();

        assertThat(invites.findFirstByBandIdAndRevokedFalseOrderByCreatedAtDesc(band)).isEmpty();
    }

    @Test
    void joining_waits_for_last_leader_withdrawal_then_rejects_the_old_code() throws Exception {
        String leader = signup("race-leader@band.app", "leader");
        String joiner = signup("race-joiner@band.app", "joiner");
        long band = createBand(leader, "closing");
        long leaderId = myUserId(leader);
        long joinerId = myUserId(joiner);
        String code = issueInvite(leader, band, null);

        assertThat(overlap(() -> accounts.withdraw(leaderId, "pw12345678"),
                () -> inviteService.join(joinerId, code, "race"))).isEqualTo(ErrorCode.INVITE_REVOKED);

        assertThat(members.countByBandIdAndLeftAtIsNull(band)).isZero();
        assertThat(invites.findByCode(code).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void joining_before_last_leader_withdrawal_produces_a_successor() throws Exception {
        String leader = signup("race-leader@band.app", "leader");
        String joiner = signup("race-joiner@band.app", "joiner");
        long band = createBand(leader, "succession");
        long leaderId = myUserId(leader);
        long joinerId = myUserId(joiner);
        String code = issueInvite(leader, band, null);

        assertThat(overlap(() -> inviteService.join(joinerId, code, "race"),
                () -> accounts.withdraw(leaderId, "pw12345678"))).isNull();

        assertLeader(band, joinerId, 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void withdrawn_user_cannot_finish_joining_or_creating_a_band(boolean create) throws Exception {
        String leader = signup("race-leader@band.app", "leader");
        String joiner = signup("race-joiner@band.app", "joiner");
        long band = createBand(leader, "user withdrawal first");
        long joinerId = myUserId(joiner);
        String code = issueInvite(leader, band, null);

        assertThat(overlap(() -> accounts.withdraw(joinerId, "pw12345678"), () -> {
            if (create) {
                bandService.create(joinerId, new CreateBandRequest("too late"));
            } else {
                inviteService.join(joinerId, code, "race");
            }
        })).isEqualTo(ErrorCode.USER_NOT_FOUND);

        assertThat(members.findActiveBandIdsForWithdrawal(joinerId)).isEmpty();
        assertThat(bands.count()).isEqualTo(1);
        assertThat(invites.findByCode(code).orElseThrow().getUsedCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void withdrawal_includes_a_join_or_creation_that_was_still_committing(boolean create) throws Exception {
        String leader = signup("race-leader@band.app", "leader");
        String joiner = signup("race-joiner@band.app", "joiner");
        long band = createBand(leader, "user withdrawal second");
        long joinerId = myUserId(joiner);
        String code = issueInvite(leader, band, null);
        var createdBand = new AtomicLong();

        assertThat(overlap(() -> {
            if (create) {
                createdBand.set(bandService.create(joinerId, new CreateBandRequest("new band")).id());
            } else {
                inviteService.join(joinerId, code, "race");
            }
        }, () -> accounts.withdraw(joinerId, "pw12345678"))).isNull();

        assertThat(users.findByIdAndDeletedAtIsNull(joinerId)).isEmpty();
        assertThat(members.findActiveBandIdsForWithdrawal(joinerId)).isEmpty();
        if (create) {
            assertThat(members.countByBandIdAndLeftAtIsNull(createdBand.get())).isZero();
        }
    }

    @Test
    void two_accounts_with_opposite_band_join_order_withdraw_without_deadlock() throws Exception {
        String first = signup("race-first@band.app", "first");
        String second = signup("race-second@band.app", "second");
        String survivor = signup("race-survivor@band.app", "survivor");
        long firstId = myUserId(first);
        long secondId = myUserId(second);
        long survivorId = myUserId(survivor);
        long a = createBand(first, "A");
        long b = createBand(second, "B");
        String codeA = issueInvite(first, a, null);
        String codeB = issueInvite(second, b, null);
        assertThat(join(second, codeA).getStatusCode().value()).isEqualTo(200);
        assertThat(join(first, codeB).getStatusCode().value()).isEqualTo(200);
        assertThat(join(survivor, codeA).getStatusCode().value()).isEqualTo(200);
        assertThat(join(survivor, codeB).getStatusCode().value()).isEqualTo(200);
        var ready = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = pool.submit(() -> withdrawTogether(firstId, ready));
            Future<?> two = pool.submit(() -> withdrawTogether(secondId, ready));
            one.get(15, TimeUnit.SECONDS);
            two.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }

        assertLeader(a, survivorId, 1);
        assertLeader(b, survivorId, 1);
        assertThat(users.findByIdAndDeletedAtIsNull(firstId)).isEmpty();
        assertThat(users.findByIdAndDeletedAtIsNull(secondId)).isEmpty();
    }

    private void withdrawTogether(long userId, CyclicBarrier ready) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            // 둘 다 자기 사용자 행을 잡은 상태에서 승계를 시작한다. FK 검증도 서로 막으면 안 된다.
            users.findActiveByIdForUpdate(userId).orElseThrow();
            try {
                ready.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            accounts.withdraw(userId, "pw12345678");
        });
    }

    private void depart(Departure departure, long band, long leaderId, long memberId) {
        switch (departure) {
            case LEAVE -> memberService.leave(band, memberId);
            case KICK -> memberService.kick(band, leaderId, memberId);
            case WITHDRAW -> accounts.withdraw(memberId, "pw12345678");
        }
    }

    private void assertLeader(long band, long userId, long memberCount) {
        assertThat(members.countByBandIdAndLeftAtIsNull(band)).isEqualTo(memberCount);
        assertThat(members.countByBandIdAndRoleAndLeftAtIsNull(band, BandMemberRole.LEADER)).isEqualTo(1);
        assertThat(members.findByBandIdAndUserIdAndLeftAtIsNull(band, userId).orElseThrow().isLeader()).isTrue();
        assertThat(bands.findById(band).orElseThrow().getLeaderId()).isEqualTo(userId);
    }

    private ErrorCode overlap(Runnable firstAction, Runnable secondAction) throws Exception {
        var firstReady = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var secondReady = new CountDownLatch(1);
        var secondPid = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        Future<?> first = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            firstAction.run();
            firstReady.countDown();
            await(commit);
        }));
        try {
            await(firstReady);
            Future<ErrorCode> second = pool.submit(() -> {
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status -> {
                        secondPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                        secondReady.countDown();
                        secondAction.run();
                    });
                    return null;
                } catch (BusinessException e) {
                    return e.errorCode();
                }
            });
            await(secondReady);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean blocked = false;
            while (!blocked && System.nanoTime() < deadline) {
                assertThat(second.isDone()).as("두 번째 요청은 첫 요청 커밋까지 기다려야 한다").isFalse();
                blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "select wait_event_type = 'Lock' from pg_stat_activity where pid = ?",
                        Boolean.class, secondPid.get()));
                if (!blocked) {
                    Thread.sleep(20);
                }
            }
            assertThat(blocked).as("실제로 DB 잠금을 기다리는지 확인").isTrue();
            commit.countDown();
            first.get(15, TimeUnit.SECONDS);
            return second.get(15, TimeUnit.SECONDS);
        } finally {
            commit.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
