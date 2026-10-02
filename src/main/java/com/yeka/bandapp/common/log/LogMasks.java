package com.yeka.bandapp.common.log;

/**
 * 로그·예외 메시지에 비밀값이 그대로 남지 않게 가리는 도우미 (PRIV-06).
 */
public final class LogMasks {

    private LogMasks() {
    }

    /** 토큰은 앞 6자만 남긴다 — 같은 건인지 맞춰 볼 수는 있고 재사용은 못 한다. */
    public static String token(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 6 ? "…" : value.substring(0, 6) + "…";
    }

    /**
     * 메시지 속 URL 을 {@code <url>} 로 바꾼다. Google API 클라이언트 예외 메시지는
     * {@code GET https://…/tokens/<구매 토큰>} 처럼 요청 URL(경로에 토큰)을 담는다.
     */
    public static String stripUrls(String message) {
        return message == null ? null : message.replaceAll("https?://\\S+", "<url>");
    }

    /**
     * 외부 클라이언트 예외를 cause 로 넘길 때 쓴다 — 메시지에서 URL 을 지운 사본(스택은 유지, 원래 cause 는 버림).
     * 원래 예외를 cause 로 붙이면 로그의 "Caused by:" 줄에 URL 이 그대로 찍힌다.
     */
    public static RuntimeException redacted(Throwable e) {
        RuntimeException copy = new RuntimeException(e.getClass().getName() + ": " + stripUrls(e.getMessage()));
        copy.setStackTrace(e.getStackTrace());
        return copy;
    }
}
