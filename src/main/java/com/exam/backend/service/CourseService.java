package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Course;
import com.exam.backend.domain.entity.Major;
import com.exam.backend.dto.MajorDto;
import com.exam.backend.repository.CourseRepository;
import com.exam.backend.repository.MajorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final MajorRepository majorRepository;

    @Transactional(readOnly = true)
    public List<MajorDto.CourseResponse> listCourses(Long majorId) {
        List<Course> courses = majorId == null
                ? courseRepository.findAll()
                : courseRepository.findByMajorId(majorId);
        return courses.stream().map(this::toResponse).toList();
    }

    @Transactional
    public MajorDto.CourseResponse createCourse(MajorDto.CourseRequest req) {
        Major major = null;
        if (req.majorId() != null) {
            major = majorRepository.findById(req.majorId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "专业不存在"));
        }
        Course c = Course.builder()
                .name(req.name())
                .description(req.description())
                .credit(req.credit())
                .semester(req.semester())
                .major(major)
                .build();
        courseRepository.save(c);
        return toResponse(c);
    }

    @Transactional
    public MajorDto.CourseResponse updateCourse(Long id, MajorDto.CourseRequest req) {
        Course c = courseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "课程不存在"));
        if (req.name() != null) c.setName(req.name());
        if (req.description() != null) c.setDescription(req.description());
        if (req.credit() != null) c.setCredit(req.credit());
        if (req.semester() != null) c.setSemester(req.semester());
        if (req.majorId() != null) {
            Major m = majorRepository.findById(req.majorId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "专业不存在"));
            c.setMajor(m);
        }
        return toResponse(c);
    }

    @Transactional
    public void deleteCourse(Long id) {
        if (!courseRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        courseRepository.deleteById(id);
    }

    private MajorDto.CourseResponse toResponse(Course c) {
        return new MajorDto.CourseResponse(c.getId(), c.getName(), c.getDescription(),
                c.getCredit(), c.getSemester(),
                c.getMajor() == null ? null : c.getMajor().getId(),
                c.getMajor() == null ? null : c.getMajor().getName(),
                c.getCreatedAt());
    }
}
