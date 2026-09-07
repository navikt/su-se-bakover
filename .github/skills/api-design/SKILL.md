---
name: api-design
description: REST API-designmønstre, versjonering, feilhåndtering (RFC 7807) og OpenAPI-konvensjoner for Nav-tjenester
license: MIT
compatibility: Go or Kotlin backend on Nais
metadata:
  domain: backend
  tags: api rest design openapi error-handling
---

# API Design Skill

REST API design for Nav services. Covers naming conventions, error handling with ProblemDetail, versioning, pagination, and OpenAPI spec.

## su-se-bakover profile

Follow existing Ktor routes, DTOs, error responses, authorization and versioning.
Do not introduce Spring annotations, `ProblemDetail`, a new pagination envelope or
URL versioning without an explicit API-contract decision. Access to a route must
also follow the established role and person/case access checks.

## URL Conventions

```
# ✅ Correct
GET    /api/vedtak                    # List
GET    /api/vedtak/{id}               # Get by ID
POST   /api/vedtak                    # Create
PUT    /api/vedtak/{id}               # Full update
PATCH  /api/vedtak/{id}               # Partial update
DELETE /api/vedtak/{id}               # Delete

# ✅ Sub-resources
GET    /api/vedtak/{id}/aktiviteter   # List child resources
POST   /api/vedtak/{id}/aktiviteter   # Create child resource

# ✅ Actions (verb as sub-resource)
POST   /api/vedtak/{id}/godkjenn      # State transition

# ❌ Wrong
GET    /api/getVedtak                 # Verb in URL
GET    /api/vedtak/hentAlle           # Verb in URL
POST   /api/createVedtak              # Verb in URL
GET    /api/Vedtak                    # PascalCase
```

## Error Handling

Use the existing route- or `StatusPages`-based mapping:

```kotlin
install(StatusPages) {
    exception<ResourceNotFoundException> { call, cause ->
        call.respond(HttpStatusCode.NotFound, existingErrorResponse(cause))
    }
    exception<ValidationException> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, existingErrorResponse(cause))
    }
}
```

## Pagination

Use offset-based pagination with consistent parameter names:

Validate bounded page size and allowlisted sort fields at the Ktor boundary. Reuse
the response shape already used by the relevant API.

Response:

```json
{
  "content": [...],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 142,
    "totalPages": 8
  }
}
```

## Input Validation

Deserialize to a typed request and convert primitive values to existing value
objects at the boundary. Keep business validation in the domain, not in routes.

## HTTP Status Codes

| Code | Usage |
|---|---|
| `200 OK` | Successful GET, PUT, PATCH |
| `201 Created` | Successful POST (new resource) |
| `204 No Content` | Successful DELETE |
| `400 Bad Request` | Invalid input / validation failed |
| `401 Unauthorized` | Missing or invalid token |
| `403 Forbidden` | Valid token, but no access |
| `404 Not Found` | Resource does not exist |
| `409 Conflict` | Duplicate / state conflict |
| `422 Unprocessable Entity` | Semantic error (valid format, wrong content) |
| `500 Internal Server Error` | Unexpected server error |

## Versioning

Use URL-based versioning when breaking changes are necessary:

Alternatively, avoid versioning by:
- Only adding new fields (never removing)
- Making new fields optional
- Deprecating fields with `@Deprecated` before removal

## Rules

- **Use nouns** in URLs, not verbs
- **Use kebab-case** for multi-word URL segments: `/api/vedtak-perioder`
- **Use camelCase** for JSON fields: `opprettetDato`, `brukerId`
- **Preserve the existing error contract** unless consumers have an agreed migration
- **Validate transport input** at the route boundary and business rules in the domain
- **Never log PII** in request/response — log correlation ID
- **Set `Content-Type: application/json`** on all responses
