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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LoanService loanService;

    private User member;
    private User otherMember;
    private User admin;
    private Book availableBook;

    @BeforeEach
    void setUp() {
        member = new User("member@library.com", "encoded-pass", "Kovacs Anna", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 2L);

        otherMember = new User("member2@library.com", "encoded-pass", "Nagy Peter", Role.MEMBER);
        ReflectionTestUtils.setField(otherMember, "id", 3L);

        admin = new User("admin@library.com", "encoded-pass", "Adminisztrator", Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 1L);

        availableBook = new Book("Clean Code", "Robert C. Martin", "978-0132350884", 2008);
        ReflectionTestUtils.setField(availableBook, "id", 10L);
    }

    @Test
    void borrowBook_success_setsUnavailableAndDueDate14Days() {
        when(userRepository.findByEmail("member@library.com")).thenReturn(Optional.of(member));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(availableBook));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(10L)).thenReturn(false);
        when(loanRepository.countByUserIdAndReturnedAtIsNull(2L)).thenReturn(2L);
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> {
            Loan saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        LoanResponse response = loanService.borrowBook(10L, "member@library.com");

        assertThat(availableBook.isAvailable()).isFalse();
        verify(bookRepository).save(availableBook);

        ArgumentCaptor<Loan> loanCaptor = ArgumentCaptor.forClass(Loan.class);
        verify(loanRepository).save(loanCaptor.capture());
        Loan persistedLoan = loanCaptor.getValue();

        assertThat(persistedLoan.getBook()).isEqualTo(availableBook);
        assertThat(persistedLoan.getUser()).isEqualTo(member);
        assertThat(persistedLoan.getReturnedAt()).isNull();
        assertThat(persistedLoan.getDueDate())
                .isEqualTo(persistedLoan.getBorrowedAt().plusDays(LoanService.LOAN_PERIOD_DAYS));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.bookId()).isEqualTo(10L);
        assertThat(response.userEmail()).isEqualTo("member@library.com");
        assertThat(response.overdue()).isFalse();
    }

    @Test
    void borrowBook_whenActiveLoanLimitReached_throwsLoanLimitExceededException() {
        when(userRepository.findByEmail("member@library.com")).thenReturn(Optional.of(member));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(availableBook));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(10L)).thenReturn(false);
        when(loanRepository.countByUserIdAndReturnedAtIsNull(2L)).thenReturn(3L);

        assertThatThrownBy(() -> loanService.borrowBook(10L, "member@library.com"))
                .isInstanceOf(LoanLimitExceededException.class)
                .hasMessageContaining("3");

        assertThat(availableBook.isAvailable()).isTrue();
        verify(bookRepository, never()).save(any());
        verify(loanRepository, never()).save(any());
    }

    @Test
    void borrowBook_whenBookUnavailable_throwsBookNotAvailableException() {
        availableBook.setAvailable(false);
        when(userRepository.findByEmail("member@library.com")).thenReturn(Optional.of(member));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(availableBook));

        assertThatThrownBy(() -> loanService.borrowBook(10L, "member@library.com"))
                .isInstanceOf(BookNotAvailableException.class);

        verify(loanRepository, never()).save(any());
    }

    @Test
    void borrowBook_whenBookHasActiveLoanRecord_throwsBookNotAvailableException() {
        when(userRepository.findByEmail("member@library.com")).thenReturn(Optional.of(member));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(availableBook));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(10L)).thenReturn(true);

        assertThatThrownBy(() -> loanService.borrowBook(10L, "member@library.com"))
                .isInstanceOf(BookNotAvailableException.class);

        verify(loanRepository, never()).save(any());
    }

    @Test
    void borrowBook_whenBookNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByEmail("member@library.com")).thenReturn(Optional.of(member));
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.borrowBook(999L, "member@library.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void returnLoan_byOwner_setsReturnedAtAndMakesBookAvailable() {
        availableBook.setAvailable(false);
        LocalDateTime borrowedAt = LocalDateTime.now().minusDays(5);
        Loan activeLoan = new Loan(availableBook, member, borrowedAt, borrowedAt.plusDays(14));
        ReflectionTestUtils.setField(activeLoan, "id", 50L);

        when(userRepository.findByEmail("member@library.com")).thenReturn(Optional.of(member));
        when(loanRepository.findWithDetailsById(50L)).thenReturn(Optional.of(activeLoan));
        when(loanRepository.save(activeLoan)).thenReturn(activeLoan);

        LoanResponse response = loanService.returnLoan(50L, "member@library.com");

        assertThat(activeLoan.getReturnedAt()).isNotNull();
        assertThat(availableBook.isAvailable()).isTrue();
        assertThat(response.returnedAt()).isNotNull();
        assertThat(response.overdue()).isFalse();
        verify(bookRepository).save(availableBook);
        verify(loanRepository).save(activeLoan);
    }

    @Test
    void returnLoan_byAdminForAnotherUser_succeeds() {
        availableBook.setAvailable(false);
        LocalDateTime borrowedAt = LocalDateTime.now().minusDays(20);
        Loan overdueLoan = new Loan(availableBook, member, borrowedAt, borrowedAt.plusDays(14));
        ReflectionTestUtils.setField(overdueLoan, "id", 51L);

        when(userRepository.findByEmail("admin@library.com")).thenReturn(Optional.of(admin));
        when(loanRepository.findWithDetailsById(51L)).thenReturn(Optional.of(overdueLoan));
        when(loanRepository.save(overdueLoan)).thenReturn(overdueLoan);

        LoanResponse response = loanService.returnLoan(51L, "admin@library.com");

        assertThat(overdueLoan.getReturnedAt()).isNotNull();
        assertThat(availableBook.isAvailable()).isTrue();
        assertThat(response.overdue()).isFalse();
    }

    @Test
    void returnLoan_byDifferentMember_throwsUnauthorizedActionException() {
        availableBook.setAvailable(false);
        Loan activeLoan = new Loan(availableBook, member, LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(12));
        ReflectionTestUtils.setField(activeLoan, "id", 52L);

        when(userRepository.findByEmail("member2@library.com")).thenReturn(Optional.of(otherMember));
        when(loanRepository.findWithDetailsById(52L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> loanService.returnLoan(52L, "member2@library.com"))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(loanRepository, never()).save(any());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void returnLoan_whenAlreadyReturned_throwsLoanAlreadyReturnedException() {
        Loan returnedLoan = new Loan(availableBook, member, LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(4));
        returnedLoan.setReturnedAt(LocalDateTime.now().minusDays(2));
        ReflectionTestUtils.setField(returnedLoan, "id", 53L);

        when(userRepository.findByEmail("member@library.com")).thenReturn(Optional.of(member));
        when(loanRepository.findWithDetailsById(53L)).thenReturn(Optional.of(returnedLoan));

        assertThatThrownBy(() -> loanService.returnLoan(53L, "member@library.com"))
                .isInstanceOf(LoanAlreadyReturnedException.class);

        verify(loanRepository, never()).save(any());
    }

    @Test
    void loanResponseOverdueFlag_calculatedCorrectly() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 10, 12, 0);

        Loan activeOverdue = new Loan(availableBook, member, now.minusDays(20), now.minusDays(1));
        ReflectionTestUtils.setField(activeOverdue, "id", 1L);

        Loan activeWithinDueDate = new Loan(availableBook, member, now.minusDays(5), now.plusDays(9));
        ReflectionTestUtils.setField(activeWithinDueDate, "id", 2L);

        Loan returnedPastDueDate = new Loan(availableBook, member, now.minusDays(20), now.minusDays(6));
        returnedPastDueDate.setReturnedAt(now.minusDays(2));
        ReflectionTestUtils.setField(returnedPastDueDate, "id", 3L);

        assertThat(LoanResponse.from(activeOverdue, now).overdue()).isTrue();
        assertThat(LoanResponse.from(activeWithinDueDate, now).overdue()).isFalse();
        assertThat(LoanResponse.from(returnedPastDueDate, now).overdue()).isFalse();
    }

    @Test
    void getMyLoans_returnsMappedLoansForUser() {
        Loan activeOverdue = new Loan(
                availableBook,
                member,
                LocalDateTime.now().minusDays(20),
                LocalDateTime.now().minusDays(6)
        );
        ReflectionTestUtils.setField(activeOverdue, "id", 70L);

        when(userRepository.findByEmail("member@library.com")).thenReturn(Optional.of(member));
        when(loanRepository.findByUserIdOrderByBorrowedAtDesc(2L)).thenReturn(List.of(activeOverdue));

        List<LoanResponse> result = loanService.getMyLoans("member@library.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(70L);
        assertThat(result.get(0).overdue()).isTrue();
    }

    @Test
    void getAllLoans_filtersByOverdueFlag() {
        Loan overdueLoan = new Loan(
                availableBook,
                member,
                LocalDateTime.now().minusDays(20),
                LocalDateTime.now().minusDays(3)
        );
        ReflectionTestUtils.setField(overdueLoan, "id", 80L);

        when(loanRepository.findOverdueLoans(any(LocalDateTime.class))).thenReturn(List.of(overdueLoan));
        when(loanRepository.findNonOverdueLoans(any(LocalDateTime.class))).thenReturn(List.of());
        when(loanRepository.findAllByOrderByBorrowedAtDesc()).thenReturn(List.of(overdueLoan));

        assertThat(loanService.getAllLoans(true)).hasSize(1);
        assertThat(loanService.getAllLoans(false)).isEmpty();
        assertThat(loanService.getAllLoans(null)).hasSize(1);
    }
}
