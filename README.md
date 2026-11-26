# Könyvtár- és kölcsönzéskezelő (Spring Boot + Angular)

[![CI](https://github.com/Laczko77/konyvtar/actions/workflows/ci.yml/badge.svg)](https://github.com/Laczko77/konyvtar/actions/workflows/ci.yml)

Modern, konténerizált könyvtár- és kölcsönzéskezelő webalkalmazás **Java 21 / Spring Boot 3**, **PostgreSQL 16 + Flyway**, **Spring Security + JWT** és **Angular 22** alapokon — szigorú üzleti szabályokkal, DTO-alapú rétegzéssel és automatizált unit + Testcontainers integrációs tesztekkel.

---

## Tartalomjegyzék

- [Architektúra áttekintése](#architektúra-áttekintése)
- [Technológiai stack](#technológiai-stack)
- [Indítás egy paranccsal (Docker Compose)](#indítás-egy-paranccsal-docker-compose)
- [Környezeti változók](#környezeti-változók)
- [Teszt belépési adatok (Flyway seed)](#teszt-belépési-adatok-flyway-seed)
- [API dokumentáció és fő végpontok](#api-dokumentáció-és-fő-végpontok)
- [Üzleti szabályok](#üzleti-szabályok)
- [Tesztek futtatása](#tesztek-futtatása)
- [Mit tanultam / Architekturális döntések](#mit-tanultam--architekturális-döntések)

---

## Architektúra áttekintése

```mermaid
flowchart LR
    Browser["Böngésző (http://localhost:4200)"] --> Nginx["Frontend Konténer (Angular + Nginx)"]
    Nginx -->|"/api/* reverse proxy"| API["Backend Konténer (Spring Boot 3 + JWT)"]
    API -->|"Spring Data JPA + Flyway"| DB[("PostgreSQL 16")]
```

### Fő funkciók
- **Hitelesítés és szerepkörök (`ADMIN` / `MEMBER`):** Regisztráció, JWT-alapú stateless belépés, szerepkörhöz kötött API és UI jogosultságok.
- **Könyvkatalógus:** Keresés cím és szerző szerint, elérhetőség szűrés, szerveroldali lapozás (`Pageable`), valamint `ADMIN` CRUD műveletek (létrehozás, szerkesztés, törlés).
- **Kölcsönzéskezelés:** Könyv kölcsönzése és visszavétele, késedelmes kölcsönzések jelölése (`overdue`), valamint `ADMIN` nézet az összes kölcsönzésre és tagra.

---

## Technológiai stack

| Réteg | Technológiák |
| --- | --- |
| **Backend** | Java 21, Spring Boot 3.4.3, Spring Web, Spring Data JPA (Hibernate), Spring Validation |
| **Adatbázis & Migráció** | PostgreSQL 16, Flyway (`ddl-auto: validate`) |
| **Biztonság** | Spring Security (stateless), JWT (`jjwt` 0.12.6, HMAC-SHA256), BCrypt |
| **API Dokumentáció** | Springdoc OpenAPI / Swagger UI (`2.8.5`) |
| **Tesztelés (Backend)** | JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL 16) |
| **Frontend** | Angular 22 (standalone komponensek, Router, HttpClient, funkcionális interceptor és guardok), Vitest |
| **DevOps & CI/CD** | Docker (multi-stage build), Nginx reverse proxy, Docker Compose, GitHub Actions |

---

## Indítás egy paranccsal (Docker Compose)

**Előfeltétel:** Docker és Docker Compose telepítve van a gépen.

1. (Opcionális) Másold le a környezeti változók sablonját:
   ```bash
   cp .env.example .env
   ```
2. Indítsd el a teljes rendszert (`db` + `api` + `frontend`):
   ```bash
   docker compose up --build
   ```

Elérhető szolgáltatások indulás után:
- **Frontend (Angular + Nginx):** http://localhost:4200
- **Backend REST API:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html

Leállításhoz és a tesztadatbázis teljes alaphelyzetbe állításához:
```bash
docker compose down -v
```

---

## Környezeti változók

A [.env.example](./.env.example) tartalmazza az összes konfigurálható paramétert (érzékeny adatok és titkos kulcsok nélkül a repóban):

| Változó | Alapértelmezett érték (`docker-compose.yml` / `application.yml`) | Leírás |
| --- | --- | --- |
| `POSTGRES_DB` | `library` | PostgreSQL adatbázis neve |
| `POSTGRES_USER` | `app` | Adatbázis felhasználó |
| `POSTGRES_PASSWORD` | `app` | Adatbázis jelszó |
| `JWT_SECRET` | `change-me-in-production-min-256-bit-secret-key` | Min. 256 bites titkos kulcs JWT aláíráshoz |
| `JWT_EXPIRATION_MS` | `86400000` | Token lejárati idő ezredmásodpercben (24 óra) |

---

## Teszt belépési adatok (Flyway seed)

Az alkalmazás első indulásakor a [V2__seed_data.sql](./backend/src/main/resources/db/migration/V2__seed_data.sql) automatikusan betölt 3 felhasználót, 12 könyvet és 3 minta kölcsönzést (köztük egy lejárt határidejű, késedelmes kölcsönzést is):

| Szerepkör | Email | Jelszó | Megjegyzés |
| --- | --- | --- | --- |
| `ADMIN` | `admin@library.com` | `Admin123!` | Teljes CRUD, összes kölcsönzés és tagok listája |
| `MEMBER` | `member@library.com` | `Member123!` | Kovács Anna — 1 aktív és 1 lejárt (késedelmes) kölcsönzéssel indul |
| `MEMBER` | `member2@library.com` | `Member123!` | Nagy Péter — 1 lezárt (visszahozott) kölcsönzéssel indul |

---

## API dokumentáció és fő végpontok

A teljes interaktív API dokumentáció elérhető a **/swagger-ui.html** útvonalon (beépített JWT Bearer hitelesítéssel — a `/api/auth/login` válaszából kapott `token` az `Authorize` gombbal megadható).

| Metódus | Végpont | Jogosultság | Leírás |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Nyilvános | Új `MEMBER` felhasználó regisztrálása |
| `POST` | `/api/auth/login` | Nyilvános | Bejelentkezés és JWT token igénylése |
| `GET` | `/api/books` | Hitelesített | Könyvek listázása szűréssel (`title`, `author`, `available`) és lapozással (`page`, `size`) |
| `GET` | `/api/books/{id}` | Hitelesített | Könyv adatainak lekérdezése |
| `POST` | `/api/books` | `ADMIN` | Új könyv felvitele (ISBN egyediség ellenőrzéssel) |
| `PUT` | `/api/books/{id}` | `ADMIN` | Könyv adatainak módosítása |
| `DELETE` | `/api/books/{id}` | `ADMIN` | Könyv törlése |
| `GET` | `/api/members` | `ADMIN` | Összes tag listázása |
| `GET` | `/api/members/me` | Hitelesített | Bejelentkezett felhasználó adatai |
| `POST` | `/api/loans` | Hitelesített | Könyv kölcsönzése (`{ "bookId": 1 }`) |
| `PATCH` | `/api/loans/{id}/return` | Hitelesített | Kölcsönzés visszahozása (`ADMIN` bármelyiket, `MEMBER` csak a sajátját) |
| `GET` | `/api/loans/my` | Hitelesített | Saját kölcsönzések listázása |
| `GET` | `/api/loans` | `ADMIN` | Összes kölcsönzés listázása (opcionális `overdue=true`/`false` szűrővel) |

---

## Üzleti szabályok

- **Max. 3 aktív kölcsönzés:** Egy tagnak egyszerre legfeljebb 3 aktív (`returned_at IS NULL`) kölcsönzése lehet; a 4. kísérlet `409 Conflict` (`LoanLimitExceededException`) hibát ad.
- **Dupla kiadás tilalma:** Kikölcsönzött könyv (`available == false` vagy aktív `Loan` rekord) nem kölcsönözhető ki újra (`409 Conflict` / `BookNotAvailableException`).
- **14 napos határidő és késedelem:** Új kölcsönzéskor `due_date = borrowed_at + 14 nap`. A `LoanResponse` számított `overdue` mezője `true`, ha `returnedAt == null` és `now > dueDate`.
- **Visszavétel jogosultság:** `MEMBER` csak saját kölcsönzést hozhat vissza (`403 Forbidden` más kölcsönzésénél), már visszahozott kölcsönzés nem vehető vissza újra (`409 Conflict` / `LoanAlreadyReturnedException`).

---

## Tesztek futtatása

### Backend (Unit + Testcontainers PostgreSQL integrációs tesztek)
*(A Testcontainers integrációs tesztek futtatásához működő Docker daemon szükséges.)*

```bash
cd backend
mvn -B verify
```

Csak a Mockito-alapú unit tesztek futtatása (Docker nélkül):
```bash
cd backend
mvn -B test -Dtest="*ServiceTest,JwtServiceTest"
```

### Frontend (Unit tesztek és production build)
```bash
cd frontend
npm test -- --watch=false
npm run build
```

---

## Mit tanultam / Architekturális döntések

1. **DTO `record`-ok vs. JPA entitások visszaadása:**
   Egyetlen REST controller sem ad vissza JPA entitást; minden bemenet és kimenet Java `record` DTO-n (`BookRequest`, `BookResponse`, `LoanResponse`, `AuthResponse`, `MemberResponse`) keresztül történik. Így az API szerződés független marad az adatbázis-modelltől, elkerülhető a véletlen `LazyInitializationException` vagy JSON körkörös hivatkozás, és a számított mezők (pl. `LoanResponse.overdue`) tisztán, a prezentációs réteg szempontjából képezhetők le.

2. **N+1 lekérdezési probléma elkerülése `@EntityGraph`-fal:**
   A `Loan` entitáson a `book` és `user` kapcsolatok `FetchType.LAZY` beállításúak, az `application.yml`-ben pedig `spring.jpa.open-in-view: false` van érvényben. A `LoanRepository` lekérdezései (`findByUserIdOrderByBorrowedAtDesc`, `findAllByOrderByBorrowedAtDesc`, `findOverdueLoans`, `findNonOverdueLoans`, `findWithDetailsById`) `@EntityGraph(attributePaths = {"book", "user"})` annotációt használnak, így a kölcsönzéslisták egyetlen `JOIN` lekérdezéssel töltődnek be N+1 probléma nélkül.

3. **Flyway vs. `spring.jpa.hibernate.ddl-auto=update`:**
   Az alkalmazásban szigorúan `spring.jpa.hibernate.ddl-auto: validate` fut, és minden tábladefiníció (`V1__init_schema.sql`) valamint mintaadat (`V2__seed_data.sql`) Flyway migráción keresztül kerül az adatbázisba. A Hibernate `ddl-auto=update` éles környezetben kiszámíthatatlan sémamódosításokat okozhat (és nem kezeli a törléseket vagy adat-migrációkat), míg a Flyway verziózott, auditálható és tesztben (Testcontainers) is pontosan ugyanúgy ellenőrizhető.

4. **Optimista zárolás (`@Version`) versenyhelyzetek ellen:**
   A `Book` entitáson található `@Version` mező megakadályozza a párhuzamos módosításokból adódó „lost update” hibákat (pl. ha két felhasználó egy időben próbálja kikölcsönözni ugyanazt a könyvet). Ütközés esetén a Hibernate `OptimisticLockingFailureException`-t dob, amelyet a `GlobalExceptionHandler` egységes `409 Conflict` válasszá alakít.
