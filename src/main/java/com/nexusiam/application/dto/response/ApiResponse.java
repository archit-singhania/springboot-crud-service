package com.nexusiam.application.dto.response;

import com.nexusiam.application.dto.PageInfo;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String status;
    private String message;
    private T data;
    private PageInfo pagination;
    private int code;

    public ApiResponse(String status, String message, T data, int code) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.code = code;
        this.pagination = null;
    }

    public ApiResponse(String status, String message, T data, PageInfo pagination, int code) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.pagination = pagination;
        this.code = code;
    }
}
