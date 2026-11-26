-- =====================================================================================================
-- DEVELOPMENT DATA ONLY. Loaded by Flyway only with the "dev" profile (filesystem:dev/seed in
-- application-dev.yml); never packaged in the jar and never listed in application.yml / application-prod.yml
-- (DevSeedLocationsTest fails otherwise).
--
-- Repeatable migration: Flyway runs it again whenever this file changes. Every insert is idempotent
-- (fixed UUIDs, ON CONFLICT DO NOTHING), so rows changed by hand in the browser are kept.
-- The password of every seeded account is in dev/README.md (development only). Only its BCrypt hash is here.
--
-- Integration part I1: users of every role, client companies, candidate accounts. Later parts add job
-- requests (I3), jobs (I4), candidate profiles (I5), applications (I7), interviews and evaluations (I8).
-- Fixed ids: d0000000-0000-4000-8000-000000000<kind><n> (1xx users, 2xx clients, 3xx contacts,
-- 4xx candidates, 5xx candidate languages).
-- =====================================================================================================

-- ---------- Client companies (2 active + 1 inactive) ----------
INSERT INTO clients (id, name, sector, nif, province, city, address, website, status, notes, created_at, updated_at)
VALUES
    ('d0000000-0000-4000-8000-000000000201', 'Petro Kwanza, S.A.', 'OIL_GAS', '5417000201', 'LUANDA', 'Luanda',
     'Rua Major Kanhangulo, Ingombota', 'https://petrokwanza.example', 'ACTIVE', 'Cliente de demonstração (dev).', now(), now()),
    ('d0000000-0000-4000-8000-000000000202', 'Logística Atlântico, Lda', 'LOGISTICS', '5417000202', 'BENGUELA', 'Lobito',
     'Avenida da Independência, Lobito', NULL, 'ACTIVE', NULL, now(), now()),
    ('d0000000-0000-4000-8000-000000000203', 'Construções Mbanza, Lda', 'CONSTRUCTION', '5417000203', 'ZAIRE', 'Mbanza Kongo',
     'Bairro Central, Mbanza Kongo', NULL, 'INACTIVE', 'Empresa desativada (dev): os seus utilizadores não entram.', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO client_contacts (id, client_id, name, role, email, phone, is_primary, created_at, updated_at)
VALUES
    ('d0000000-0000-4000-8000-000000000301', 'd0000000-0000-4000-8000-000000000201', 'Helena Bento', 'Diretora de RH',
     'helena.bento@petrokwanza.local', '+244923100111', TRUE, now(), now()),
    ('d0000000-0000-4000-8000-000000000302', 'd0000000-0000-4000-8000-000000000202', 'Mário Tavares', 'Gestor de Operações',
     'mario.tavares@logatlantico.local', '+244923100112', TRUE, now(), now()),
    ('d0000000-0000-4000-8000-000000000303', 'd0000000-0000-4000-8000-000000000203', 'Teresa Lima', 'Administradora',
     'teresa.lima@mbanza.local', '+244923100113', TRUE, now(), now())
ON CONFLICT DO NOTHING;

-- ---------- Users ----------
-- password_hash NULL = invitation not accepted yet. email_verified_at set = the email was confirmed (staff
-- confirm it by accepting the invitation; candidates by the link sent at registration).
INSERT INTO users (id, name, email, phone, role, job_title, client_id, password_hash, email_verified_at,
                   terms_accepted_at, two_factor_enabled, active, created_at, updated_at)
VALUES
    -- Administrators (one with two-step verification: the SMS code is printed in the backend log)
    ('d0000000-0000-4000-8000-000000000101', 'Ana Costa', 'ana.costa@fehoiram.local', '+244923100101', 'ADMIN',
     'Diretora de Recrutamento', NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, FALSE, TRUE, now(), now()),
    ('d0000000-0000-4000-8000-000000000102', 'Carlos Neto', 'carlos.neto@fehoiram.local', '+244923100102', 'ADMIN',
     'Administrador de Sistemas', NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, TRUE, TRUE, now(), now()),
    -- Recruiters
    ('d0000000-0000-4000-8000-000000000103', 'Rita Santos', 'rita.santos@fehoiram.local', '+244923100103', 'RECRUITER',
     'Recrutadora Sénior', NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, FALSE, TRUE, now(), now()),
    ('d0000000-0000-4000-8000-000000000104', 'João Pedro', 'joao.pedro@fehoiram.local', '+244923100104', 'RECRUITER',
     'Recrutador', NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, FALSE, TRUE, now(), now()),
    -- Interviewers
    ('d0000000-0000-4000-8000-000000000105', 'Marta Silva', 'marta.silva@fehoiram.local', '+244923100105', 'INTERVIEWER',
     'Engenheira de Petróleos', NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, FALSE, TRUE, now(), now()),
    ('d0000000-0000-4000-8000-000000000106', 'Paulo Dias', 'paulo.dias@fehoiram.local', '+244923100106', 'INTERVIEWER',
     'Chefe de Logística', NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, FALSE, TRUE, now(), now()),
    -- Deactivated user (cannot log in)
    ('d0000000-0000-4000-8000-000000000107', 'Nuno Lopes', 'nuno.lopes@fehoiram.local', '+244923100107', 'RECRUITER',
     'Recrutador', NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, FALSE, FALSE, now(), now()),
    -- Pending invitation (no password: "Reenviar convite" sends a new link to Mailpit)
    ('d0000000-0000-4000-8000-000000000108', 'Sara Mendes', 'sara.mendes@fehoiram.local', '+244923100108', 'INTERVIEWER',
     'Técnica de HSE', NULL, NULL, NULL, NULL, FALSE, TRUE, now(), now()),
    -- Client-portal users (the last one belongs to the inactive company: login refused)
    ('d0000000-0000-4000-8000-000000000111', 'Helena Bento', 'helena.bento@petrokwanza.local', '+244923100111', 'CLIENT',
     'Diretora de RH', 'd0000000-0000-4000-8000-000000000201', '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, FALSE, TRUE, now(), now()),
    ('d0000000-0000-4000-8000-000000000112', 'Mário Tavares', 'mario.tavares@logatlantico.local', '+244923100112', 'CLIENT',
     'Gestor de Operações', 'd0000000-0000-4000-8000-000000000202', '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, FALSE, TRUE, now(), now()),
    ('d0000000-0000-4000-8000-000000000113', 'Teresa Lima', 'teresa.lima@mbanza.local', '+244923100113', 'CLIENT',
     'Administradora', 'd0000000-0000-4000-8000-000000000203', '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), NULL, FALSE, TRUE, now(), now()),
    -- Candidates: two with a confirmed email (profile below), one still unconfirmed (no profile yet)
    ('d0000000-0000-4000-8000-000000000121', 'Luísa Fernandes', 'luisa.fernandes@exemplo.local', '+244923100121', 'CANDIDATE',
     NULL, NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), now(), FALSE, TRUE, now(), now()),
    ('d0000000-0000-4000-8000-000000000122', 'Mateus Kiala', 'mateus.kiala@exemplo.local', '+244923100122', 'CANDIDATE',
     NULL, NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', now(), now(), FALSE, TRUE, now(), now()),
    ('d0000000-0000-4000-8000-000000000123', 'Pedro Vaz', 'pedro.vaz@exemplo.local', '+244923100123', 'CANDIDATE',
     NULL, NULL, '{bcrypt}$2a$12$RYzdDmyGAZGEGfHkPXgE5.9pxbXVgH7FcEwcb6uBlBukehbqVHhTC', NULL, now(), FALSE, TRUE, now(), now())
ON CONFLICT DO NOTHING;

-- ---------- Candidate profiles of the confirmed candidates (minimal: completed in I5) ----------
INSERT INTO candidates (id, user_id, full_name, email, phone, nationality, province, availability, source, created_at, updated_at)
VALUES
    ('d0000000-0000-4000-8000-000000000401', 'd0000000-0000-4000-8000-000000000121', 'Luísa Fernandes',
     'luisa.fernandes@exemplo.local', '+244923100121', 'Angolana', 'LUANDA', 'IMMEDIATE', 'PORTAL', now(), now()),
    ('d0000000-0000-4000-8000-000000000402', 'd0000000-0000-4000-8000-000000000122', 'Mateus Kiala',
     'mateus.kiala@exemplo.local', '+244923100122', 'Angolana', 'BENGUELA', 'DAYS_30', 'PORTAL', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO candidate_languages (id, candidate_id, position, language, language_key, level, created_at, updated_at)
VALUES
    ('d0000000-0000-4000-8000-000000000501', 'd0000000-0000-4000-8000-000000000401', 0, 'Português', 'portugues', 'NATIVE', now(), now()),
    ('d0000000-0000-4000-8000-000000000502', 'd0000000-0000-4000-8000-000000000402', 0, 'Português', 'portugues', 'NATIVE', now(), now())
ON CONFLICT DO NOTHING;

-- Link the accounts to their profiles (only when the profile row above is the one that exists)
UPDATE users u
SET candidate_id = c.id
FROM candidates c
WHERE c.user_id = u.id
  AND u.id IN ('d0000000-0000-4000-8000-000000000121', 'd0000000-0000-4000-8000-000000000122')
  AND u.candidate_id IS NULL;
