-- Teszt felhasznalok (BCryptPasswordEncoder-rel generalt hash-ek):
--   admin@library.com   -> Admin123!
--   member@library.com  -> Member123!
--   member2@library.com -> Member123!

INSERT INTO users (email, password, full_name, role, created_at) VALUES
('admin@library.com', '$2a$10$7mF8PDqM80Uqn5vPtZc2Z.EuZHW2HEdDHUia5bwzvBHnavIR9LiGe', 'Adminisztrátor', 'ADMIN', NOW() - INTERVAL '60 days'),
('member@library.com', '$2a$10$uhvBSkHcqKKXsuJ6qDmfD.QQk.Tu0Vla32Qj5Cq/SVTpr.F9GHXaG', 'Kovács Anna', 'MEMBER', NOW() - INTERVAL '30 days'),
('member2@library.com', '$2a$10$/WnzDCtjOLTfBXGOzubZGOYKLKlp859mkK93jiB4X91XCt0KOKgcm', 'Nagy Péter', 'MEMBER', NOW() - INTERVAL '25 days');

INSERT INTO books (title, author, isbn, published_year, available, version) VALUES
('Clean Code', 'Robert C. Martin', '978-0132350884', 2008, TRUE, 0),
('Effective Java', 'Joshua Bloch', '978-0134685991', 2018, TRUE, 0),
('Spring in Action', 'Craig Walls', '978-1617297571', 2022, TRUE, 0),
('Domain-Driven Design', 'Eric Evans', '978-0321125217', 2003, TRUE, 0),
('Designing Data-Intensive Applications', 'Martin Kleppmann', '978-1449373320', 2017, TRUE, 0),
('Refactoring', 'Martin Fowler', '978-0134757599', 2018, TRUE, 0),
('Java Concurrency in Practice', 'Brian Goetz', '978-0321349606', 2006, TRUE, 0),
('Head First Design Patterns', 'Eric Freeman', '978-1492078005', 2020, TRUE, 0),
('The Pragmatic Programmer', 'David Thomas', '978-0135957059', 2019, TRUE, 0),
('Patterns of Enterprise Application Architecture', 'Martin Fowler', '978-0321127426', 2002, TRUE, 0),
('Microservices Patterns', 'Chris Richardson', '978-1617294549', 2018, FALSE, 0),
('Release It!', 'Michael T. Nygard', '978-1680502398', 2018, FALSE, 0);

-- 1. Aktiv, hataridon beluli kolcsonzes (Microservices Patterns -> Kovács Anna)
-- 2. Aktiv, LEJART kolcsonzes a kesedelmi jelzes tesztelesehez (Release It! -> Kovács Anna)
-- 3. Lezart, mar visszahozott kolcsonzes (Clean Code -> Nagy Péter)
INSERT INTO loans (book_id, user_id, borrowed_at, due_date, returned_at) VALUES
(11, 2, NOW() - INTERVAL '3 days', NOW() + INTERVAL '11 days', NULL),
(12, 2, NOW() - INTERVAL '20 days', NOW() - INTERVAL '6 days', NULL),
(1, 3, NOW() - INTERVAL '18 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '5 days');
