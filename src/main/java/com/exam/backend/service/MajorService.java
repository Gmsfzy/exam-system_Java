package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Course;
import com.exam.backend.domain.entity.Department;
import com.exam.backend.domain.entity.Major;
import com.exam.backend.dto.MajorDto;
import com.exam.backend.repository.CourseRepository;
import com.exam.backend.repository.DepartmentRepository;
import com.exam.backend.repository.MajorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MajorService {

    private final MajorRepository majorRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<MajorDto.DepartmentResponse> listDepartments() {
        return departmentRepository.findAll().stream()
                .map(d -> new MajorDto.DepartmentResponse(d.getId(), d.getName(),
                        d.getDescription(), d.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MajorDto.MajorResponse> listMajors(Long departmentId) {
        List<Major> majors = departmentId == null
                ? majorRepository.findAll()
                : majorRepository.findByDepartmentId(departmentId);
        return majors.stream().map(this::toMajorResponse).toList();
    }

    @Transactional(readOnly = true)
    public MajorDto.MajorResponse getMajor(Long id) {
        Major m = majorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "专业不存在"));
        return toMajorResponse(m);
    }

    @Transactional
    public MajorDto.MajorResponse createMajor(MajorDto.MajorRequest req) {
        Department dept = null;
        if (req.departmentId() != null) {
            dept = departmentRepository.findById(req.departmentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "院系不存在"));
        }
        Major major = Major.builder()
                .name(req.name())
                .description(req.description())
                .department(dept)
                .build();
        majorRepository.save(major);
        return toMajorResponse(major);
    }

    @Transactional
    public MajorDto.MajorResponse updateMajor(Long id, MajorDto.MajorRequest req) {
        Major major = majorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "专业不存在"));
        if (req.name() != null) major.setName(req.name());
        if (req.description() != null) major.setDescription(req.description());
        if (req.departmentId() != null) {
            Department dept = departmentRepository.findById(req.departmentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "院系不存在"));
            major.setDepartment(dept);
        }
        return toMajorResponse(major);
    }

    @Transactional
    public void deleteMajor(Long id) {
        if (!majorRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "专业不存在");
        }
        // 级联清理课程
        List<Course> courses = courseRepository.findByMajorId(id);
        courseRepository.deleteAll(courses);
        majorRepository.deleteById(id);
    }

    private MajorDto.MajorResponse toMajorResponse(Major m) {
        return new MajorDto.MajorResponse(m.getId(), m.getName(), m.getDescription(),
                m.getDepartment() == null ? null : m.getDepartment().getId(),
                m.getDepartment() == null ? null : m.getDepartment().getName(),
                m.getCreatedAt());
    }
}
