## How to run

Install JDK 17 and Docker, then run these commands from the project root:

1. Start the bank simulator:

   ```sh
   docker compose up -d
   ```

2. Start the application:

   ```sh
   ./gradlew bootRun
   ```

The application runs at http://localhost:8090.
Open [Swagger UI](http://localhost:8090/swagger-ui/index.html) to try the API.

Run automated tests with `./gradlew test`.

## Assumptions

- Accepts currency codes only in uppercase.
- Amounts must be positive integers in minor currency units, up to Integer limit (2,147,483,647).
- Card expiry must be later than the current UTC month and cards expiring this month are rejected.
- Expiry year must contain four digits and should be in the future.
- JSON strict types are configured for POST request.
- Rejected requests return HTTP 400 with an error message, without a payment ID or status,
  as the requirements do not explicitly specify whether rejected responses must include a status
  field.
- Authorized and declined payments both return HTTP 201 and are stored with a random-generated UUID.
- Only the last four card digits are stored and returned.
- Bank authorization codes are not required for payment retrieval and are not stored.
- Bank HTTP 400 maps to HTTP 422, HTTP 503 remains HTTP 503 and other bank or communication failures return HTTP 502.
- Bank error logs include HTTP status and exception types without logging raw bank response bodies
  or exception messages.
- Bank calls use already given 10-second connection(TBD for production implementation) and read timeouts, with no automatic retries.
- Each payment submission is treated as a new payment. Duplicates requests and idempotency are outside scope.
- Left the Payment Retrieval 'Page not found' error response as a standard 404 Not Found.
- Bank responses are mocked in automated tests using mock server, so a running bank simulator is not required.

## Tests Covered

- Authorized and declined payments
- Payment retrieval
- Invalid input and required fields
- Expiry boundaries
- Bank failures
- Response masking
- Bank request string redaction
- Card-length boundaries
- Valid four-digit CVVs
- USD/EUR acceptance
- Integer overflow and string coercion
- Missing/null fields through HTTP
- Malformed retrieval IDs
- Bank HTTP error mappings
- Log redaction
