package com.eventhub.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated user list response for admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageUserResponse {
    private List<UserResponse> content;
    private PageInfo page;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        private int size;
        private int number;
        private long totalElements;
        private int totalPages;
    }
}