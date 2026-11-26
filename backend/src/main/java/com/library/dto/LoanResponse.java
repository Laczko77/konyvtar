package com.library.dto;

import com.library.entity.Loan;

import java.time.LocalDateTime;

public record LoanResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String bookIsbn,
        Long userId,
        String userEmail,
        String userFullName,
        LocalDateTime borrowedAt,
        LocalDateTime dueDate,
        LocalDateTime returnedAt,
        boolean overdue
) {
    public static LoanResponse from(Loan loan) {
        return from(loan, LocalDateTime.now());
    }

    public static LoanResponse from(Loan loan, LocalDateTime referenceTime) {
        boolean isOverdue = loan.getReturnedAt() == null && referenceTime.isAfter(loan.getDueDate());
        return new LoanResponse(
                loan.getId(),
                loan.getBook().getId(),
                loan.getBook().getTitle(),
                loan.getBook().getIsbn(),
                loan.getUser().getId(),
                loan.getUser().getEmail(),
                loan.getUser().getFullName(),
                loan.getBorrowedAt(),
                loan.getDueDate(),
                loan.getReturnedAt(),
                isOverdue
        );
    }
}
