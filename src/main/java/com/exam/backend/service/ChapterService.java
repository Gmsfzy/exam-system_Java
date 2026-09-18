package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Chapter;
import com.exam.backend.domain.entity.Course;
import com.exam.backend.dto.MajorDto;
import com.exam.backend.repository.ChapterRepository;
import com.exam.backend.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<MajorDto.ChapterResponse> listChapters(Long courseId) {
        if (courseId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "course_id 必填");
        }
        return chapterRepository.findByCourseIdOrderByOrderNumAsc(courseId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MajorDto.ChapterResponse getChapter(Long id) {
        return toResponse(requireChapter(id));
    }

    @Transactional
    public MajorDto.ChapterResponse createChapter(MajorDto.ChapterRequest req) {
        Course course = null;
        if (req.courseId() != null) {
            course = courseRepository.findById(req.courseId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "课程不存在"));
        }
        Chapter c = Chapter.builder()
                .name(req.name())
                .description(req.description())
                .orderNum(req.orderNum())
                .course(course)
                .build();
        chapterRepository.save(c);
        return toResponse(c);
    }

    @Transactional
    public MajorDto.ChapterResponse updateChapter(Long id, MajorDto.ChapterRequest req) {
        Chapter c = requireChapter(id);
        if (req.name() != null) c.setName(req.name());
        if (req.description() != null) c.setDescription(req.description());
        if (req.orderNum() != null) c.setOrderNum(req.orderNum());
        if (req.courseId() != null) {
            Course course = courseRepository.findById(req.courseId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "课程不存在"));
            c.setCourse(course);
        }
        return toResponse(c);
    }

    @Transactional
    public void deleteChapter(Long id) {
        if (!chapterRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "章节不存在");
        }
        chapterRepository.deleteById(id);
    }

    private Chapter requireChapter(Long id) {
        return chapterRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "章节不存在"));
    }

    private MajorDto.ChapterResponse toResponse(Chapter c) {
        return new MajorDto.ChapterResponse(c.getId(), c.getName(), c.getDescription(),
                c.getOrderNum(),
                c.getCourse() == null ? null : c.getCourse().getId(),
                c.getCourse() == null ? null : c.getCourse().getName(),
                c.getCreatedAt());
    }
}
