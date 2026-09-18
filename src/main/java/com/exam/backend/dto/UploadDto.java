package com.exam.backend.dto;

public class UploadDto {
    public record UploadResponse(String url, String filename, Long size) {}
}
