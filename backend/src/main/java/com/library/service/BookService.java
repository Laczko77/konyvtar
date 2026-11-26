package com.library.service;

import com.library.dto.BookRequest;
import com.library.dto.BookResponse;
import com.library.entity.Book;
import com.library.exception.BookHasLoanHistoryException;
import com.library.exception.BookNotAvailableException;
import com.library.exception.DuplicateResourceException;
import com.library.exception.ResourceNotFoundException;
import com.library.repository.BookRepository;
import com.library.repository.BookSpecification;
import com.library.repository.LoanRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;

    public BookService(BookRepository bookRepository, LoanRepository loanRepository) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
    }

    @Transactional(readOnly = true)
    public Page<BookResponse> findBooks(String title, String author, Boolean available, Pageable pageable) {
        return bookRepository
                .findAll(BookSpecification.withFilters(title, author, available), pageable)
                .map(BookResponse::from);
    }

    @Transactional(readOnly = true)
    public BookResponse findById(Long id) {
        Book book = getBookOrThrow(id);
        return BookResponse.from(book);
    }

    @Transactional
    public BookResponse createBook(BookRequest request) {
        String normalizedIsbn = request.isbn().trim();
        if (bookRepository.existsByIsbn(normalizedIsbn)) {
            throw new DuplicateResourceException("Book with ISBN already exists: " + normalizedIsbn);
        }

        Book book = new Book(
                request.title().trim(),
                request.author().trim(),
                normalizedIsbn,
                request.publishedYear()
        );
        return BookResponse.from(bookRepository.save(book));
    }

    @Transactional
    public BookResponse updateBook(Long id, BookRequest request) {
        Book book = getBookOrThrow(id);
        String normalizedIsbn = request.isbn().trim();
        if (bookRepository.existsByIsbnAndIdNot(normalizedIsbn, id)) {
            throw new DuplicateResourceException("Book with ISBN already exists: " + normalizedIsbn);
        }

        book.setTitle(request.title().trim());
        book.setAuthor(request.author().trim());
        book.setIsbn(normalizedIsbn);
        book.setPublishedYear(request.publishedYear());
        return BookResponse.from(bookRepository.save(book));
    }

    @Transactional
    public void deleteBook(Long id) {
        Book book = getBookOrThrow(id);
        if (!book.isAvailable() || loanRepository.existsByBookIdAndReturnedAtIsNull(id)) {
            throw new BookNotAvailableException("Cannot delete a book that is currently loaned out");
        }
        if (loanRepository.existsByBookId(id)) {
            throw new BookHasLoanHistoryException("Cannot delete a book with existing loan history");
        }
        bookRepository.delete(book);
    }

    private Book getBookOrThrow(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + id));
    }
}
