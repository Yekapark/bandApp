package com.yeka.bandapp.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.room.entity.Room;
import com.yeka.bandapp.room.repository.RoomRepository;
import com.yeka.bandapp.support.FakeGeocodingClient;
import com.yeka.bandapp.support.GeocodingTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 완료 기준:
 * <ul>
 *   <li>지오코딩 실패 시에도 주소만으로 등록이 가능하다(좌표 null 허용).</li>
 *   <li>다른 밴드의 합주실이 조회되지 않는다.</li>
 * </ul>
 * 목록 정렬·수정 시 재지오코딩·소프트 삭제·이름 중복도 함께 검증한다.
 */
@Import(GeocodingTestConfig.class)
class RoomIntegrationTest extends RoomApiSupport {

    @Autowired
    FakeGeocodingClient geocoding;

    @Autowired
    RoomRepository roomRepository;

    @BeforeEach
    void resetGeocoding() {
        geocoding.reset();
    }

    @Test
    void geocoding_success_fills_coordinates() {
        String leader = signup("room-geo1@band.app", "리더");
        long bandId = createBand(leader, "혁오");
        geocoding.willReturn(37.5559, 126.9236);

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/rooms",
                roomBody("사운드박스 합주실", "서울시 마포구 와우산로 123"), leader);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        JsonNode room = data(res);
        assertThat(room.get("lat").asDouble()).isEqualTo(37.5559);
        assertThat(room.get("lng").asDouble()).isEqualTo(126.9236);
    }

    /** 완료 기준 ① — 지오코딩이 실패해도 등록은 성공하고 좌표만 비어 있다. */
    @Test
    void geocoding_failure_still_creates_room_with_null_coordinates() {
        String leader = signup("room-geo2@band.app", "리더");
        long bandId = createBand(leader, "국카스텐");
        geocoding.willReturnNothing();

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/rooms",
                roomBody("베이스먼트9", "존재하지 않는 주소 텍스트"), leader);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        JsonNode room = data(res);
        assertThat(room.get("lat").isNull()).isTrue();
        assertThat(room.get("lng").isNull()).isTrue();
        assertThat(room.get("address").asText()).isEqualTo("존재하지 않는 주소 텍스트");
    }

    @Test
    void room_without_address_does_not_call_geocoding() {
        String leader = signup("room-geo3@band.app", "리더");
        long bandId = createBand(leader, "새소년");

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/rooms",
                "{\"name\":\"우리집 지하실\"}", leader);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(data(res).get("lat").isNull()).isTrue();
        assertThat(geocoding.callCount()).isZero();
    }

    /** 완료 기준 ② — 다른 밴드 멤버는 목록을 볼 수 없다. */
    @Test
    void non_member_cannot_list_rooms() {
        String leader = signup("room-acl1@band.app", "리더");
        String stranger = signup("room-acl1-x@band.app", "낯선이");
        long bandId = createBand(leader, "잔나비");
        createRoom(leader, bandId, roomBody("연습실 A", "서울시 어딘가"));

        ResponseEntity<String> res = get("/api/v1/bands/" + bandId + "/rooms", stranger);

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_BAND_MEMBER");
    }

    /** 완료 기준 ② — 다른 밴드의 roomId 를 자기 밴드 경로에 끼워 넣어도 볼 수 없다. */
    @Test
    void room_of_another_band_is_not_reachable_through_my_band_path() {
        String alice = signup("room-acl2-a@band.app", "앨리스");
        String bob = signup("room-acl2-b@band.app", "밥");
        long aliceBand = createBand(alice, "앨리스밴드");
        long bobBand = createBand(bob, "밥밴드");
        long aliceRoom = createRoom(alice, aliceBand, roomBody("앨리스 연습실", "서울"));

        // 밥이 자기 밴드 경로로 앨리스의 roomId 를 조회 → 존재를 알리지 않고 404
        ResponseEntity<String> res = get("/api/v1/bands/" + bobBand + "/rooms/" + aliceRoom, bob);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(res)).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void list_is_ordered_by_usage_count_desc() {
        String leader = signup("room-order@band.app", "리더");
        long bandId = createBand(leader, "쏜애플");
        long low = createRoom(leader, bandId, "{\"name\":\"덜 쓰는 방\"}");
        long high = createRoom(leader, bandId, "{\"name\":\"자주 쓰는 방\"}");
        long mid = createRoom(leader, bandId, "{\"name\":\"가끔 쓰는 방\"}");

        bumpUsage(high, 5);
        bumpUsage(mid, 2);

        JsonNode list = data(get("/api/v1/bands/" + bandId + "/rooms", leader));
        assertThat(list.get("roomCount").asInt()).isEqualTo(3);
        assertThat(list.get("rooms").get(0).get("id").asLong()).isEqualTo(high);
        assertThat(list.get("rooms").get(1).get("id").asLong()).isEqualTo(mid);
        assertThat(list.get("rooms").get(2).get("id").asLong()).isEqualTo(low);
    }

    @Test
    void update_re_geocodes_only_when_address_changes() {
        String leader = signup("room-upd@band.app", "리더");
        long bandId = createBand(leader, "아도이");
        geocoding.willReturn(37.1, 127.1);
        long roomId = createRoom(leader, bandId, roomBody("노이즈룸", "옛 주소"));
        assertThat(geocoding.callCount()).isEqualTo(1);

        // 주소 그대로, 이름만 변경 → 재지오코딩 없음
        put("/api/v1/bands/" + bandId + "/rooms/" + roomId,
                roomBody("노이즈룸(리뉴얼)", "옛 주소"), leader);
        assertThat(geocoding.callCount()).isEqualTo(1);

        // 주소 변경 → 재지오코딩, 좌표 갱신
        geocoding.willReturn(35.5, 129.3);
        ResponseEntity<String> res = put("/api/v1/bands/" + bandId + "/rooms/" + roomId,
                roomBody("노이즈룸(리뉴얼)", "새 주소"), leader);
        assertThat(geocoding.callCount()).isEqualTo(2);
        assertThat(data(res).get("lat").asDouble()).isEqualTo(35.5);
    }

    @Test
    void update_to_unresolvable_address_clears_old_coordinates() {
        String leader = signup("room-upd2@band.app", "리더");
        long bandId = createBand(leader, "혁오투");
        geocoding.willReturn(37.1, 127.1);
        long roomId = createRoom(leader, bandId, roomBody("좌표있는방", "좋은 주소"));

        geocoding.willReturnNothing();
        ResponseEntity<String> res = put("/api/v1/bands/" + bandId + "/rooms/" + roomId,
                roomBody("좌표있는방", "안 풀리는 주소"), leader);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("lat").isNull()).isTrue();
        assertThat(data(res).get("lng").isNull()).isTrue();
    }

    @Test
    void deleted_room_disappears_and_its_name_can_be_reused() {
        String leader = signup("room-del@band.app", "리더");
        long bandId = createBand(leader, "실리카겔");
        long roomId = createRoom(leader, bandId, "{\"name\":\"루프탑 스튜디오\"}");

        assertThat(delete("/api/v1/bands/" + bandId + "/rooms/" + roomId, leader)
                .getStatusCode().value()).isEqualTo(204);

        assertThat(data(get("/api/v1/bands/" + bandId + "/rooms", leader)).get("roomCount").asInt())
                .isZero();
        assertThat(get("/api/v1/bands/" + bandId + "/rooms/" + roomId, leader)
                .getStatusCode().value()).isEqualTo(404);

        // 같은 이름으로 다시 등록 가능
        ResponseEntity<String> again = post("/api/v1/bands/" + bandId + "/rooms",
                "{\"name\":\"루프탑 스튜디오\"}", leader);
        assertThat(again.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void duplicate_name_in_same_band_is_409() {
        String leader = signup("room-dup@band.app", "리더");
        long bandId = createBand(leader, "데이먼스이어");
        createRoom(leader, bandId, "{\"name\":\"1번방\"}");

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/rooms",
                "{\"name\":\"1번방\"}", leader);

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("ROOM_NAME_DUPLICATED");
    }

    @Test
    void invited_member_can_register_a_room() {
        String leader = signup("room-mem-l@band.app", "리더");
        String member = signup("room-mem-m@band.app", "멤버");
        long bandId = createBand(leader, "브로콜리");
        join(member, issueInvite(leader, bandId, null));

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/rooms",
                "{\"name\":\"멤버가 등록한 방\"}", member);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
    }

    private void bumpUsage(long roomId, int times) {
        Room room = roomRepository.findById(roomId).orElseThrow();
        for (int i = 0; i < times; i++) {
            room.increaseUsage();
        }
        roomRepository.saveAndFlush(room);
    }
}
