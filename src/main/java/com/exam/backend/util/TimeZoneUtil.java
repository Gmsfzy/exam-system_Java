package com.exam.backend.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeZoneUtil {

    public static final ZoneId UTC = ZoneId.of("UTC");
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeZoneUtil() {}

    /** 数据库存储的 UTC LocalDateTime → 上海时间字符串 */
    public static String toDisplay(LocalDateTime utc) {
        if (utc == null) return null;
        return utc.atZone(UTC).withZoneSameInstant(SHANGHAI).format(DISPLAY);
    }

    /** 用户输入（上海时间）→ 数据库 UTC LocalDateTime */
    public static LocalDateTime fromInput(LocalDateTime local) {
        if (local == null) return null;
        return local.atZone(SHANGHAI).withZoneSameInstant(UTC).toLocalDateTime();
    }

    /** 当前 UTC 时间 */
    public static LocalDateTime utcNow() {
        return LocalDateTime.now(UTC);
    }
}
