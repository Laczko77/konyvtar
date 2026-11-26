package com.library.service;

import com.library.dto.LoanResponse;
import com.library.entity.Book;
import com.library.entity.Loan;
import com.library.entity.Role;
import com.library.entity.User;
import com.library.exception.BookNotAvailableException;
import com.library.exception.LoanAlreadyReturnedException;
import com.library.exception.LoanLimitExceededException;
import com.library.exception.ResourceNotFoundException;
import com.library.exception.UnauthorizedActionException;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import com.library.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LoanService {

    public static final int MAX_ACTIVE_LOANS = 3;
    public static final int LOAN_PERIOD_DAYS = 14;

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public LoanService(
            LoanRepository loanRepository,
            BookRepository bookRepository,
            UserRepository userRepository
    ) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public LoanResponse borrowBook(Long bookId, String userEmail) {
        User user = getUserByEmailOrThrow(userEmail);
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + bookId));

        if (!book.isAvailable() || loanRepository.existsByBookIdAndReturnedAtIsNull(bookId)) {
            throw new BookNotAvailableException("Book is currently not available for borrowing");
        }

        long activeLoanCount = loanRepository.countByUserIdAndReturnedAtIsNull(user.getId());
        if (activeLoanCount >= MAX_ACTIVE_LOANS) {
            throw new LoanLimitExceededException(
                    "Maximum active loan limit (" + MAX_ACTIVE_LOANS + ") reached"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        book.setAvailable(false);
        bookRepository.save(book);

        Loan loan = new Loan(book, user, now, now.plusDays(LOAN_PERIOD_DAYS));
        Loan savedLoan = loanRepository.save(loan);
        return LoanResponse.from(savedLoan, now);
    }

    @Transactional
    public LoanResponse returnLoan(Long loanId, String userEmail) {
        User actingUser = getUserByEmailOrThrow(userEmail);
        Loan loan = loanRepository.findWithDetailsById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found with id: " + loanId));

        boolean isAdmin = actingUser.getRole() == Role.ADMIN;
        boolean isOwner = loan.getUser().getId().equals(actingUser.getId());
        if (!isAdmin && !isOwner) {
            throw new UnauthorizedActionException("You are not allowed to return another member's loan");
        }

        if (loan.getReturnedAt() != null) {
            throw new LoanAlreadyReturnedException("Loan has already been returned");
        }

        LocalDateTime now = LocalDateTime.now();
        loan.setReturnedAt(now);
        loan.getBook().setAvailable(true);
        bookRepository.save(loan.getBook());

        Loan savedLoan = loanRepository.save(loan);
        return LoanResponse.from(savedLoan, now);
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getMyLoans(String userEmail) {
        User user = getUserByEmailOrThrow(userEmail);
        LocalDateTime now = LocalDateTime.now();
        return loanRepository.findByUserIdOrderByBorrowedAtDesc(user.getId()).stream()
                .map(loan -> LoanResponse.from(loan, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getAllLoans(Boolean overdue) {
        LocalDateTime now = LocalDateTime.now();
        List<Loan> loans;
        if (Boolean.TRUE.equals(overdue)) {
            loans = loanRepository.findOverdueLoans(now);
        } else if (Boolean.FALSE.equals(overdue)) {
            loans = loanRepository.findNonOverdueLoans(now);
        } else {
            loans = loanRepository.findAllByOrderByBorrowedAtDesc();
        }
        return loans.stream()
                .map(loan -> LoanResponse.from(loan, now))
                .toList();
    }

    private User getUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
