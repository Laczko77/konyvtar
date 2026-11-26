package com.library.controller;

import com.library.dto.CreateLoanRequest;
import com.library.dto.LoanResponse;
import com.library.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
@Tag(name = "Kolcsonzesek", description = "Kolcsonzes inditas, visszahozes, listazas")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    @Operation(summary = "Konyv kolcsonzese a bejelentkezett felhasznalonak")
    public ResponseEntity<LoanResponse> borrowBook(
            @Valid @RequestBody CreateLoanRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        LoanResponse loan = loanService.borrowBook(request.bookId(), principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(loan);
    }

    @PatchMapping("/{id}/return")
    @Operation(summary = "Kolcsonzes visszahozasa")
    public ResponseEntity<LoanResponse> returnLoan(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        LoanResponse returned = loanService.returnLoan(id, principal.getUsername());
        return ResponseEntity.ok(returned);
    }

    @GetMapping("/my")
    @Operation(summary = "Sajat kolcsonzesek listazasa")
    public ResponseEntity<List<LoanResponse>> getMyLoans(
            @AuthenticationPrincipal UserDetails principal
    ) {
        List<LoanResponse> loans = loanService.getMyLoans(principal.getUsername());
        return ResponseEntity.ok(loans);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Osszes kolcsonzes listazasa (csak ADMIN), opcionalis overdue szuro")
    public ResponseEntity<List<LoanResponse>> getAllLoans(
            @RequestParam(required = false) Boolean overdue
    ) {
        List<LoanResponse> loans = loanService.getAllLoans(overdue);
        return ResponseEntity.ok(loans);
    }
}
