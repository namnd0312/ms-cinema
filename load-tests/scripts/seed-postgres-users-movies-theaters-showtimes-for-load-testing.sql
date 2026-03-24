-- =============================================================================
-- seed-test-data.sql
-- Seeds load test data into ms-cinema databases.
-- Re-runnable: uses ON CONFLICT DO NOTHING / IF NOT EXISTS guards.
-- BCrypt hash below is for password "TestPass123!" (strength=10)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- testdb (auth-service)
-- ---------------------------------------------------------------------------
\connect testdb

-- Ensure ROLE_USER exists (id=1)
INSERT INTO roles (id, name)
VALUES (1, 'ROLE_USER')
ON CONFLICT (id) DO NOTHING;

-- Insert 1000 load-test users
-- BCrypt("TestPass123!") = $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
DO $$
DECLARE
    i INTEGER;
    uid BIGINT;
    uname TEXT;
    uemail TEXT;
    bhash TEXT := '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
BEGIN
    FOR i IN 1..1000 LOOP
        uname  := 'loadtest_' || LPAD(i::TEXT, 3, '0');
        uemail := uname || '@test.com';

        INSERT INTO users (username, password, full_name, email, active, created_at, updated_at)
        VALUES (uname, bhash, 'Load Test User ' || i, uemail, true, NOW(), NOW())
        ON CONFLICT (email) DO NOTHING;

        -- Map user → ROLE_USER
        SELECT id INTO uid FROM users WHERE email = uemail;
        IF uid IS NOT NULL THEN
            INSERT INTO user_roles (user_id, role_id)
            VALUES (uid, 1)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- moviedb (movie-service)
-- ---------------------------------------------------------------------------
\connect moviedb

-- 10 movies
INSERT INTO movies (id, title, description, genre, duration, release_date, poster_url, created_at, updated_at)
VALUES
  (1,  'Inception',          'A thief who steals secrets through dreams.',          'Sci-Fi',   148, '2010-07-16', 'https://example.com/posters/inception.jpg',          NOW(), NOW()),
  (2,  'The Dark Knight',    'Batman fights the Joker in Gotham City.',             'Action',   152, '2008-07-18', 'https://example.com/posters/dark-knight.jpg',         NOW(), NOW()),
  (3,  'Interstellar',       'Astronauts travel through a wormhole.',               'Sci-Fi',   169, '2014-11-07', 'https://example.com/posters/interstellar.jpg',        NOW(), NOW()),
  (4,  'The Avengers',       'Earth's mightiest heroes unite.',                    'Action',   143, '2012-05-04', 'https://example.com/posters/avengers.jpg',            NOW(), NOW()),
  (5,  'Parasite',           'Class struggle in South Korea.',                      'Drama',    132, '2019-10-05', 'https://example.com/posters/parasite.jpg',            NOW(), NOW()),
  (6,  'Joker',              'The origin of the Clown Prince of Crime.',            'Drama',    122, '2019-10-04', 'https://example.com/posters/joker.jpg',               NOW(), NOW()),
  (7,  'Dune',               'A noble family fights for a desert planet.',          'Sci-Fi',   155, '2021-10-22', 'https://example.com/posters/dune.jpg',                NOW(), NOW()),
  (8,  'No Time to Die',     'James Bond faces a new global threat.',               'Action',   163, '2021-10-08', 'https://example.com/posters/no-time-to-die.jpg',      NOW(), NOW()),
  (9,  'Everything Everywhere All at Once', 'A laundromat owner battles the multiverse.', 'Sci-Fi', 139, '2022-03-25', 'https://example.com/posters/eeaao.jpg',   NOW(), NOW()),
  (10, 'Oppenheimer',        'The story of the atomic bomb's creator.',             'Drama',    180, '2023-07-21', 'https://example.com/posters/oppenheimer.jpg',         NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Reset sequence to avoid collision on future inserts
SELECT setval(pg_get_serial_sequence('movies', 'id'), GREATEST(10, (SELECT MAX(id) FROM movies)));

-- 3 theaters
INSERT INTO theaters (id, name, total_seats, created_at, updated_at)
VALUES
  (1, 'Theater Alpha',   50, NOW(), NOW()),
  (2, 'Theater Beta',    50, NOW(), NOW()),
  (3, 'Theater Gamma',   50, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('theaters', 'id'), GREATEST(3, (SELECT MAX(id) FROM theaters)));

-- Seats A1-E10 (50 seats per theater = 5 rows × 10 cols)
DO $$
DECLARE
    t INTEGER;
    r TEXT;
    c INTEGER;
    rows TEXT[] := ARRAY['A','B','C','D','E'];
BEGIN
    FOR t IN 1..3 LOOP
        FOREACH r IN ARRAY rows LOOP
            FOR c IN 1..10 LOOP
                INSERT INTO seats (theater_id, row_label, seat_number, created_at, updated_at)
                VALUES (t, r, c, NOW(), NOW())
                ON CONFLICT DO NOTHING;
            END LOOP;
        END LOOP;
    END LOOP;
END $$;

-- 15 showtimes (5 per theater, spread across movies, price = 100000 VND)
INSERT INTO showtimes (id, movie_id, theater_id, start_time, end_time, price, available_seats, created_at, updated_at)
VALUES
  (1,  1, 1, NOW() + INTERVAL '1 day',           NOW() + INTERVAL '1 day 2 hours 30 min', 100000, 50, NOW(), NOW()),
  (2,  2, 1, NOW() + INTERVAL '1 day 3 hours',   NOW() + INTERVAL '1 day 5 hours 30 min', 100000, 50, NOW(), NOW()),
  (3,  3, 1, NOW() + INTERVAL '1 day 6 hours',   NOW() + INTERVAL '1 day 8 hours 50 min', 100000, 50, NOW(), NOW()),
  (4,  4, 1, NOW() + INTERVAL '2 days',           NOW() + INTERVAL '2 days 2 hours 25 min',100000, 50, NOW(), NOW()),
  (5,  5, 1, NOW() + INTERVAL '2 days 3 hours',  NOW() + INTERVAL '2 days 5 hours 15 min',100000, 50, NOW(), NOW()),
  (6,  1, 2, NOW() + INTERVAL '1 day',           NOW() + INTERVAL '1 day 2 hours 30 min', 100000, 50, NOW(), NOW()),
  (7,  6, 2, NOW() + INTERVAL '1 day 3 hours',   NOW() + INTERVAL '1 day 5 hours 5 min',  100000, 50, NOW(), NOW()),
  (8,  7, 2, NOW() + INTERVAL '1 day 6 hours',   NOW() + INTERVAL '1 day 8 hours 40 min', 100000, 50, NOW(), NOW()),
  (9,  8, 2, NOW() + INTERVAL '2 days',           NOW() + INTERVAL '2 days 2 hours 45 min',100000, 50, NOW(), NOW()),
  (10, 9, 2, NOW() + INTERVAL '2 days 3 hours',  NOW() + INTERVAL '2 days 5 hours 20 min',100000, 50, NOW(), NOW()),
  (11, 2, 3, NOW() + INTERVAL '1 day',           NOW() + INTERVAL '1 day 2 hours 35 min', 100000, 50, NOW(), NOW()),
  (12, 3, 3, NOW() + INTERVAL '1 day 3 hours',   NOW() + INTERVAL '1 day 5 hours 55 min', 100000, 50, NOW(), NOW()),
  (13, 4, 3, NOW() + INTERVAL '1 day 6 hours',   NOW() + INTERVAL '1 day 8 hours 25 min', 100000, 50, NOW(), NOW()),
  (14, 10,3, NOW() + INTERVAL '2 days',           NOW() + INTERVAL '2 days 3 hours',       100000, 50, NOW(), NOW()),
  (15, 5, 3, NOW() + INTERVAL '2 days 3 hours',  NOW() + INTERVAL '2 days 5 hours 15 min',100000, 50, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('showtimes', 'id'), GREATEST(15, (SELECT MAX(id) FROM showtimes)));

-- ---------------------------------------------------------------------------
-- Done
-- ---------------------------------------------------------------------------
\echo 'Seed complete: 1000 users, 10 movies, 3 theaters (150 seats), 15 showtimes'
