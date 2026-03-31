package com.example.demo.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void testConstructorWithoutPagination() {
        ApiResponse<String> response = new ApiResponse<>("success", "message", "data", 200);
        assertEquals("success", response.getStatus());
        assertEquals("message", response.getMessage());
        assertEquals("data", response.getData());
        assertNull(response.getPagination());
        assertEquals(200, response.getCode());
    }

    @Test
    void testConstructorWithPagination() {
        PageInfo pageInfo = new PageInfo(1, 5, 20, true, false);
        ApiResponse<String> response = new ApiResponse<>("success", "msg", "data", pageInfo, 200);
        assertEquals("success", response.getStatus());
        assertEquals("msg", response.getMessage());
        assertEquals("data", response.getData());
        assertEquals(pageInfo, response.getPagination());
        assertEquals(200, response.getCode());
    }
}
