package com.library.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @NotBlank(message = "Author is required")
        @Size(max = 255, message = "Author must be at most 255 characters")
        String author,

        @NotBlank(message = "ISBN is required")
        @Size(max = 20, message = "ISBN must be at most 20 characters")
        String isbn,

        @Min(value = 1000, message = "Published year must be at least 1000")
        @Max(value = 2100, message = "Published year must be at most 2100")
        Integer publishedYear
) {
}
