# ShadowBase API Contract — v1

Status: Proposed for team review  
Base URL: `http://localhost:8080/api/v1`

## Shared Rules

- Normal API responses use JSON. The live updates endpoint returns `text/event-stream`, with JSON in each `data` line.
- JSON field names use `camelCase`.
- Timestamps use UTC ISO-8601 format, for example: `2026-11-13T17:20:00Z`.
- The backend generates the `sandboxId`.
- The production database is named `shadowbase_prod`.
- The frontend handles migration script export as a file download.

## Create a Sandbox

`POST /sandboxes`

Request:

```json
{
  "name": "orders-risk-check",
  "sourceDatabase": "shadowbase_prod"
}
```

Response: `201 Created`

```json
{
  "sandboxId": "sbx-001",
  "name": "orders-risk-check",
  "status": "CREATED",
  "createdAt": "2026-11-13T17:20:00Z"
}
```

## Get Sandbox Status

`GET /sandboxes/{sandboxId}`

Response: `200 OK`

```json
{
  "sandboxId": "sbx-001",
  "name": "orders-risk-check",
  "status": "SEEDED",
  "createdAt": "2026-11-13T17:20:00Z"
}
```

Allowed statuses: `CREATED`, `SEEDING`, `SEEDED`, `REPLAYING`, `STOPPED`, `FAILED`.

## Seed the Sandbox

`POST /sandboxes/{sandboxId}/seed`

Request:

```json
{
  "mode": "snapshot"
}
```

Response: `200 OK`

```json
{
  "sandboxId": "sbx-001",
  "status": "SEEDED",
  "rowCounts": {
    "customers": 2,
    "orders": 3
  }
}
```

## Start or Stop Replay

`POST /sandboxes/{sandboxId}/replay`

Request to start replay:

```json
{
  "action": "START"
}
```

Response: `200 OK`

```json
{
  "sandboxId": "sbx-001",
  "replayStatus": "RUNNING"
}
```

Request to stop replay:

```json
{
  "action": "STOP"
}
```

Response: `200 OK`

```json
{
  "sandboxId": "sbx-001",
  "replayStatus": "STOPPED"
}
```

Allowed `action` values: `START`, `STOP`.

## Apply a Migration

`POST /sandboxes/{sandboxId}/migrations`

Request:

```json
{
  "script": "ALTER TABLE public.orders DROP COLUMN status;"
}
```

Response: `200 OK`

```json
{
  "migrationId": "mig-001",
  "sandboxId": "sbx-001",
  "status": "APPLIED",
  "appliedAt": "2026-11-13T17:25:00Z"
}
```

## Get Replay Metrics

`GET /sandboxes/{sandboxId}/metrics`

Response: `200 OK`

```json
{
  "sandboxId": "sbx-001",
  "queriesReplayed": 24,
  "errorCount": 2,
  "errorRatePercent": 8.33,
  "lagMs": 140,
  "updatedAt": "2026-11-13T17:26:00Z"
}
```

## Get SQL Errors

`GET /sandboxes/{sandboxId}/errors?limit=50`

Response: `200 OK`

```json
{
  "sandboxId": "sbx-001",
  "items": [
    {
      "eventId": "evt-001",
      "operation": "UPDATE",
      "tableName": "orders",
      "message": "Column status does not exist",
      "sqlState": "42703",
      "occurredAt": "2026-11-13T17:26:00Z"
    }
  ]
}
```

## Live Updates

`GET /sandboxes/{sandboxId}/events`

Use Server-Sent Events with the request header `Accept: text/event-stream`. Each `data` value contains JSON.

```text
event: metrics
data: {"sandboxId":"sbx-001","queriesReplayed":24,"errorCount":2,"errorRatePercent":8.33,"lagMs":140}

event: sql-error
data: {"eventId":"evt-001","operation":"UPDATE","tableName":"orders","message":"Column status does not exist","sqlState":"42703","occurredAt":"2026-11-13T17:26:00Z"}
```

## Delete a Sandbox

`DELETE /sandboxes/{sandboxId}`

Success response: `204 No Content`.

## Common Error Response

```json
{
  "timestamp": "2026-11-13T17:26:00Z",
  "status": 404,
  "code": "SANDBOX_NOT_FOUND",
  "message": "Sandbox not found",
  "path": "/api/v1/sandboxes/sbx-001"
}
```

Common error codes: `VALIDATION_ERROR`, `SANDBOX_NOT_FOUND`, `SANDBOX_NOT_READY`, `MIGRATION_FAILED`, `INTERNAL_ERROR`.