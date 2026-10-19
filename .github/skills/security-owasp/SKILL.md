---
name: security-owasp
description: OWASP-kontroller tilpasset Kotlin/Ktor, Kotliquery og Nais i su-se-bakover
license: MIT
metadata:
  domain: security
  tags: security owasp kotlin ktor nais
---

# OWASP for su-se-bakover

Bruk OWASP som risikomodell, men følg repoets konkrete auth-, tilgangs-, database-
og feilflyt.

## Prioriterte kontroller

| Risiko | Repo-kontroll |
|---|---|
| Broken access control | Rolle samt person-/sakstilgang; etablerte provider- og CEF-mønstre |
| Security misconfiguration | Ingen wildcard CORS, åpne adminruter eller brede accessPolicy-regler |
| Supply chain | SHA-pinnede actions, kontrollerte Gradle-avhengigheter og eksisterende skanning |
| Cryptographic failures | Ingen hardkodede secrets eller deaktivert TLS-validering |
| Injection | Parameterisert Kotliquery og ingen shell med uklarert input |
| Insecure design | Typed domenefeil, lovlige tilstandsoverganger og eksplisitt transaksjonsstrategi |
| Authentication failures | Signatur, issuer, audience og relevante claims valideres |
| Integrity failures | Tillatte polymorfe typer og bakoverkompatibel persistert JSON |
| Logging failures | Ingen sensitive data i ordinær logg; riktig sikkerlogg og CEF-audit |
| Exceptional conditions | Ingen brede catches, stille fallback eller feilaktig rollback-antakelse |

## Kotlin-eksempler

```kotlin
session.run(
    queryOf(
        "SELECT * FROM vedtak WHERE id = ?",
        vedtakId,
    ).map(::mapVedtak).asSingle,
)
```

```kotlin
val sak = sakRepo.hentSak(sakId) ?: return FantIkkeSak.left()
tilgangstyringService.assertHarTilgangTilSak(sak.id, bruker)
```

Eksemplene er mønstre, ikke API-er som kan kopieres uten å finne tilsvarende kode i
modulen.

## Grenser

- Rapporter og rett sikkerhetsproblemer når oppgaven ber om det.
- Be om beslutning før auth-, tilgangs- eller auditkontrakter endres.
- Logg aldri fødselsnummer, token, hemmeligheter eller rå request bodies.
- Bruk ikke Spring-, JPA-, Node- eller Go-eksempler i dette repoet.
