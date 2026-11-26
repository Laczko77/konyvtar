package com.library.repository;

import com.library.entity.Loan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    long countByUserIdAndReturnedAtIsNull(Long userId);

    boolean existsByBookIdAndReturnedAtIsNull(Long bookId);

    boolean existsByBookId(Long bookId);

    @EntityGraph(attributePaths = {"book", "user"})
    Optional<Loan> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"book", "user"})
    List<Loan> findByUserIdOrderByBorrowedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"book", "user"})
    List<Loan> findAllByOrderByBorrowedAtDesc();

    @EntityGraph(attributePaths = {"book", "user"})
    @Query("SELECT l FROM Loan l WHERE l.returnedAt IS NULL AND l.dueDate < :now ORDER BY l.dueDate ASC")
    List<Loan> findOverdueLoans(@Param("now") LocalDateTime now);
}
