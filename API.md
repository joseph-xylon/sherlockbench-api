# Sherlockbench API Documentation

## Overview

Sherlockbench is a platform for running coding challenges with two types of runs:
- **Anonymous runs**: Self-guided challenge sessions using selected problem sets
- **Competition runs**: Pre-configured challenges with official tracking

## Authentication and Session Management

- No traditional authentication for API calls
- Client identification via `client-id` parameter
- Run verification via `run-id` and `attempt-id`
- All runs have a limited session lifetime

## Common Response Format

- All API responses use JSON format
- Standard headers include:
  ```
  Content-Type: application/json
  Access-Control-Allow-Origin: *
  ```
- Successful responses have status code 200
- Error responses include `error` field with description

## Endpoints

### Problem Sets

#### `GET /api/problem-sets`

Lists available problem sets by category.

**Response (200)**
```json
{
  "problem-sets": {
    "category1": [
      {"id": "problem-set-id", "name": "Problem Set Name"}
    ]
  }
}
```

**Errors**
- 403: Anonymous runs disabled

### Run Management

#### `POST /api/is-pending-run`

Checks if a run is in pending state.

**Request Body**
```json
{
  "run-id": "<uuid>"
}
```

**Response (200)**
```json
{
  "response": true|false
}
```

#### `POST /api/start-run`

Starts a new run, either anonymous or using existing run ID.

**Request Body (Competition)**
```json
{
  "client-id": "<string>",
  "existing-run-id": "<uuid>"
}
```

**Request Body (Anonymous)**
```json
{
  "client-id": "<string>",
  "problem-set": "<string>"
}
```

**Response (200)**
```json
{
  "run-id": "<uuid>",
  "run-type": "anonymous"|"competition",
  "benchmark-version": "<string>",
  "attempts": [
    {
      "attempt-id": "<uuid>",
      "arg-spec": "<schema>",
      "test-limit": <number>,
      "attempts-remaining": <number>
    }
  ]
}
```

**Errors**
- 400: Missing run ID or problem set
- 400: Invalid problem set
- 403: Anonymous runs disabled
- 412: Run already started

#### `POST /api/complete-run`

Finalizes a run and reports results.

**Request Body**
```json
{
  "run-id": "<uuid>"
}
```

**Response (200)**
```json
{
  "run-time": "<duration>",
  "score": {
    "numerator": <number>,
    "denominator": <number>
  },
  "percent": <number>,
  "problem-names": <array>
}
```

**Errors**
- 412: Invalid run ID

### Function Testing and Verification

#### `POST /api/test-function`

Tests a function with provided arguments.

**Request Body**
```json
{
  "run-id": "<uuid>",
  "attempt-id": "<uuid>",
  "args": [<arguments>]
}
```

**Response (200)**
```json
{
  "output": <result>,
  "attempts_remaining": <number>,
  "test_limit": <number>
}
```

**Errors**
- 400: Invalid arguments
- 400: Test limit reached
- 400: Cannot test after starting verifications
- 412: Invalid run ID or expired session
- 412: Attempt ID doesn't match run ID

#### `POST /api/next-verification`

Gets the next verification challenge.

**Request Body**
```json
{
  "run-id": "<uuid>",
  "attempt-id": "<uuid>"
}
```

**Response (200)**
```json
{
  "status": "success"|"done",
  "next-verification": <verification-data>,
  "output-type": <string>
}
```

**Errors**
- 412: Invalid run or attempt ID

#### `POST /api/attempt-verification`

Submit verification attempt.

**Request Body**
```json
{
  "run-id": "<uuid>",
  "attempt-id": "<uuid>",
  "prediction": <predicted-output>
}
```

**Response (200, Correct)**
```json
{
  "status": "correct",
  "next-verification": <verification-data>,
  "output-type": <string>
}
```

**Response (200, Success/Complete)**
```json
{
  "status": "done",
  "next-verification": null
}
```

**Response (200, Incorrect)**
```json
{
  "status": "wrong",
  "next-verification": null
}
```

**Errors**
- 400: No remaining verifications
- 412: Invalid run or attempt ID

## Run Lifecycle

1. Start a run (anonymous or competition)
2. Test functions with arguments (optional, up to a limit)
3. Get verification challenges 
4. Attempt verifications for each problem
5. Complete the run to get final score

### Developer Operations

Developer operations convert a run from "official" or "anonymous" to "developer" type, as they could be used to circumvent normal challenge rules.

#### `POST /api/developer/reset-attempt`

Resets an attempt to its initial state, allowing retries.

**Request Body**
```json
{
  "run-id": "<uuid>",
  "attempt-id": "<uuid>"
}
```

**Response (200)**
```json
{
  "status": "success",
  "message": "Attempt has been reset"
}
```

**Errors**
- 412: Invalid run or attempt ID

**Note**: Using this endpoint will change the run's type to "developer", marking it as non-official.

## Error Codes

- 200: Success
- 400: Bad request (invalid parameters)
- 403: Forbidden (anonymous runs disabled)
- 412: Precondition failed (invalid session state)
