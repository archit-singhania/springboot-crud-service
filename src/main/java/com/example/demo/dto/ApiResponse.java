package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
//@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
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
