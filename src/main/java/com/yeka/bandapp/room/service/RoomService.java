package com.yeka.bandapp.room.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.ratelimit.RateLimitProperties;
import com.yeka.bandapp.common.ratelimit.RedisRateLimiter;
import com.yeka.bandapp.room.dto.CreateRoomRequest;
import com.yeka.bandapp.room.dto.RoomListResponse;
import com.yeka.bandapp.room.dto.RoomResponse;
import com.yeka.bandapp.room.dto.UpdateRoomRequest;
import com.yeka.bandapp.room.entity.Room;
import com.yeka.bandapp.room.naver.Coordinates;
import com.yeka.bandapp.room.naver.GeocodingClient;
import com.yeka.bandapp.room.repository.RoomRepository;
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
 */
@Service
public class RoomService {

    private static final String GEOCODE_BUCKET = "geocode:user";

    private final RoomRepository roomRepository;
    private final BandAccessGuard accessGuard;
    private final GeocodingClient geocodingClient;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public RoomService(RoomRepository roomRepository, BandAccessGuard accessGuard,
                       GeocodingClient geocodingClient, RedisRateLimiter rateLimiter,
                       RateLimitProperties rateLimitProperties) {
        this.roomRepository = roomRepository;
        this.accessGuard = accessGuard;
        this.geocodingClient = geocodingClient;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * 합주실 등록. 주소가 있으면 지오코딩을 시도하되, <b>실패해도 좌표 없이 저장한다</b>
     * (Phase 3 완료 기준).
     */
    @Transactional
    public RoomResponse create(long bandId, long userId, CreateRoomRequest request) {
        accessGuard.requireActiveMember(bandId, userId);

        String name = request.name().trim();
        String address = trimToNull(request.address());
        requireNameAvailable(bandId, name);

        Room room = Room.create(bandId, userId, name, address,
                trimToNull(request.phone()), trimToNull(request.memo()));
        geocode(userId, address).ifPresent(room::applyCoordinates);

        return RoomResponse.from(roomRepository.save(room));
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

    /** 수정. 주소가 실제로 바뀐 경우에만 지오코딩을 다시 호출한다(외부 API 호출 절약). */
    @Transactional
    public RoomResponse update(long bandId, long roomId, long userId, UpdateRoomRequest request) {
        accessGuard.requireActiveMember(bandId, userId);
        Room room = room(bandId, roomId);

        String name = request.name().trim();
        String address = trimToNull(request.address());
        if (!name.equals(room.getName())) {
            requireNameAvailable(bandId, name);
        }

        boolean addressChanged = !Objects.equals(address, room.getAddress());
        room.update(name, address, trimToNull(request.phone()), trimToNull(request.memo()));

        if (addressChanged) {
            // 주소가 바뀐 이상 옛 좌표는 더 이상 이 주소의 것이 아니다. 새로 얻지 못하면 비운다.
            Optional<Coordinates> found = geocode(userId, address);
            if (found.isPresent()) {
                room.applyCoordinates(found.get());
            } else {
                room.clearCoordinates();
            }
        }
        return RoomResponse.from(room);
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
