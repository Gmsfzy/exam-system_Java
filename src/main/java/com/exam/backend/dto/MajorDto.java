package com.exam.backend.dto;

import java.time.LocalDateTime;

public class MajorDto {

    public record DepartmentResponse(Long id, String name, String description, LocalDateTime createdAt) {}

    public record MajorRequest(String name, String description, Long departmentId) {}

    public record MajorResponse(Long id, String name, String description,
                                  Long departmentId, String departmentName,
                                  LocalDateTime createdAt) {}

    public record CourseRequest(String name, String description,
                                  Integer credit, Integer semester, Long majorId) {}

    public record CourseResponse(Long id, String name, String description,
                                   Integer credit, Integer semester,
                                   Long majorId, String majorName,
                                   LocalDateTime createdAt) {}

    public record ChapterRequest(String name, String description,
                                   Integer orderNum, Long courseId) {}

    public record ChapterResponse(Long id, String name, String description,
                                    Integer orderNum,
                                    Long courseId, String courseName,
                                    LocalDateTime createdAt) {}
}
