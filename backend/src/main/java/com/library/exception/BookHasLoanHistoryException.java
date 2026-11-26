package com.library.exception;

public class BookHasLoanHistoryException extends RuntimeException {
    public BookHasLoanHistoryException(String message) {
        super(message);
    }
}
