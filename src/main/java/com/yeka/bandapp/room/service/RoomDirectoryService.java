package com.yeka.bandapp.room.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.room.entity.Room;
import com.yeka.bandapp.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 다른 도메인(일정 등)이 합주실을 참조할 때 쓰는 창구. 도메인 간 참조는 저장소가 아니라 이 서비스를 통한다
 * (코딩 컨벤션). {@link com.yeka.bandapp.user.service.UserDirectoryService}와 같은 역할이다.
 */
@Service
public class RoomDirectoryService {

    private final RoomRepository roomRepository;

    public RoomDirectoryService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * 활성 합주실을 확인하고 표시용 요약을 돌려준다. 다른 밴드의 {@code roomId}이거나 이미 삭제됐으면
     * 존재를 알리지 않고 {@code ROOM_NOT_FOUND}로 응답한다({@link RoomService#room}과 같은 방침).
     */
    @Transactional(readOnly = true)
    public RoomBrief requireActiveRoom(long bandId, long roomId) {
        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (!room.belongsTo(bandId)) {
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        }
        return RoomBrief.from(room);
    }

    /** 일정 등록 / 합주실 변경 시 새 방의 사용 횟수 +1. */
    public void increaseUsage(long roomId) {
        roomRepository.increaseUsageCount(roomId);
    }

    /** 일정 취소·거절 / 합주실 변경 시 이전 방의 사용 횟수 -1. */
    public void decreaseUsage(long roomId) {
        roomRepository.decreaseUsageCount(roomId);
    }

    /** 정기 일정 회차 N건을 한 번에 등록할 때 사용 횟수 +N. {@code delta <= 0}이면 아무 일도 하지 않는다. */
    public void increaseUsageBy(long roomId, int delta) {
        if (delta > 0) {
            roomRepository.increaseUsageCountBy(roomId, delta);
        }
    }

    /** 규칙 삭제로 한 방의 미래 회차 N건이 취소될 때 사용 횟수 -N. {@code delta <= 0}이면 아무 일도 하지 않는다. */
    public void decreaseUsageBy(long roomId, int delta) {
        if (delta > 0) {
            roomRepository.decreaseUsageCountBy(roomId, delta);
        }
    }

    /**
     * 주어진 id 들의 이름 맵. 소프트 삭제된 합주실도 포함한다 — 삭제된 방을 참조하는 과거 일정도
     * 이름은 그대로 보여야 하기 때문이다.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> namesOf(Collection<Long> roomIds) {
        return roomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, Room::getName, (a, b) -> a));
    }

    public record RoomBrief(long id, String name) {
        static RoomBrief from(Room room) {
            return new RoomBrief(room.getId(), room.getName());
        }
    }
}
