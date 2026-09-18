package com.exam.backend.dto;

import com.exam.backend.domain.enums.NotificationTypeEnum;

import java.time.LocalDateTime;

public class NotificationDto {

    public record NotificationResponse(
            Long id, String title, String content, NotificationTypeEnum type,
            Boolean read, String relatedType, Long relatedId, LocalDateTime createdAt) {}

    public record UnreadCountResponse(Long count) {}
}
