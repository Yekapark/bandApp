package com.yeka.bandapp.plan.service;

import java.util.OptionalLong;

/**
 * 스토어 구매에 붙이는 "어느 밴드를 위해 결제했나" 표시. 앱이 구매할 때 Play 의
 * {@code obfuscatedAccountId}(in_app_purchase 의 {@code applicationUserName})로 넣고, 서버는
 * {@code subscriptionsv2} 응답의 {@code externalAccountIdentifiers.obfuscatedExternalAccountId} 로 읽는다.
 *
 * <p><b>왜 필요한가</b> — 결제 직후 검증 전에 앱이 꺼지면, 다음에 앱을 켰을 때 미완료 구매를 처리한다.
 * 그때 "지금 선택된 밴드" 로 검증하면 밴드장이 밴드를 두 개 가진 경우 엉뚱한 밴드가 PREMIUM 이 된다
 * (LAUNCH_REVIEW B2·B3). 구매 기록 자체에 밴드를 적어 두면 서버가 그 값으로 정한다.
 *
 * <p>형식은 {@code band-{bandId}}. <b>클라이언트 {@code IapService.bandTag} 와 같아야 한다.</b>
 * 개인정보(이메일·이름)는 넣지 않는다 — Play 정책상 이 값에 평문 개인정보를 넣으면 안 된다.
 */
public final class PurchaseBandTag {

    private static final String PREFIX = "band-";

    private PurchaseBandTag() {
    }

    public static String of(long bandId) {
        return PREFIX + bandId;
    }

    /** 표시에서 밴드 id 를 읽는다. 없거나 형식이 다르면 empty. */
    public static OptionalLong parse(String tag) {
        if (tag == null || !tag.startsWith(PREFIX)) {
            return OptionalLong.empty();
        }
        try {
            long bandId = Long.parseLong(tag.substring(PREFIX.length()));
            return bandId > 0 ? OptionalLong.of(bandId) : OptionalLong.empty();
        } catch (NumberFormatException notANumber) {
            return OptionalLong.empty();
        }
    }
}
