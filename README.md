# Geocoder Service

A Spring Boot-based geocoding backend service that resolves address search queries into coordinates (latitude and longitude). It uses a tiered lookup strategy to optimize response times and manage third-party API usage: Redis cache → PostgreSQL database → Google Geocoding API, persisting new results to the database and caching them.

The resolution workflow is as follows:
1. **Cache Lookup**: Check if the coordinates for the normalized address are already stored in the Redis cache.
2. **Database Lookup**: If not cached, query the PostgreSQL database. If found, cache the result.
3. **Google Geocoding API**: If not in the database, fetch the coordinates from Google's API, persist the new record to the PostgreSQL database, and cache the result.

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
- **Query Parameter**: `address` (required) — The address to look up.

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

The Web UI allows entering an address, displaying its coordinates and source, and visualizing the location on an embedded interactive map.

## Tests

Execute the unit and integration tests using:
```bash
./mvnw test
```
*Note: Integration tests utilize Testcontainers to run lightweight Postgres and Redis instances. Ensure Docker is running before executing tests.*
