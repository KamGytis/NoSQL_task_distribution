# Issue Tracker: backend and databases

A Jira-style issue tracker. This README covers the Spring Boot backend and the databases it runs on. The React frontend is in `frontend/`.

## Stack

| Part | Technology |
|---|---|
| Backend | Java 21+, Spring Boot, Spring Security, Maven |
| Main database | MongoDB |
| Sessions, caching, counters, locks | Redis |
| Analytics | ClickHouse |
| Full-text search | Elasticsearch |
| Local infrastructure | Docker Compose |

## Requirements

- Java 21 or newer (the Maven wrapper is included, no Maven install needed)
- Docker Desktop, running

## Run it

1. Start the databases:

   ```
   cd docker
   docker compose up -d
   docker ps
   ```

   `docker ps` should list four containers: `mongo`, `redis`, `clickhouse` and `elasticsearch`. Elasticsearch takes up to a minute to become ready.

2. Start the backend:

   ```
   cd backend
   .\mvnw spring-boot:run
   ```

   On macOS or Linux use `./mvnw spring-boot:run`. The API is then at `http://localhost:8080/api/v1`.

3. Optional: start the frontend (`cd frontend`, `npm install`, `npm run dev`). It runs on `http://localhost:5173` and proxies `/api` to the backend.

Stop the databases with `docker compose down`. Add `-v` to also delete their data.

## Databases

| Service | Port | Used for |
|---|---|---|
| MongoDB | 27017 | All main data (database `tracker`) |
| Redis | 6379 | Login sessions (`tracker:session:*`), issue numbering (`tracker:issue-counter:<PROJECT KEY>`), caching and locks |
| ClickHouse | 8123, 9000 | Analytics events and reports (user `tracker`, password `tracker`) |
| Elasticsearch | 9200 | Full-text search (security is off for local development) |

The credentials above are for local development only.

### MongoDB collections

| Collection | Contents |
|---|---|
| `users` | Accounts (email is unique, passwords are BCrypt hashes) |
| `projects` | Projects with an embedded member list and roles (unique `key`) |
| `invitations` | Project invitations: `PENDING`, `ACCEPTED` or `DECLINED` |
| `issues` | Issues with a unique key such as `DEMO-7`, status and optimistic locking |
| `comments` | Issue comments with `@email` mentions |
| `worklogs` | Time logged on issues |
| `issue_history` | Append-only audit trail of issue changes |
| `notifications` | Mention and assignment notifications (deleted after 90 days) |

The full model is in [`docs/db-model.md`](docs/db-model.md).

## Configuration

Settings live in `backend/src/main/resources/application.yml`:

- MongoDB: `spring.mongodb.uri` (default `mongodb://localhost:27017/tracker`)
- Redis: `spring.data.redis.host` and `port`
- Sessions: stored in Redis, 30 minute timeout, `HttpOnly` cookie named `SESSION`

## API

- Base path: `/api/v1`
- Authentication: register and log in with `POST /auth/register` and `POST /auth/login`. The server sets a `SESSION` cookie, and every other endpoint requires it.
- Roles per project: `ADMIN`, `MEMBER`, `VIEWER`. A non-member gets 404, and a member with too low a role gets 403.
- Errors: JSON with `status`, `error` and `message`.
- Endpoint list: [`docs/api.md`](docs/api.md)

## Project layout

```
backend/src/main/java/lt/vu/tracker/
├── controller/   REST endpoints (HTTP only, no database access)
├── service/      business logic and permission checks
├── repository/   Spring Data MongoDB repositories
├── model/        MongoDB documents and enums
├── dto/          request and response objects
├── security/     login, sessions, current user
└── config/       security and infrastructure configuration
```

Requests flow `controller → service → repository → database`.

## Tests

From `backend/`:

```
.\mvnw test "-Dtest=*ServiceTest"       # unit tests, nothing else needed
.\mvnw test "-Dtest=*E2ETest"           # end-to-end API tests, needs Docker running
.\mvnw test                             # everything
```

The end-to-end tests start the real application and use a separate `tracker_test` database, so your development data is not touched.

## Troubleshooting

- **`Connection refused` on port 27017, 6379 or 9200:** Docker Desktop is not running, or `docker compose up -d` has not been run.
- **`mvnw` is not recognized in PowerShell:** use `.\mvnw` instead.
- **Every request returns 401:** you are not logged in, or the Redis container is down.
- **Data appears in a database called `test`:** the MongoDB URI is not being applied. Check `spring.mongodb.uri` in `application.yml`.
