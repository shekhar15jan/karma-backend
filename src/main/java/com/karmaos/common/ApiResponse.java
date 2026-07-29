package com.karmaos.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private ErrorDetail error;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Created")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, int code) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorDetail.builder().code(code).message(message).build())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, int code, T details) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorDetail.builder().code(code).message(message).details(details).build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private int code;
        private String message;
        private Object details;
    }
}
