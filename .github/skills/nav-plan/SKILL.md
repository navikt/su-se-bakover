---
name: nav-plan
description: Planlegg endringer i su-se-bakover med Nav-, domene- og driftskontekst
license: MIT
metadata:
  domain: planning
  tags: planning architecture nais migration
---

# Planlegging i su-se-bakover

Bruk skillen for ikke-trivielle endringer. Les `AGENTS.md`,
`.github/su-eksptert.md` og relevante temafiler først. Verifiser mot kode og tester.

## Arbeidsflyt

1. Avgrens målet, ikke-mål og berørte konsumenter.
2. Spor inngang, application/service, domene, persistens, integrasjoner og output.
3. Beskriv tilstandsoverganger, transaksjonseier og sideeffekter.
4. Vurder auth, sakstilgang, personvern og audit.
5. Vurder kontrakt, Flyway/persistert JSON og bakoverkompatibilitet.
6. Velg minste test som dekker endringen.
7. Beskriv utrulling, observerbarhet og rollback når produksjonsadferd endres.
8. Gjennomfør endringen når beslutningene er avklart.

## Teknologiprofil

- Kotlin/JVM 21 og Ktor
- Gradle multiprosjekt
- Kotliquery, Flyway og PostgreSQL
- Arrow og typed domenefeil
- Kafka-klienter og IBM MQ etter eksisterende mønstre
- JUnit 5, Kotest, Mockito og embedded Postgres
- Nais-manifester og eksisterende GitHub Actions

Ikke planlegg Spring, Rapids & Rivers, Testcontainers, Koin eller ny
prosjektscaffolding. Ikke anta TokenX, ID-porten eller Maskinporten; kontroller den
konkrete eksisterende klienten.

## Leveranse

Planen skal være konkret nok til implementering, men trenger ikke egne dokumenter,
fase-stopp eller seremoni for små endringer. Rapportér risiko tydelig og implementer
når oppgaven ber om det.

Referansene i mappen er generell Nav-bakgrunn. Der de omtaler andre rammeverk eller
standardressurser, gjelder repoets regler og eksisterende konfigurasjon foran.
