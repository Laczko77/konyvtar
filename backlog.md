# Fejlesztési Backlog — Könyvtár- és kölcsönzéskezelő

Ez a dokumentum a [PRD.md](./PRD.md) alapján készült fejlesztési terv és feladatlista. A feladatok a fázisok és technikai függőségek sorrendjében követik egymást.

---

## Jelölések és munkamenet

* **Státusz:**
  * `- [ ]` Nyitott / Elvégzendő
  * `- [x]` Kész (tesztelve és ellenőrizve)
* **Prioritás:**
  * `P0` – Kritikus alapfunkció / blokkoló
  * `P1` – Alap követelmény (MVP része)
  * `P2` – Kiegészítő / opcionális fejlesztés
* **Definition of Done (DoD):**
  * A kód lefordul és megfelel a PRD architekturális szabályainak (DTO-k használata, N+1 mentesség, `@Valid`, tiszta rétegzés).
  * Az érintett üzleti szabályokhoz és hibaágakhoz készültek automatizált tesztek, és `mvn verify` hibamentesen lefut.
  * Nincsenek hardkódolt jelszavak vagy titkos kulcsok a kódban.
  * Konvencionális commit üzenet (pl. `feat: implement loan limit check`).

---

## Fázis 0 — Projekt váz és konfigurációs alapok

* **Cél:** Tiszta repóstruktúra, környezeti változók sablonja és a `backend` / `frontend` mappák előkészítése.

- [x] **BL-001** `[P0]` **Repó és környezeti alapok létrehozása**
  * `.gitignore` létrehozása/bővítése Java/Maven, Angular/Node, IDE és `.env` fájlokhoz.
  * `.env.example` létrehozása a szükséges változókkal (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`).
  * Projekt könyvtárstruktúra kialakítása a gyökérben (`backend/`, `frontend/`, `.github/workflows/`).

---

## Fázis 1 — Backend alapok, adatbázis-séma és entitások

* **Cél:** Működő Spring Boot projekt, Flyway-jel menedzselt PostgreSQL séma, seed adatok, JPA entitások és N+1-mentes Repository réteg.

- [x] **BL-010** `[P0]` **Spring Boot 3.x + Java 21 Maven projekt inicializálása**
  * `backend/pom.xml` létrehozása a szükséges függőségekkel:
    * `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`
    * `flyway-core`, `flyway-database-postgresql`, `postgresql`
    * JWT könyvtár (pl. `jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
    * `springdoc-openapi-starter-webmvc-ui`
    * Tesztelés: `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`
  * Csomagstruktúra létrehozása `com.library` alatt (`config`, `controller`, `dto`, `entity`, `exception`, `repository`, `service`).

- [x] **BL-011** `[P0]` **Alkalmazás konfiguráció (`application.yml`)**
  * PostgreSQL adatforrás beállítása környezeti változókból alapértelmezett lokális értékekkel.
  * `spring.jpa.hibernate.ddl-auto: validate` szigorú beállítása.
  * Flyway engedélyezése (`spring.flyway.enabled: true`).
  * JWT konfigurációs tulajdonságok (`jwt.secret`, `jwt.expiration-ms`) bekötése.

- [x] **BL-012** `[P0]` **Adatbázis séma migráció (`V1__init_schema.sql`)**
  * `users` tábla: `id`, `email` (UNIQUE, NOT NULL), `password` (NOT NULL), `full_name` (NOT NULL), `role` (`ADMIN`/`MEMBER`), `created_at`.
  * `books` tábla: `id`, `title`, `author`, `isbn` (UNIQUE, NOT NULL), `published_year`, `available` (DEFAULT TRUE), `version` (DEFAULT 0).
  * `loans` tábla: `id`, `book_id` (FK -> `books.id`), `user_id` (FK -> `users.id`), `borrowed_at`, `due_date`, `returned_at` (NULLABLE).
  * Szükséges indexek és megszorítások (pl. `CHECK` vagy FK) hozzáadása.

- [x] **BL-013** `[P0]` **Mintaadatok migrációja (`V2__seed_data.sql`)**
  * 1 `ADMIN` (`admin@library.com` / `Admin123!`) és 2 `MEMBER` (`member@library.com` / `Member123!`, `member2@library.com` / `Member123!`) felhasználó érvényes BCrypt hash-sel.
  * Legalább 10 minta könyv (elérhető és kikölcsönzött vegyesen).
  * 2–3 minta kölcsönzés, köztük legalább 1 lejárt `due_date`-tel (hogy a késedelmi jelzés azonnal tesztelhető legyen), a kapcsolódó könyvek `available = false` állapotával összhangban.

- [x] **BL-014** `[P0]` **JPA entitások implementálása**
  * `Role` enum (`ADMIN`, `MEMBER`).
  * `User`, `Book`, `Loan` entitások pontos leképezése a Flyway sémához.
  * `Book` entitáson `@Version` mező az optimista zároláshoz (versenyhelyzetek kivédésére).
  * `Loan` entitáson `@ManyToOne(fetch = FetchType.LAZY)` kapcsolatok a `Book` és `User` entitások felé.

- [x] **BL-015** `[P0]` **Repository réteg és N+1 védelem**
  * `UserRepository`: `findByEmail(String email)`, `existsByEmail(String email)`.
  * `BookRepository`: `JpaRepository` + `JpaSpecificationExecutor<Book>` a dinamikus szűréshez (`title`, `author`, `available`), `existsByIsbn(String isbn)`.
  * `LoanRepository`:
    * `countByUserIdAndReturnedAtIsNull(Long userId)` az aktív kölcsönzések számolásához.
    * `existsByBookIdAndReturnedAtIsNull(Long bookId)` a dupla kiadás ellenőrzéséhez.
    * Saját és összes kölcsönzés lekérdezése `@EntityGraph(attributePaths = {"book", "user"})` használatával (lapozva vagy szűrve, `overdue` szűrés támogatásával).

---

## Fázis 2 — Üzleti logika, DTO-k, REST API és Swagger

* **Cél:** Teljes üzleti réteg, szigorú validáció és hibakezelés, REST végpontok, Swagger dokumentáció.

- [x] **BL-020** `[P0]` **DTO `record`-ok és mappelés kialakítása**
  * Auth DTO-k: `RegisterRequest`, `LoginRequest`, `AuthResponse` (token + user adatok/role).
  * Book DTO-k: `BookRequest` (`@NotBlank`, `@Size`, `@Min`/`@Max` validációkkal), `BookResponse`.
  * Member DTO-k: `MemberResponse`.
  * Loan DTO-k: `CreateLoanRequest` (`@NotNull bookId`), `LoanResponse` (számított `overdue` boolean mezővel: `returnedAt == null && now > dueDate`).
  * Ellenőrzés: egyetlen Controller sem adhat vissza JPA entitást.

- [x] **BL-021** `[P0]` **Kivételkezelés és globális `@RestControllerAdvice`**
  * Egyedi kivételek létrehozása:
    * `ResourceNotFoundException` (`404 Not Found`)
    * `LoanLimitExceededException` (`409 Conflict`)
    * `BookNotAvailableException` (`409 Conflict`)
    * `LoanAlreadyReturnedException` (`409 Conflict`)
    * `DuplicateResourceException` (pl. duplikált email vagy ISBN -> `409 Conflict`)
    * `UnauthorizedActionException` / `AccessDeniedException` (`403 Forbidden`)
  * `GlobalExceptionHandler` (`@RestControllerAdvice`): egységes `ErrorResponse` vagy RFC 7807 `ProblemDetail` (`timestamp`, `status`, `error`, `message`, `validationErrors`), `@Valid` (`MethodArgumentNotValidException`) és optimista zárolási hiba (`ObjectOptimisticLockingFailureException` -> `409 Conflict`) kezelése.

- [x] **BL-022** `[P0]` **Könyvkezelő szolgáltatás és API (`BookService` & `BookController`)**
  * `GET /api/books?title=&author=&available=&page=0&size=10` — `Specification` alapú szűrés és lapozás (`Pageable`).
  * `GET /api/books/{id}` — könyv részletei.
  * `POST /api/books` — új könyv felvitele (`ADMIN`, ISBN egyediség ellenőrzése).
  * `PUT /api/books/{id}` — könyv módosítása (`ADMIN`).
  * `DELETE /api/books/{id}` — könyv törlése (`ADMIN`, aktív kölcsönzés esetén védett/kezelt hibaág).

- [x] **BL-023** `[P0]` **Kölcsönzéskezelő szolgáltatás és API (`LoanService` & `LoanController`)**
  * `POST /api/loans` (`@Transactional`):
    * Könyv létezésének és elérhetőségének (`available == true` és nincs aktív `Loan`) ellenőrzése -> `BookNotAvailableException`.
    * Tag aktív kölcsönzéseinek számolása (max. 3) -> `LoanLimitExceededException`.
    * `book.setAvailable(false)`, `Loan` mentése `borrowed_at = now`, `due_date = borrowed_at + 14 nap`.
  * `PATCH /api/loans/{id}/return` (`@Transactional`):
    * Jogosultság ellenőrzése: `ADMIN` bármelyiket visszaveheti, `MEMBER` csak a sajátját (`403 Forbidden` más kölcsönzése esetén).
    * Már visszahozott kölcsönzés elutasítása (`LoanAlreadyReturnedException`).
    * `returned_at = now`, `book.setAvailable(true)`.
  * `GET /api/loans/my` — bejelentkezett felhasználó saját kölcsönzései.
  * `GET /api/loans?overdue=true` — összes kölcsönzés (`ADMIN`), opcionális `overdue` szűrővel.

- [x] **BL-024** `[P1]` **Tagkezelő szolgáltatás és API (`MemberService` & `MemberController`)**
  * `GET /api/members` — összes tag listázása (`ADMIN`).
  * `GET /api/members/me` — aktuálisan bejelentkezett felhasználó adatai.

- [x] **BL-025** `[P1]` **Springdoc OpenAPI / Swagger UI konfiguráció**
  * `OpenApiConfig`: Bearer JWT security scheme beállítása, hogy a Swagger UI-ból közvetlenül lehessen tokennel hívni a védett végpontokat.
  * Elérhetőség ellenőrzése a `/swagger-ui.html` útvonalon.

---

## Fázis 3 — Biztonság (Spring Security + JWT) és Automatizált tesztek

* **Cél:** Stateless JWT hitelesítés, szerepkör-alapú jogosultságkezelés, valamint átfogó unit és Testcontainers integrációs tesztek.

- [x] **BL-030** `[P0]` **JWT szolgáltatás és autentikációs filter**
  * `JwtService`: HMAC-SHA256 aláírás (`JWT_SECRET`-ből), token generálás (subject = email, role claim), token validálás és claim kinyerés.
  * `JwtAuthenticationFilter` (`OncePerRequestFilter`): `Authorization: Bearer <token>` feldolgozása, `SecurityContextHolder` feltöltése.
  * `CustomUserDetailsService` + `BCryptPasswordEncoder`.

- [x] **BL-031** `[P0]` **Security konfiguráció és Auth végpontok**
  * `SecurityConfig`: CSRF kikapcsolása (stateless API), `SessionCreationPolicy.STATELESS`, CORS konfiguráció (`http://localhost:4200`), publikus végpontok (`/api/auth/**`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`), minden más hitelesítéshez kötve, `@EnableMethodSecurity` (`@PreAuthorize`).
  * `AuthService` & `AuthController`:
    * `POST /api/auth/register` — publikus `MEMBER` regisztráció, BCrypt jelszó-hash, email duplikáció ellenőrzés.
    * `POST /api/auth/login` — hitelesítés `AuthenticationManager`-rel, JWT token visszaadása.

- [x] **BL-032** `[P0]` **Unit tesztek (JUnit 5 + Mockito)**
  * `LoanServiceTest`:
    * Sikeres kölcsönzés (`available` -> `false`, `due_date = +14 nap`).
    * Sikeres visszahozás (`returned_at` kitöltve, `available` -> `true`).
    * Hibaág: 4. aktív kölcsönzés indításakor `LoanLimitExceededException`.
    * Hibaág: már kikölcsönzött könyv esetén `BookNotAvailableException`.
    * Hibaág: már visszahozott kölcsönzés újbóli visszavételekor kivétel.
    * Hibaág: `MEMBER` más felhasználó kölcsönzését próbálja visszahozni -> jogosultsági hiba.
    * `LoanResponse` `overdue` flag helyes számítása (aktív lejárt = `true`, aktív határidőn belüli = `false`, lezárt = `false`).
  * `BookServiceTest` & `AuthServiceTest`: alap CRUD/szűrési és regisztrációs hibaágak (duplikált ISBN/email).

- [x] **BL-033** `[P0]` **Integrációs tesztek (Testcontainers PostgreSQL + MockMvc)**
  * Közös Testcontainers bázisosztály PostgreSQL 16 konténerrel, Flyway migrációk (`V1`, `V2`) és `ddl-auto=validate` automatikus ellenőrzésével.
  * E2E teszt: Login (`POST /api/auth/login`) -> JWT kinyerése -> Könyv kölcsönzése (`POST /api/loans`) -> Kölcsönzések lekérdezése (`GET /api/loans/my`) -> Visszahozás (`PATCH /api/loans/{id}/return`).
  * Jogosultsági (RBAC) tesztek:
    * Token nélküli hívás védett végpontra -> `401 Unauthorized` (vagy Spring Security 401/403 entry point szerint egységes `401`).
    * `MEMBER` hívja az `ADMIN` végpontokat (`POST /api/books`, `GET /api/loans`, `GET /api/members`) -> `403 Forbidden`.
  * Üzleti szabály integrációs teszt: 4. kölcsönzés elutasítása és dupla kölcsönzés elutasítása valós DB ellen.

---

## Fázis 4 — Frontend (Angular MVP)

* **Cél:** Letisztult, funkcionális Angular felület a fő felhasználói folyamatokhoz (`Login`, `Könyvek`, `Kölcsönzések`).

- [x] **BL-040** `[P1]` **Angular alkalmazás inicializálása és core réteg**
  * Standalone komponens alapú Angular (aktuális LTS) projekt létrehozása a `frontend/` mappában.
  * Környezeti konfiguráció (`API_URL`), routing (`app.routes.ts`), `provideHttpClient(withInterceptors([authInterceptor]))`.
  * `AuthService`: login, register, logout, JWT token mentése/olvasása `localStorage`-ból, aktuális user és szerepkör (`ADMIN`/`MEMBER`) dekódolása vagy `/api/members/me` betöltése.
  * `authInterceptor`: `Authorization: Bearer <token>` csatolása és `401` esetén átirányítás a `/login` oldalra.
  * `authGuard`: védett útvonalak őrzése.

- [x] **BL-041** `[P1]` **Bejelentkezés és regisztráció oldal**
  * Email + jelszó űrlap reaktív validációval, szerveroldali hibaüzenetek megjelenítése.
  * Regisztrációs nézet/fül (`POST /api/auth/register`) új tagoknak.
  * Sikeres belépés után átirányítás a könyvek listájára.

- [x] **BL-042** `[P1]` **Könyvek oldal (böngészés, szűrés, CRUD, kölcsönzés)**
  * Keresőmező (cím és szerző szerint), elérhetőség szűrő (`Mind` / `Csak elérhető` / `Kikölcsönzött`).
  * Táblázatos lista lapozással (`page`, `size`, előző/következő oldal).
  * „Kölcsönzés” gomb az elérhető könyveknél (`POST /api/loans`), visszajelzés sikerről vagy üzleti hibáról (pl. max. 3 könyv limit).
  * `ADMIN` funkciók: „Új könyv” létrehozása, könyv szerkesztése és „Törlés” gomb megerősítéssel.

- [x] **BL-043** `[P1]` **Kölcsönzéseim / Összes kölcsönzés oldal**
  * Aktív és lezárt kölcsönzések listázása (`GET /api/loans/my`, `ADMIN` esetén `GET /api/loans` nézetváltóval és `overdue=true` szűrővel).
  * Piros `Késedelmes` jelvény megjelenítése, ha `overdue === true`.
  * „Visszahozás” gomb aktív kölcsönzéseknél (`PATCH /api/loans/{id}/return`) és lista automatikus frissítése.

- [x] **BL-044** `[P2]` **Alap layout, navigáció és tagok nézet (`ADMIN`)**
  * Felső navigációs sáv (Könyvek, Kölcsönzéseim, [Admin: Összes kölcsönzés / Tagok], felhasználónév + szerepkör + Kijelentkezés).
  * Tagok listája nézet `ADMIN` szerepkörhöz (`GET /api/members`).

---

## Fázis 5 — DevOps, Docker, CI/CD és Dokumentáció

* **Cél:** Egy paranccsal indítható konténerizált rendszer, zöld CI pipeline és portfólió-minőségű README.

- [x] **BL-050** `[P1]` **Backend `Dockerfile` (multi-stage build)**
  * `backend/Dockerfile` létrehozása a PRD 8. pontja szerint (`maven:3.9-eclipse-temurin-21` build -> `eclipse-temurin:21-jre` runtime).
  * `.dockerignore` létrehozása (`target/`, stb.).

- [x] **BL-051** `[P2]` **Frontend `Dockerfile` és Nginx konfiguráció (opcionális MVP bővítés)**
  * `frontend/Dockerfile` multi-stage build (Node build -> Nginx) a `4200`-as vagy `80`-as portra, Angular SPA routing (`try_files $uri $uri/ /index.html`) és `/api` proxy támogatással.

- [x] **BL-052** `[P1]` **`docker-compose.yml` összeállítása**
  * `db` (`postgres:16` healthcheckkel) és `api` (`build: ./backend`, `depends_on: service_healthy`) szolgáltatások bekötése.
  * Opcionálisan `frontend` service bekötése a `4200`-as porton.
  * Tiszta indítás ellenőrzése `docker compose up --build` paranccsal.

- [x] **BL-053** `[P1]` **GitHub Actions CI pipeline (`.github/workflows/ci.yml`)**
  * `push` és `pull_request` triggerek, Java 21 (Temurin) + Maven cache, `mvn -B verify` futtatása a `./backend` könyvtárban.
  * Ellenőrzés: a Testcontainers tesztek hiba nélkül lefutnak CI környezetben.

- [x] **BL-054** `[P1]` **Teljes `README.md` elkészítése**
  * Rövid leírás, CI badge (és opcionális élő demó link).
  * Képernyőkép vagy GIF az alkalmazásról.
  * Tech stack felsorolása.
  * Indítás egy paranccsal (`docker compose up --build`).
  * Swagger UI link (`/swagger-ui.html`), fő végpontok és seed teszt belépési adatok (`admin@library.com`, `member@library.com`).
  * Tesztek futtatása (`mvn verify`).
  * **Mit tanultam / architekturális döntések:** DTO vs. Entitás, N+1 elkerülése `@EntityGraph`-fal, Flyway vs. `ddl-auto=update`, `@Version` optimista zárolás.

- [x] **BL-055** `[P0]` **Készre jelentés ellenőrzőlista (PRD 10. fejezet)**
  * `docker compose up` tiszta környezetben hiba nélkül elindítja a rendszert.
  * A CI zöld, és a badge szerepel a `README.md` tetején.
  * Az üzleti szabályokra és hibaágakra szóló unit és integrációs tesztek zöldek.
  * Nincs jelszó vagy titkos kulcs a repóban (`.env.example` megvan).
  * Konvencionális commit üzenetek.
  * A Flyway seed adatokkal az alkalmazás azonnal kipróbálható.
