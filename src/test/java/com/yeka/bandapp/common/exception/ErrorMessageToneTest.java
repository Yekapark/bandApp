package com.yeka.bandapp.common.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오류 문구는 앱이 그대로 화면에 띄운다. 앱 문구는 "~어요" · "~해 주세요" 로 통일했는데(client/test/copy_tone_test.dart),
 * 서버 문구만 "~습니다" 로 남아 한 화면에서 말투가 섞였다(2026-10-13, LAUNCH_REVIEW U65). 새 코드가 다시 섞이지 않게 막는다.
 */
class ErrorMessageToneTest {

    /** 격식체 "~니다", 명령형 "~하세요"("주세요" 는 부탁 말투라 허용). */
    private static final Pattern FORMAL = Pattern.compile("[가-힣]니다(?=[.,!?\\s)]|$)");
    private static final Pattern IMPERATIVE = Pattern.compile("(?<!주)세요(?=[.,!?\\s)]|$)");

    /** 사용자가 할 수 있는 게 없는 개발 용어. */
    private static final List<String> JARGON = List.of("API", "토큰", "커서", "파라미터", "디바이스");

    @Test
    void error_messages_use_the_app_tone() {
        List<String> offenders = Arrays.stream(ErrorCode.values())
                .filter(c -> FORMAL.matcher(c.defaultMessage()).find()
                        || IMPERATIVE.matcher(c.defaultMessage()).find())
                .map(c -> c.name() + ": " + c.defaultMessage())
                .toList();

        assertThat(offenders).as("앱 말투(~어요·~해 주세요)와 다른 오류 문구").isEmpty();
    }

    @Test
    void error_messages_have_no_developer_jargon() {
        List<String> offenders = Arrays.stream(ErrorCode.values())
                .filter(c -> JARGON.stream().anyMatch(c.defaultMessage()::contains))
                .map(c -> c.name() + ": " + c.defaultMessage())
                .toList();

        assertThat(offenders).as("사용자에게 보일 오류 문구에 개발 용어").isEmpty();
    }
}
