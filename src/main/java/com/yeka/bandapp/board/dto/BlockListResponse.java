package com.yeka.bandapp.board.dto;

import java.util.List;

/** 내가 차단한 사용자 목록(최근순). */
public record BlockListResponse(int count, List<BlockResponse> blocks) {
}
