package com.yeka.bandapp.room.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.ratelimit.RateLimitProperties;
import com.yeka.bandapp.common.ratelimit.RedisRateLimiter;
import com.yeka.bandapp.room.dto.CreateRoomRequest;
import com.yeka.bandapp.room.dto.PlaceSearchResponse;
import com.yeka.bandapp.room.dto.RoomListResponse;
import com.yeka.bandapp.room.dto.RoomResponse;
import com.yeka.bandapp.room.dto.UpdateRoomRequest;
import com.yeka.bandapp.room.entity.Room;
import com.yeka.bandapp.room.naver.Coordinates;
import com.yeka.bandapp.room.naver.GeocodingClient;
import com.yeka.bandapp.room.naver.PlaceSearchClient;
import com.yeka.bandapp.room.repository.RoomRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 합주실 등록·조회·수정·삭제. 등록/수정/삭제 모두 <b>밴드 멤버면 누구나</b> 가능하다
 * (BUILD_PLAN Phase 3 — 밴드장 전용 작업이 아니다).
 *
 * <p>모든 메서드는 {@link BandAccessGuard#requireActiveMember}로 시작해 타 밴드 데이터 접근을 막는다.
 *
 * <p><b>지오코딩(외부 HTTP)은 어떤 트랜잭션 안에서도 호출하지 않는다</b>(CLAUDE.md 규칙). 그래서
 * {@code create}/{@code update}에는 {@code @Transactional}이 없다 — 멤버십 확인·이름 검사·저장은
 * 각각 저장소 호출 단위의 짧은 트랜잭션으로 처리되고, 이름 유니크 경합은 {@link #persist} /
 * {@code RoomRepository#updateEditableFields}에서 409로 변환한다.
 */
@Service
public class RoomService {

    private static final String GEOCODE_BUCKET = "geocode:user";
    private static final String PLACE_SEARCH_BUCKET = "placesearch:user";

    private final RoomRepository roomRepository;
    private final BandAccessGuard accessGuard;
    private final GeocodingClient geocodingClient;
    private final PlaceSearchClient placeSearchClient;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public RoomService(RoomRepository roomRepository, BandAccessGuard accessGuard,
                       GeocodingClient geocodingClient, PlaceSearchClient placeSearchClient,
                       RedisRateLimiter rateLimiter, RateLimitProperties rateLimitProperties) {
        this.roomRepository = roomRepository;
        this.accessGuard = accessGuard;
        this.geocodingClient = geocodingClient;
        this.placeSearchClient = placeSearchClient;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * 합주실 등록. 주소가 있으면 지오코딩을 시도하되, <b>실패해도 좌표 없이 저장한다</b>
     * (Phase 3 완료 기준). 지오코딩이 트랜잭션 밖이어야 하므로 {@code @Transactional} 없음 —
     * 실제 쓰기는 {@link #persist}의 단일 INSERT 하나뿐이라 원자성 손실이 없다.
     */
    public RoomResponse create(long bandId, long userId, CreateRoomRequest request) {
        accessGuard.requireActiveMember(bandId, userId);

        String name = request.name().trim();
        String address = trimToNull(request.address());
        requireNameAvailable(bandId, name);

        Room room = Room.create(bandId, userId, name, address,
                trimToNull(request.phone()), trimToNull(request.memo()));
        geocode(userId, address).ifPresent(room::applyCoordinates);

        return RoomResponse.from(persist(room));
    }

    @Transactional(readOnly = true)
    public RoomListResponse list(long bandId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        List<RoomResponse> rows = roomRepository
                .findByBandIdAndDeletedAtIsNullOrderByUsageCountDescIdAsc(bandId).stream()
                .map(RoomResponse::from)
                .toList();
        return new RoomListResponse(bandId, rows.size(), rows);
    }

    @Transactional(readOnly = true)
    public RoomResponse get(long bandId, long roomId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        return RoomResponse.from(room(bandId, roomId));
    }

    /**
     * 합주실 이름·주소 후보 검색(카카오 로컬 검색). 등록 폼에서 "검색해서 자동 입력"에 쓴다.
     *
     * <p>지오코딩과 같은 이유로 {@code @Transactional}이 없다 — 외부 HTTP 호출을 트랜잭션 안에서 하지
     * 않는다. 멤버십 확인은 그 자체로 짧은 조회 트랜잭션이고, 검색 호출은 그 밖에서 이뤄진다.
     * 검색어가 비었거나 검색 키가 없으면 빈 결과를 돌려준다(에러 아님). 외부 API 남용 방지를 위해
     * 지오코딩과 동일한 계정당 분당 상한을 건다.
     */
    public PlaceSearchResponse searchPlaces(long bandId, long userId, String query) {
        accessGuard.requireActiveMember(bandId, userId);
        String q = trimToNull(query);
        if (q == null) {
            return PlaceSearchResponse.of("", List.of());
        }
        rateLimiter.check(PLACE_SEARCH_BUCKET, String.valueOf(userId),
                rateLimitProperties.geocodePerUserPerMin());
        return PlaceSearchResponse.of(q, placeSearchClient.search(q));
    }

    /**
     * 수정. 주소가 실제로 바뀐 경우에만 지오코딩을 다시 호출한다(외부 API 호출 절약).
     *
     * <p>지오코딩을 트랜잭션 밖에서 끝낸 뒤, 확정된 좌표를 들고 {@code updateEditableFields} 부분 UPDATE
     * 한 번으로 쓴다. 엔티티 {@code merge}(전체 컬럼 재기록)를 피해 동시에 바뀔 수 있는 {@code usageCount} 등을 보존한다.
     */
    public RoomResponse update(long bandId, long roomId, long userId, UpdateRoomRequest request) {
        accessGuard.requireActiveMember(bandId, userId);
        Room snapshot = room(bandId, roomId);

        String name = request.name().trim();
        String address = trimToNull(request.address());
        String phone = trimToNull(request.phone());
        String memo = trimToNull(request.memo());

        Double lat = snapshot.getLat();
        Double lng = snapshot.getLng();
        if (!Objects.equals(address, snapshot.getAddress())) {
            // 주소가 바뀐 이상 옛 좌표는 더 이상 이 주소의 것이 아니다. 새로 얻지 못하면 비운다.
            Optional<Coordinates> found = geocode(userId, address);
            lat = found.map(Coordinates::lat).orElse(null);
            lng = found.map(Coordinates::lng).orElse(null);
        }
        if (!name.equals(snapshot.getName())) {
            requireNameAvailable(bandId, name);
        }

        try {
            if (roomRepository.updateEditableFields(roomId, name, address, lat, lng, phone, memo) == 0) {
                throw new BusinessException(ErrorCode.ROOM_NOT_FOUND); // 조회와 UPDATE 사이에 삭제됨
            }
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ROOM_NAME_DUPLICATED); // 이름 경합
        }
        return RoomResponse.from(room(bandId, roomId));
    }

    /** 소프트 삭제. 과거 일정이 참조할 수 있도록 행은 남긴다. */
    @Transactional
    public void delete(long bandId, long roomId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        room(bandId, roomId).delete(Instant.now());
    }

    /**
     * 합주실 조회 + 밴드 교차 접근 차단.
     *
     * <p>경로의 {@code bandId}만 믿지 않고 실제 소유 밴드를 대조한다 — 다른 밴드의 {@code roomId}를
     * 자기 밴드 경로에 끼워 넣는 시도를 막기 위해서다. 존재 여부를 알려주지 않도록 이 경우도
     * 똑같이 {@code ROOM_NOT_FOUND}로 응답한다.
     */
    private Room room(long bandId, long roomId) {
        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (!room.belongsTo(bandId)) {
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        }
        return room;
    }

    private void requireNameAvailable(long bandId, String name) {
        if (roomRepository.existsByBandIdAndNameAndDeletedAtIsNull(bandId, name)) {
            throw new BusinessException(ErrorCode.ROOM_NAME_DUPLICATED);
        }
    }

    /**
     * 저장 + 이름 유니크 경합 방어. {@link #requireNameAvailable} 선검사와 이 저장 사이에 다른 요청이
     * 같은 이름을 넣으면 {@code ux_rooms_band_name_active} 위반이 나는데, 그걸 500이 아니라 409로 바꾼다
     * (signup·초대코드 생성과 같은 패턴). {@code saveAndFlush}로 위반을 이 메서드 안에서 잡는다.
     */
    private Room persist(Room room) {
        try {
            return roomRepository.saveAndFlush(room);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ROOM_NAME_DUPLICATED);
        }
    }

    /**
     * 지오코딩 시도. 주소가 없으면 호출하지 않는다.
     *
     * <p>외부 API의 무료 한도를 한 계정이 태우지 못하도록 호출 직전 계정 단위 분당 제한을 건다.
     * 제한을 넘으면 429가 나가고 등록 자체가 거부된다 — 좌표 없이 조용히 저장하면 사용자가
     * "왜 지도에 안 뜨지"를 알 수 없기 때문에, 남용 상황만큼은 명시적으로 알린다.
     */
    private Optional<Coordinates> geocode(long userId, String address) {
        if (address == null) {
            return Optional.empty();
        }
        rateLimiter.check(GEOCODE_BUCKET, String.valueOf(userId),
                rateLimitProperties.geocodePerUserPerMin());
        return geocodingClient.geocode(address);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
