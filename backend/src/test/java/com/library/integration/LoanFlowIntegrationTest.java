package com.library.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void e2e_loginBorrowListAndReturnBook_updatesAvailabilityAndLoanState() throws Exception {
        String adminToken = login("admin@library.com", "Admin123!");
        String memberToken = registerNewMember("e2e");
        long bookId = createBook(adminToken, "E2E Clean Architecture");

        MvcResult borrowResult = mockMvc.perform(post("/api/loans")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bookId": %d }
                                """.formatted(bookId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(bookId))
                .andExpect(jsonPath("$.returnedAt").value(nullValue()))
                .andExpect(jsonPath("$.overdue").value(false))
                .andReturn();

        long loanId = objectMapper.readTree(borrowResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/books/{id}", bookId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get("/api/loans/my")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) loanId)));

        mockMvc.perform(patch("/api/loans/{id}/return", loanId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedAt", notNullValue()))
                .andExpect(jsonPath("$.overdue").value(false));

        mockMvc.perform(get("/api/books/{id}", bookId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void businessRules_rejectFourthActiveLoanDoubleBorrowDuplicateReturnAndForeignReturn() throws Exception {
        String adminToken = login("admin@library.com", "Admin123!");
        String firstMemberToken = registerNewMember("limit1");
        String secondMemberToken = registerNewMember("limit2");

        long book1 = createBook(adminToken, "Rule Book 1");
        long book2 = createBook(adminToken, "Rule Book 2");
        long book3 = createBook(adminToken, "Rule Book 3");
        long book4 = createBook(adminToken, "Rule Book 4");

        long firstLoanId = borrowBook(firstMemberToken, book1);
        borrowBook(firstMemberToken, book2);
        borrowBook(firstMemberToken, book3);

        // 4. aktiv kolcsonzes elutasitasa (max 3)
        mockMvc.perform(post("/api/loans")
                        .header("Authorization", "Bearer " + firstMemberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bookId": %d }
                                """.formatted(book4)))
                .andExpect(status().isConflict());

        // Dupla kolcsonzes elutasitasa (mar kikolcsonzott konyv)
        mockMvc.perform(post("/api/loans")
                        .header("Authorization", "Bearer " + secondMemberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bookId": %d }
                                """.formatted(book1)))
                .andExpect(status().isConflict());

        // Masik tag nem hozhatja vissza mas kolcsonzeset
        mockMvc.perform(patch("/api/loans/{id}/return", firstLoanId)
                        .header("Authorization", "Bearer " + secondMemberToken))
                .andExpect(status().isForbidden());

        // Admin visszaveheti barmelyik kolcsonzest
        mockMvc.perform(patch("/api/loans/{id}/return", firstLoanId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedAt", notNullValue()));

        // Mar visszahozott kolcsonzes ujbóli visszavetele elutasitva
        mockMvc.perform(patch("/api/loans/{id}/return", firstLoanId)
                        .header("Authorization", "Bearer " + firstMemberToken))
                .andExpect(status().isConflict());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String registerNewMember(String prefix) throws Exception {
        String email = prefix + "." + UUID.randomUUID() + "@library.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123!",
                                  "fullName": "Teszt Tag"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createBook(String adminToken, String title) throws Exception {
        String isbn = "978-" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_0000L);
        MvcResult result = mockMvc.perform(post("/api/books")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "author": "Teszt Szerzo",
                                  "isbn": "%s",
                                  "publishedYear": 2024
                                }
                                """.formatted(title, isbn)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private long borrowBook(String token, long bookId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bookId": %d }
                                """.formatted(bookId)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
