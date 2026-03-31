package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Data;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PageInfo {
    private int currentPage;
    private int pageSize;
    private long total;
    private boolean hasNext;
    private boolean hasPrevious;
}