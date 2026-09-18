package com.exam.backend.service;

import com.exam.backend.dto.LogDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LogService {

    private static final Set<String> ALLOWED_LEVELS = Set.of("DEBUG", "INFO", "WARNING", "ERROR");
    private static final Pattern SANITIZE = Pattern.compile("[\\r\\n]");

    public void receive(LogDto.LogRequest req, String sourceTag) {
        String level = sanitize(req.level(), 10).toUpperCase();
        if (!ALLOWED_LEVELS.contains(level)) level = "INFO";
        String msg = truncate(sanitize(req.message(), 2000), 2000);
        String source = truncate(sanitize(req.source(), 500), 500);
        String component = truncate(sanitize(req.component(), 500), 500);

        String prefix = "[" + sourceTag + "]";
        if (!component.isBlank()) prefix += " [" + component + "]";
        if (!source.isBlank()) prefix += " [" + source + "]";

        switch (level) {
            case "DEBUG" -> log.debug("{} {}", prefix, msg);
            case "WARNING" -> log.warn("{} {}", prefix, msg);
            case "ERROR" -> log.error("{} {}", prefix, msg);
            default -> log.info("{} {}", prefix, msg);
        }
    }

    private String sanitize(String s, int max) {
        if (s == null) return "";
        return SANITIZE.matcher(s).replaceAll(" ");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
