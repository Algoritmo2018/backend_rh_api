# Local development data

> **DEVELOPMENT ONLY.** The accounts below and their password exist only in a local database started with the
> `dev` profile. The seed (`dev/seed/R__dev_seed.sql`) is loaded by Flyway only through
> `application-dev.yml` (`filesystem:dev/seed`), is never packaged in the jar and must never be listed in
> `application.yml` or `application-prod.yml` (`DevSeedLocationsTest` fails). Never reuse this password anywhere.

## Start everything (Windows)

1. Docker Desktop running.
2. Backend environment (once): in `recrutamento-api`, copy `.env.example` to `.env` and set at least

   ```env
   MINIO_ROOT_USER=recrutamento
   MINIO_ROOT_PASSWORD=recrutamento-dev
   MINIO_KMS_SECRET_KEY=recrutamento-key:<32 random bytes in base64>
   ```

   (the MinIO user/password must match the `dev` profile defaults, or be exported in the shell too). A key
   can be generated in PowerShell with
   `"recrutamento-key:" + [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`.
   Remove the `POSTGRES_PASSWORD=change-me` line or keep it: Spring Boot reads the database credentials from
   the compose container.
3. Backend: `mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` (starts PostgreSQL, Mailpit and MinIO;
   Flyway applies the migrations and then this seed).
4. Frontend: in `Front_RH`, copy `.env.example` to `.env.local`, `npm install`, `npm run dev` →
   <http://localhost:5173/software_rh/>.
5. Mailpit (invitations, password reset, email confirmation, security alerts): <http://localhost:8025>.
   Swagger: <http://localhost:8080/swagger-ui.html>.
6. SMS codes (two-step verification, SMS login, phone change) are not sent: they are printed in the backend
   log (`[simulated SMS] to +244… : …`).
7. Use Chrome, Edge or Firefox: the `Secure` refresh cookie on `http://localhost` may not work in Safari.

## Seeded accounts (integration part I1)

Password of every account with a password: **`FehDev2026`**

| Email | Role | Notes |
|---|---|---|
| `ana.costa@fehoiram.local` | admin | Main administrator |
| `carlos.neto@fehoiram.local` | admin | Two-step verification on (SMS code in the backend log) |
| `rita.santos@fehoiram.local` | recruiter | |
| `joao.pedro@fehoiram.local` | recruiter | |
| `marta.silva@fehoiram.local` | interviewer | |
| `paulo.dias@fehoiram.local` | interviewer | |
| `nuno.lopes@fehoiram.local` | recruiter | **Deactivated**: login refused |
| `sara.mendes@fehoiram.local` | interviewer | **Pending invitation** (no password): "Reenviar convite" sends a link to Mailpit |
| `helena.bento@petrokwanza.local` | client | Petro Kwanza, S.A. (active) |
| `mario.tavares@logatlantico.local` | client | Logística Atlântico, Lda (active) |
| `teresa.lima@mbanza.local` | client | Construções Mbanza, Lda (**inactive company**: login refused) |
| `luisa.fernandes@exemplo.local` | candidate | Email confirmed, minimal profile |
| `mateus.kiala@exemplo.local` | candidate | Email confirmed, minimal profile |
| `pedro.vaz@exemplo.local` | candidate | Email **not** confirmed (no profile yet) |

Because an administrator with a password exists, the bootstrap administrator (`admin@fehoiram.local`) is not
created on a database that has the seed.

## How the seed behaves

- Repeatable Flyway migration: it runs again only when the file changes. Every insert uses fixed UUIDs and
  `ON CONFLICT DO NOTHING`, so changes made in the browser are kept; to start over, drop the database volume
  (`docker compose down -v`, which also deletes the MinIO files and the Mailpit inbox).
- Only BCrypt hashes are stored in the file. To change the password, generate a new BCrypt hash (cost 12),
  replace it in the seed, and update this README and `DevSeedMigrationTest`.
- `DevSeedMigrationTest` applies all migrations plus the seed on a fresh database and logs in with seeded
  users, so a schema change that breaks the seed fails in `./mvnw verify`.
- Later integration parts extend it: job requests (I3), jobs (I4), candidate profiles (I5), applications (I7),
  interviews and evaluations (I8).
