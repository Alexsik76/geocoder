# Geocoder Service

A Spring Boot-based geocoding backend service that resolves address search queries into coordinates (latitude and longitude). It uses a tiered lookup strategy to optimize response times and manage third-party API usage: Redis cache → PostgreSQL database → Google Geocoding API, persisting new results to the database and caching them.

The resolution workflow is as follows:
1. **Cache Lookup**: Check if the coordinates for the normalized address are already stored in the Redis cache.
2. **Database Lookup**: If not cached, query the PostgreSQL database. If found, cache the result.
3. **Google Geocoding API**: If not in the database, fetch the coordinates from Google's API, persist the new record to the PostgreSQL database, and cache the result.

The service is built to degrade gracefully rather than fail outright:
- If **Redis is unreachable**, cache reads/writes are caught and logged; requests are served straight from the database/Google instead of returning an error.
- If **Google's API times out or the connection drops** (a transient I/O failure), the call is retried up to 3 times before giving up and reporting the address as not found.
- If **Google returns an error status** (invalid request, quota, 4xx/5xx), it is *not* retried — quota isn't wasted on requests that will keep failing the same way — and the address is reported as not found.
- A **blank address** is rejected before it ever reaches Google, both from the REST API (`400 Bad Request`) and the web form (an inline message instead of a misleading "not found").

## Requirements

- **Java**: JDK 21
- **Docker & Docker Compose**: Used for running the database (PostgreSQL) and cache (Redis) services.

## Setup & Run

1. **Configure Environment Variables**:
   Copy the example environment configuration file to create your active environment file:
   ```bash
   cp .env.example .env
   ```
   Open the `.env` file and set your actual Google Maps API key in the `GOOGLE_API_KEY` variable:
   ```env
   GOOGLE_API_KEY=your_real_google_api_key_here
   ```

2. **Start Infrastructure Services**:
   Launch PostgreSQL (mapped to port `5437`) and Redis (mapped to port `6379`) using Docker Compose:
   ```bash
   docker compose up -d
   ```
   Database schema and a handful of sample addresses (Kyiv, Lviv, Vinnytsia) are created automatically by a Flyway migration on startup.

3. **Start the Application**:
   Run the Spring Boot application using the Maven wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```

## API Reference

### Geocode Address

Retrieve coordinates for a specific address.

- **URL**: `/api/geocode`
- **Method**: `GET`
- **Query Parameter**: `address` (required, must not be blank) — The address to look up.

| Response | When |
|---|---|
| `200 OK` with JSON body | Address resolved (from cache, database, or Google) |
| `404 Not Found` | Address could not be resolved anywhere |
| `400 Bad Request` | `address` is missing or blank |

#### Example Request
```bash
curl "http://localhost:8080/api/geocode?address=Kyiv"
```

#### Example Response (JSON)
```json
{
  "address": "Kyiv",
  "latitude": 50.4501,
  "longitude": 30.5234,
  "source": "google"
}
```
*Note: The `source` field indicates where the result was resolved from (`cache`, `database`, or `google`).*

## Web UI

The service features a simple interactive web user interface powered by Thymeleaf and HTMX. You can access it at:
- **URL**: http://localhost:8080

The Web UI allows entering an address, displaying its coordinates and source, and visualizing the location on an embedded interactive map. It also shows the last 20 entries currently held in the database and in the Redis cache, refreshed after every search. A banner appears if a request to the backend fails (e.g. the database is down), instead of the page silently doing nothing.

## Health & Monitoring

Spring Boot Actuator is exposed with the `health` and `info` endpoints:

- **`GET /actuator/health`** — overall status, with detailed `db` (PostgreSQL) and `redis` components.
- **`GET /actuator/health/readiness`** — a narrower readiness view that only considers `db`. Since the cache is optional and the service keeps working without it, an unreachable Redis shouldn't make an orchestrator take the pod out of rotation; the full `/actuator/health` still reports it as `DOWN` for visibility.

## Tests

Execute the unit and integration tests using:
```bash
./mvnw test
```
*Note: Integration tests utilize Testcontainers to run lightweight Postgres and Redis instances. Ensure Docker is running before executing tests.*

Test coverage includes plain unit tests (Mockito), `@WebMvcTest` slices, `@SpringBootTest` integration tests against real (Testcontainers-backed) Postgres/Redis, and dedicated tests for the degraded-mode paths — Redis unavailable, Google returning an error, and Google timing out and being retried.

## Tech Stack

- **Spring Boot 4.1** / Java 21
- **Spring Data JPA** + **Flyway** for PostgreSQL access and schema migrations
- **Spring Data Redis** (`RedisCacheManager`, JSON value serialization) for caching
- **Spring RestClient** for calling the Google Geocoding API
- **Resilience4j** (`resilience4j-spring-boot3`) for retrying transient Google API failures
- **Spring Boot Actuator** for health reporting
- **Thymeleaf + HTMX** for the web UI
- **Testcontainers**, **JUnit 5**, **Mockito**, **AssertJ** for testing

> **Note on dependency naming under Spring Boot 4**: this project uses `resilience4j-spring-boot3` because `resilience4j-spring-boot4` is not yet published to Maven Central (tracked upstream in [resilience4j#2427](https://github.com/resilience4j/resilience4j/issues/2427)); it works fine against Boot 4.1 in practice. The AOP starter needed for `@Retry` is `spring-boot-starter-aspectj`, not `spring-boot-starter-aop` (renamed in Boot 4).
