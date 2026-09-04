---
name: nav-troubleshoot
description: Feilsøk Ktor, Nais, PostgreSQL, Kafka og MQ i su-se-bakover
license: MIT
metadata:
  domain: platform
  tags: troubleshooting diagnostics nais kotlin
---

# Feilsøking

1. Reproduser eller avgrens symptomet.
2. Finn berørt kallkjede og siste relevante endring.
3. Sjekk eksisterende metrikker og logger innen et kort tidsrom.
4. Skill mellom kodefeil, konfigurasjon, avhengighet, data og ressursproblem.
5. Test hypotesen med minst mulig inngrep.
6. Rett rotårsaken og kjør målrettet test.

## Vanlige spor

- **401/403:** skill mellom autentisering, rolle, person-/sakstilgang og
  accessPolicy. Ikke dekod eller lim inn ekte token.
- **Database:** kontroller Flyway, connection budget, trege queries, lekkasje og
  nestede sessions. Ikke øk poolen som første tiltak.
- **Kafka/MQ:** kontroller topic/queue, consumer group, schema, retry,
  idempotens og publiseringsrekkefølge etter repoets faktiske klienter. Bruk ikke
  Rapids & Rivers-råd.
- **Pod:** kontroller events, forrige logg, OOM, probes og faktisk resource-bruk.
- **Deploy:** kontroller eksisterende reusable workflow, image og Nais-status.

Ikke hent secrets eller persondata, og ikke endre eller restarte produksjon uten
uttrykkelig godkjenning.
