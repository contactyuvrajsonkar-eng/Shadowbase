# testcontainers-postgres-manager

This is my Week 1 internship project for ShadowBase (Container Engine). It is a Spring Boot application that can start, fill with data, and delete a PostgreSQL database running inside Docker. All of this is done through REST API calls, and the Docker part is handled with the Testcontainers Java API.

I followed the API contract that my team leader wrote (`docs/api-contract.md`), so the URLs and JSON field names match it.

## What the project does

1. I send a request to create a "sandbox". The app starts a PostgreSQL container in Docker and waits until the database is ready.
2. I send a seed request. The app connects to that database and creates two tables (`customers` and `orders`) with some sample rows.
3. I can check the status of the sandbox at any time.
4. I send a delete request. The app stops and removes the container.

## How it is organised

```
Postman
   |
Controller   (receives the request and sends back the response)
   |
Service      (the actual logic)
   |-- Registry  (remembers which container belongs to which sandbox id)
   |-- Seeder    (connects with JDBC and inserts the sample data)
   |
Testcontainers -> Docker -> PostgreSQL container
```

Packages:

- `controller`: the REST endpoints. It only receives requests and calls the service.
- `service`: the main logic, the registry and the seeder.
- `dto`: simple classes for the JSON requests and responses, as written in the contract.
- `exception`: `ApiException` and `GlobalExceptionHandler`, which turns every error into the JSON format from the contract.
- `config`: empty for now.

## Tools and versions

- Java 21
- Spring Boot 4.1.2
- Testcontainers 2.0.5 (the version is chosen by Spring Boot, I did not write it in the pom)
- PostgreSQL image: `postgres:17-alpine`
- Maven
- Docker Desktop on Windows
- Eclipse IDE and Postman

## How to run it

You need JDK 21 and Docker Desktop. Docker must be running, which means it shows "Engine running".

1. Clone the repo and go to the project folder.
2. Start Docker Desktop.
3. Run `mvnw spring-boot:run`, or run `TestcontainersPostgresManagerApplication` from Eclipse.
4. The API is now at `http://localhost:8080/api/v1`.

The first time takes longer because Docker downloads the PostgreSQL image.

To run the tests: `mvnw test`

## Settings

These are in `application.properties`:

- `sandbox.postgres.image`: which PostgreSQL image to use
- `sandbox.postgres.database`: the database name
- `sandbox.postgres.username`: the database user
- `sandbox.max-sandboxes`: how many sandboxes can exist at once (I set it to 3)

I did not put the password in this file. A new random password is made for every container, so there is no password in the code or in Git.

## API

Base URL: `http://localhost:8080/api/v1`

**Create a sandbox** - `POST /sandboxes` - returns `201 Created`

Request:
```json
{ "name": "orders-risk-check", "sourceDatabase": "shadowbase_prod" }
```
Response:
```json
{ "sandboxId": "sbx-001", "name": "orders-risk-check", "status": "CREATED", "createdAt": "2026-11-20T08:21:18Z" }
```
In Docker, a new postgres container starts on a random port.

**Get a sandbox** - `GET /sandboxes/{sandboxId}` - returns `200 OK`

Response:
```json
{ "sandboxId": "sbx-001", "name": "orders-risk-check", "status": "SEEDED", "createdAt": "2026-11-20T08:21:18Z" }
```

**Seed a sandbox** - `POST /sandboxes/{sandboxId}/seed` - returns `200 OK`

Request:
```json
{ "mode": "snapshot" }
```
Response:
```json
{ "sandboxId": "sbx-001", "status": "SEEDED", "rowCounts": { "customers": 2, "orders": 2 } }
```
If I seed again, the numbers stay the same because the tables are cleared first.

**Delete a sandbox** - `DELETE /sandboxes/{sandboxId}` - returns `204 No Content` with an empty body. The container is stopped and removed.

**Error response** (same format for every error):
```json
{
  "timestamp": "2026-11-20T08:45:02Z",
  "status": 404,
  "code": "SANDBOX_NOT_FOUND",
  "message": "Sandbox not found",
  "path": "/api/v1/sandboxes/sbx-999/seed"
}
```

**Apply a migration** - `POST /sandboxes/{sandboxId}/migrations` - returns `200 OK`

Request:
```json
{ "script": "ALTER TABLE public.orders DROP COLUMN status;" }
```
Response:
```json
{ "migrationId": "mig-001", "sandboxId": "sbx-001", "status": "APPLIED", "appliedAt": "2026-11-21T10:00:00Z" }
```
The whole script runs as one transaction, so if one statement fails, nothing is changed. A failed script returns `422` with the code `MIGRATION_FAILED` (I chose 422 myself, the contract does not give a status).

Error codes I used:

- `VALIDATION_ERROR` (400): the request body is wrong or missing
- `SANDBOX_NOT_FOUND` (404): the sandbox id does not exist
- `SANDBOX_NOT_READY` (409): the sandbox is busy or its container is not running (I chose 409 myself, the contract does not say)
- `SANDBOX_LIMIT_REACHED` (409): too many sandboxes (this code is my own idea and is not in the contract)
- `INTERNAL_ERROR` (500): Docker or database problem

## How I checked the database

After seeding, I looked inside the real container:

```
docker ps
docker exec -it <container id> psql -U sandbox_user -d sandbox_db -c "SELECT * FROM public.orders;"
```

## Tests

`SandboxSeederIntegrationTest` starts a real PostgreSQL container with Testcontainers and checks that:

- seeding creates the tables and inserts 2 customers and 2 orders
- seeding twice does not duplicate the rows

In the test, Testcontainers starts and removes the container by itself. In the application, my own code does it when a request comes in. That is the main difference.

## What happens if the app stops suddenly

The registry only lives in memory, so it is lost. But Testcontainers also starts a small container called Ryuk. When the app disappears, Ryuk removes the leftover containers. I saw this myself: after I stopped the app, `docker ps` was empty.

## Limitations

This is a learning project, not something to put on a public server.

- There is no login, so anyone who can reach the API can start containers.
- A program that controls Docker has a lot of power over the computer it runs on, so this is risky if misused.
- The container ports are open on all network interfaces and protected only by the random password.
- The registry is in memory, so it only works with one running copy of the app.
- The migration endpoint runs any SQL it receives. That is only safe because it runs inside the throwaway sandbox container and never touches a production database.

## Problems I faced

- Docker said "daemon not running" at first. I had to wait for Docker Desktop to show "Engine running".
- Eclipse wanted compiler level 25 but I have JDK 21, so I set it to 21 in the settings.
- The app did not start because Spring tried to connect to a database automatically. I turned that off in `application.properties` because my code creates the database later.
- My test file was in `src/main/java` and showed 14 errors. The test libraries are only available in `src/test/java`, so I moved it.
- Many online examples use the old Testcontainers 1.x package names. With Testcontainers 2.x the class is `org.testcontainers.postgresql.PostgreSQLContainer`.

## What is not done yet

Replay, migrations, metrics, SQL errors and live updates from the contract are not implemented yet.

## Seeding in this version

`sourceDatabase` is accepted but not used yet. This version seeds a fixed sample dataset (2 customers and 2 orders). Cloning a snapshot of the production database is planned as a later feature.

## Things to confirm with my team leader

- The contract example shows 3 orders, but the schema file in the repo inserts 2.
- The HTTP status for `SANDBOX_NOT_READY`, and whether `SANDBOX_LIMIT_REACHED` is okay.