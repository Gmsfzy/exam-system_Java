package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Notification;
import com.exam.backend.domain.enums.NotificationTypeEnum;
import com.exam.backend.dto.NotificationDto;
import com.exam.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationDto.NotificationResponse> list(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotificationDto.UnreadCountResponse unreadCount(Long userId) {
        return new NotificationDto.UnreadCountResponse(notificationRepository.countByUserIdAndReadFalse(userId));
    }

    @Transactional
    public void markRead(Long id, Long currentUserId) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"));
        if (!n.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作他人通知");
        }
        n.setRead(true);
    }

    @Transactional
    public int markAllRead(Long currentUserId) {
        return notificationRepository.markAllRead(currentUserId);
    }

    @Transactional
    public void delete(Long id, Long currentUserId) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"));
        if (!n.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作他人通知");
        }
        notificationRepository.deleteById(id);
    }

    @Transactional
    public int clearAll(Long currentUserId) {
        return notificationRepository.deleteAllByUserId(currentUserId);
    }

    @Transactional
    public void notifyStudentInvited(Long studentId, Long examId, String examTitle) {
        create(studentId, "考试邀请", "您已被邀请参加考试: " + examTitle,
                NotificationTypeEnum.info, "exam", examId);
    }

    @Transactional
    public void notifyExamPublished(Long examId, String examTitle, List<Long> studentIds) {
        for (Long sid : studentIds) {
            create(sid, "考试发布", "考试已发布: " + examTitle,
                    NotificationTypeEnum.success, "exam", examId);
        }
    }

    @Transactional
    public void notifyResultPublished(Long studentId, Long examId, String examTitle, Double score) {
        create(studentId, "成绩公布", "您的考试 " + examTitle + " 成绩: " + score,
                NotificationTypeEnum.info, "result", examId);
    }

    @Transactional
    public Notification create(Long userId, String title, String content,
                                NotificationTypeEnum type, String relatedType, Long relatedId) {
        Notification n = Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .type(type == null ? NotificationTypeEnum.info : type)
                .read(false)
                .relatedType(relatedType)
                .relatedId(relatedId)
                .build();
        return notificationRepository.save(n);
    }

    private NotificationDto.NotificationResponse toResponse(Notification n) {
        return new NotificationDto.NotificationResponse(n.getId(), n.getTitle(), n.getContent(),
                n.getType(), n.getRead(), n.getRelatedType(), n.getRelatedId(),
                n.getCreatedAt());
    }
}
