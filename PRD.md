# PRD: Könyvtár- és kölcsönzéskezelő (Spring Boot + Angular)

## 1. Projekt áttekintés és cél
Egy teljes értékű könyvtár- és kölcsönzéskezelő rendszer, amely bemutatja a modern Java backend (Spring Boot, Spring Data JPA, Hibernate, Flyway, Spring Security + JWT, Testcontainers) és az Angular frontend fejlesztési gyakorlatokat.
A hangsúly a **backend architektúra minőségén, a szigorú üzleti szabályokon, a tiszta rétegzésen (DTO-k) és a megbízható automatizált teszteken** van; az Angular felület letisztult, funkcionális MVP.

---

## 2. Technológiai stack és könyvtárszerkezet
* **Backend:** Java 21, Spring Boot 3.x, Maven
* **Adatkezelés:** Spring Data JPA (Hibernate), PostgreSQL 16, Flyway (adatbázis-migráció)
* **Biztonság:** Spring Security + JWT (Stateless auth, BCrypt jelszó-hash)
* **Dokumentáció:** Springdoc OpenAPI / Swagger UI
* **Tesztelés:** JUnit 5, Mockito, Testcontainers (PostgreSQL)
* **Frontend:** Angular (aktuális LTS, standalone komponensek, HttpClient, Router)
* **DevOps:** Docker (multi-stage build), Docker Compose, GitHub Actions (CI)

### Javasolt projektstruktúra
```text
library-management/
├── backend/
│   ├── src/main/java/com/library/
│   │   ├── config/          # Security, JWT, OpenAPI, CORS
│   │   ├── controller/      # REST Controllerek
│   │   ├── dto/             # Request/Response Record-ok
│   │   ├── entity/          # JPA Entitások
│   │   ├── exception/       # Custom kivételek + @ControllerAdvice
│   │   ├── repository/      # Spring Data JPA + @EntityGraph
│   │   └── service/         # Üzleti logika
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/    # Flyway SQL szkriptek (V1__, V2__...)
│   ├── src/test/java/       # Unit és Testcontainers integrációs tesztek
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                # Angular alkalmazás
├── .github/workflows/ci.yml
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 3. Adatbázis séma és Flyway migrációk
* **Fontos szabály:** `spring.jpa.hibernate.ddl-auto=validate` beállítás használata! Minden sémaváltozás kizárólag Flyway migrációval történhet.

### Táblák (`V1__init_schema.sql`)
1. **`users`**
   * `id` (BIGSERIAL, PK)
   * `email` (VARCHAR(255), UNIQUE, NOT NULL)
   * `password` (VARCHAR(255), NOT NULL - BCrypt)
   * `full_name` (VARCHAR(150), NOT NULL)
   * `role` (VARCHAR(20), NOT NULL — értékek: `ADMIN`, `MEMBER`)
   * `created_at` (TIMESTAMP, NOT NULL)
2. **`books`**
   * `id` (BIGSERIAL, PK)
   * `title` (VARCHAR(255), NOT NULL)
   * `author` (VARCHAR(255), NOT NULL)
   * `isbn` (VARCHAR(20), UNIQUE, NOT NULL)
   * `published_year` (INT)
   * `available` (BOOLEAN, NOT NULL, DEFAULT TRUE)
   * `version` (BIGINT, DEFAULT 0 — optimista zároláshoz `@Version`, versenyhelyzetek ellen)
3. **`loans`**
   * `id` (BIGSERIAL, PK)
   * `book_id` (BIGINT, FK -> `books.id`, NOT NULL)
   * `user_id` (BIGINT, FK -> `users.id`, NOT NULL)
   * `borrowed_at` (TIMESTAMP, NOT NULL)
   * `due_date` (TIMESTAMP, NOT NULL — alapértelmezetten +14 nap)
   * `returned_at` (TIMESTAMP, NULLABLE)

### Seed adatok (`V2__seed_data.sql`)
* 1 `ADMIN` felhasználó (pl. `admin@library.com` / `Admin123!`)
* 2 `MEMBER` felhasználó (pl. `member@library.com` / `Member123!`, `member2@library.com` / `Member123!`)
* Legalább 10 minta könyv (elérhető és kikölcsönzött vegyesen)
* 2–3 minta kölcsönzés (köztük egy lejárt határidejű, hogy a késedelmi jelzés azonnal látható legyen).

---

## 4. Funkcionális követelmények és üzleti szabályok

### 4.1. Hitelesítés és szerepkörök
* **JWT alapú autentikáció:** Bejelentkezés után Bearer token kiadása.
* **`ADMIN`:** Teljes CRUD a könyveken, tagok listázása/kezelése, összes kölcsönzés megtekintése, bármely kölcsönzés visszavétele.
* **`MEMBER`:** Könyvek böngészése/keresése, saját kölcsönzés indítása, kizárólag a saját kölcsönzéseinek megtekintése és visszahozatala.

### 4.2. Könyvek kezelése
* CRUD műveletek (`ADMIN` jogkörrel az írási műveletekhez).
* Listázás **lapozással** (`Pageable`) és **szűréssel**: cím (`title`), szerző (`author`), elérhetőség (`available`).
* Használjunk Spring Data `Specification` vagy egyedi `@Query` megoldást.

### 4.3. Kölcsönzési üzleti szabályok (Service rétegben szigorúan ellenőrizve)
1. **Max. 3 aktív kölcsönzés szabály:** Egy tagnak egyszerre legfeljebb 3 aktív (`returned_at IS NULL`) kölcsönzése lehet. A 4. próbálkozásnál dobjon `LoanLimitExceededException`-t (`409 Conflict` vagy `400 Bad Request`).
2. **Dupla kiadás tilalma:** Ha a könyv `available == false` (vagy van rá aktív kölcsönzés), nem kölcsönözhető ki újra -> `BookNotAvailableException` (`409 Conflict`).
3. **Kölcsönzés indítása:** Tranzakcionális (`@Transactional`), a könyv `available` mezője `false` lesz, létrejön a `Loan` rekord (`due_date = borrowed_at + 14 nap`).
4. **Visszahozás:** A `returned_at` kitöltése aktuális időbélyeggel, a könyv `available` mezőjének visszaállítása `true`-ra. Már visszahozott kölcsönzést nem lehet újra visszavenni.
5. **Késedelmi jelzés:** A `LoanResponseDto` számított mezőként tartalmazza az `overdue: boolean` flaget (`returnedAt == null && LocalDateTime.now().isAfter(dueDate)`).

### 4.4. Architektúrális és minőségi követelmények
* **DTO használat:** Entitásokat SOHA nem adunk vissza közvetlenül a Controllerből (Java `record` DTO-k használata).
* **N+1 probléma elkerülése:** A kölcsönzések listázásakor `@EntityGraph(attributePaths = {"book", "user"})` vagy `JOIN FETCH` használata a Repository-ban.
* **Validáció és hibakezelés:** `@Valid` annotáció a kéréseken, globális `@RestControllerAdvice` egységes hibaformátummal (RFC 7807 `ProblemDetail` vagy saját `ErrorResponse`: timestamp, status, error, message, validationErrors).

---

## 5. API Végpontok specifikációja
* **Auth:**
  * `POST /api/auth/register` (Nyilvános - MEMBER regisztráció)
  * `POST /api/auth/login` (Nyilvános - JWT token visszaadása)
* **Könyvek:**
  * `GET /api/books?title=&author=&available=&page=0&size=10` (Hitelesített)
  * `GET /api/books/{id}` (Hitelesített)
  * `POST /api/books` (`ADMIN`)
  * `PUT /api/books/{id}` (`ADMIN`)
  * `DELETE /api/books/{id}` (`ADMIN`)
* **Tagok:**
  * `GET /api/members` (`ADMIN`)
  * `GET /api/members/me` (Hitelesített)
* **Kölcsönzések:**
  * `POST /api/loans` (Body: `{ "bookId": 1 }` — `MEMBER`/`ADMIN`)
  * `PATCH /api/loans/{id}/return` (Visszahozás — `ADMIN` bármelyiket, `MEMBER` csak a sajátját)
  * `GET /api/loans/my` (Saját kölcsönzések — `MEMBER`)
  * `GET /api/loans` (Összes kölcsönzés, szűrhető `overdue=true` paraméterrel — `ADMIN`)
* **Swagger UI:** `/swagger-ui.html` (JWT Bearer token támogatással konfigurálva).

---

## 6. Frontend specifikáció (Angular MVP)
* **Login oldal:** Email + jelszó űrlap, JWT mentése `localStorage`-ba, HTTP Interceptor a `Bearer` token automatikus csatolásához.
* **Könyvek oldal:** Keresőmező (cím/szerző), elérhetőség szűrő, táblázatos lista lapozással, "Kölcsönzés" gomb az elérhető könyveknél (és `ADMIN` esetén "Új könyv" / "Törlés" gomb).
* **Kölcsönzéseim / Összes kölcsönzés oldal:** Aktív és lezárt kölcsönzések listája, piros jelvény (`Késedelmes`), ha `overdue === true`, és "Visszahozás" gomb.

---

## 7. Tesztelési követelmények
* **Unit tesztek (JUnit 5 + Mockito):**
  * Sikeres kölcsönzés és visszahozás.
  * Hibaág: 4. könyv kölcsönzésekor kivétel dobása (max. 3-as limit).
  * Hibaág: Már kikölcsönzött könyv újbóli kölcsönzése sikertelen.
  * Késedelmi flag (`overdue`) helyes számítása.
* **Integrációs tesztek (Testcontainers + MockMvc / TestRestTemplate):**
  * Valódi PostgreSQL konténerrel, Flyway migrációk lefutásával.
  * End-to-end folyamat: Login -> JWT lekérés -> Könyv kölcsönzése -> Jogosultság ellenőrzése (`MEMBER` nem hívhat `ADMIN` végpontot -> `403 Forbidden`).

---

## 8. DevOps, Docker és CI/CD

### `backend/Dockerfile`
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### `docker-compose.yml`
```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: library
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d library"]
      interval: 5s
      retries: 10

  api:
    build: ./backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/library
      SPRING_DATASOURCE_USERNAME: app
      SPRING_DATASOURCE_PASSWORD: app
      JWT_SECRET: ${JWT_SECRET:-change-me-in-production-min-256-bit-secret-key}
    depends_on:
      db:
        condition: service_healthy
    ports:
      - "8080:8080"
```
*(Opcionálisan az Angular frontendet is be lehet kötni egy `frontend` service-ként Nginx-szel a 4200-as porton.)*

### `.github/workflows/ci.yml`
```yaml
name: CI
on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: ./backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven
      - run: mvn -B verify
```
*(CD bővítéshez: második job `docker/build-push-action`-nel GitHub Container Registrybe, majd deploy Render/Railway platformra.)*

---

## 9. Megvalósítási ütemterv (Antigravity fázisok)
1. **Fázis 1 (1. nap délelőtt):** Spring Boot projekt inicializálása, JPA entitások, Flyway migrációk (`V1`, `V2`), Repository-k (`@EntityGraph`-fal).
2. **Fázis 2 (1. nap délután):** Service réteg és üzleti szabályok, DTO-k, REST Controllerek, `@ControllerAdvice`, Swagger/OpenAPI konfiguráció.
3. **Fázis 3 (2. nap délelőtt):** Spring Security + JWT auth, szerepkörök (`@PreAuthorize`), Unit tesztek az üzleti szabályokra, 1–2 Testcontainers integrációs teszt.
4. **Fázis 4 (2. nap délután):** Angular MVP felület (login, könyvlista + keresés, kölcsönzés/visszahozás), Docker, GitHub Actions CI, README.

---

## 10. README sablon, Ellenőrzőlista és Interjú fókuszpontok

### README kötelező elemei
1. Egy mondatos leírás + élő demó link + CI badge.
2. Képernyőkép vagy GIF az alkalmazásról.
3. Tech stack felsorolása.
4. Indítás egy paranccsal (`docker compose up --build`).
5. Swagger UI link (`/swagger-ui.html`) és fő végpontok + teszt belépési adatok.
6. Tesztek futtatása (`mvn verify`).
7. **Mit tanultam / architekturális döntések:** Miért DTO az entitás helyett, N+1 elkerülése `@EntityGraph`/`JOIN FETCH`-csel, miért Flyway és nem `ddl-auto=update`.

### Készre jelentés ellenőrzőlista
- [ ] `docker compose up` tiszta gépen elindítja a rendszert.
- [ ] A CI zöld, és a badge a README tetején van.
- [ ] Vannak tesztek az üzleti szabályokra és hibaágakra is (nem csak happy path).
- [ ] Nincs jelszó vagy titkos kulcs a repóban (`.env.example` szerepel).
- [ ] Érthető, konvencionális commit üzenetek (pl. `feat: add loan limit rule`).
- [ ] A Flyway seed adatokkal az alkalmazás azonnal kipróbálható.