package com.library.controller;

import com.library.dto.MemberResponse;
import com.library.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@Tag(name = "Tagok", description = "Tagok listazasa es sajat profil")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Osszes tag listazasa (csak ADMIN)")
    public ResponseEntity<List<MemberResponse>> getAllMembers() {
        List<MemberResponse> members = memberService.findAllMembers();
        return ResponseEntity.ok(members);
    }

    @GetMapping("/me")
    @Operation(summary = "Bejelentkezett felhasznalo adatai")
    public ResponseEntity<MemberResponse> getCurrentMember(
            @AuthenticationPrincipal UserDetails principal
    ) {
        MemberResponse member = memberService.findByEmail(principal.getUsername());
        return ResponseEntity.ok(member);
    }
}
