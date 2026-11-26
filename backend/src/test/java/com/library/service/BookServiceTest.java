package com.library.service;

import com.library.dto.BookRequest;
import com.library.dto.BookResponse;
import com.library.entity.Book;
import com.library.exception.BookHasLoanHistoryException;
import com.library.exception.BookNotAvailableException;
import com.library.exception.DuplicateResourceException;
import com.library.exception.ResourceNotFoundException;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanRepository loanRepository;

    @InjectMocks
    private BookService bookService;

    private Book existingBook;

    @BeforeEach
    void setUp() {
        existingBook = new Book("Clean Code", "Robert C. Martin", "978-0132350884", 2008);
        ReflectionTestUtils.setField(existingBook, "id", 1L);
    }

    @Test
    void findBooks_returnsPagedBookResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Book> page = new PageImpl<>(List.of(existingBook), pageable, 1);

        when(bookRepository.findAll(ArgumentMatchers.<Specification<Book>>any(), eq(pageable))).thenReturn(page);

        Page<BookResponse> result = bookService.findBooks("Clean", null, true, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Clean Code");
    }

    @Test
    void findById_whenMissing_throwsResourceNotFoundException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createBook_success_trimsFieldsAndSaves() {
        BookRequest request = new BookRequest("  Effective Java  ", "  Joshua Bloch ", " 978-0134685991 ", 2018);
        when(bookRepository.existsByIsbn("978-0134685991")).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        BookResponse response = bookService.createBook(request);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.title()).isEqualTo("Effective Java");
        assertThat(response.author()).isEqualTo("Joshua Bloch");
        assertThat(response.isbn()).isEqualTo("978-0134685991");
        assertThat(response.available()).isTrue();
    }

    @Test
    void createBook_whenIsbnExists_throwsDuplicateResourceException() {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "978-0132350884", 2008);
        when(bookRepository.existsByIsbn("978-0132350884")).thenReturn(true);

        assertThatThrownBy(() -> bookService.createBook(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(bookRepository, never()).save(any());
    }

    @Test
    void updateBook_whenIsbnBelongsToOtherBook_throwsDuplicateResourceException() {
        BookRequest request = new BookRequest("Updated", "Author", "978-9999999999", 2020);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existingBook));
        when(bookRepository.existsByIsbnAndIdNot("978-9999999999", 1L)).thenReturn(true);

        assertThatThrownBy(() -> bookService.updateBook(1L, request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(bookRepository, never()).save(any());
    }

    @Test
    void deleteBook_whenAvailableAndNoHistory_deletesBook() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existingBook));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(1L)).thenReturn(false);
        when(loanRepository.existsByBookId(1L)).thenReturn(false);

        bookService.deleteBook(1L);

        verify(bookRepository).delete(existingBook);
    }

    @Test
    void deleteBook_whenCurrentlyLoaned_throwsBookNotAvailableException() {
        existingBook.setAvailable(false);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existingBook));

        assertThatThrownBy(() -> bookService.deleteBook(1L))
                .isInstanceOf(BookNotAvailableException.class);

        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void deleteBook_whenHasLoanHistory_throwsBookHasLoanHistoryException() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existingBook));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(1L)).thenReturn(false);
        when(loanRepository.existsByBookId(1L)).thenReturn(true);

        assertThatThrownBy(() -> bookService.deleteBook(1L))
                .isInstanceOf(BookHasLoanHistoryException.class);

        verify(bookRepository, never()).delete(any(Book.class));
    }
}
