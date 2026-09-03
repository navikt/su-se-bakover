# Systemoversikt

**Status:** `verified`

`su-se-bakover` er en Kotlin/Ktor-backend for saksbehandling av supplerende stønad.
Den dekker sakstypene alder og uføre og brukes av `su-se-framover`, som fungerer som
frontend/BFF.

## Teknologi

- Kotlin på JVM 21
- Gradle multiprosjekt
- Ktor
- PostgreSQL med Flyway
- Kotliquery
- Arrow
- Kafka og IBM MQ i integrasjonsflatene
- JUnit 5, Kotest, Mockito og embedded Postgres
- Spotless med ktlint

## Lagdeling

Kodebasen er under gradvis modulering. Nye områder er ofte delt i:

```text
<område>:domain
<område>:application
<område>:infrastructure
<område>:presentation
```

Eldre sentral kode ligger fortsatt i modulene `domain`, `service`, `database`, `web`
og `client`. Ikke anta at filplassering alene avgjør arkitekturlaget; kontroller
pakken, portene og wiringen.

Typisk ansvar:

- **domain** – forretningsregler, tilstander, value objects og porter
- **application/service** – orkestrering av use case og transaksjonsgrenser
- **infrastructure/database/client** – Postgres og eksterne integrasjoner
- **presentation/web** – HTTP-kontrakter, autentisering og responsmapping
- **bootstrap** – komposisjon og oppstart

## Viktige domeneområder

| Område | Hovedmoduler |
|---|---|
| Sak, søknad, revurdering og klage | `domain`, `behandling:*` |
| Beregning | `beregning`, `satser`, `grunnbeløp` |
| Regulering | `behandling:regulering:*`, `domain`, `service` |
| Vilkår og grunnlag | `vilkår:*` |
| Utbetaling og simulering | `økonomi:*` |
| Brev og dokument | `dokument:*` |
| Vedtak | `vedtak:*`, `domain` |
| Hendelser | `hendelse:*` |
| Kontrollsamtale | `kontrollsamtale:*` |
| Nøkkeltall | `nøkkeltall:*` |
| Tilbakekreving | `tilbakekreving:*` |
| Tilgang | `tilgangstyring:*`, `web` |
| Person og oppgaver | `person:*`, `oppgave:*` |
| Statistikk | `statistikk:*` |
| Historisk import | `domain`, `service`, `database`, `client`, `web` |

`settings.gradle.kts` er autoritativ for hvilke moduler som finnes.

## Kommunikasjon

- `su-se-framover` kaller backend med brukerens on-behalf-of-token.
- HTTP-klienter brukes blant annet mot PDL/personoppslag, Oppgave, Joark/SAF, Kabal,
  Pesys, AAP, KRR, skjerming og simulering.
- Utbetalingsoppdrag sendes til Oppdrag gjennom økonomimodulens publisher.
- Kafka brukes for enkelte asynkrone hendelser og statistikkflater.

Konkrete klienter og konfigurasjon er kilde for faktisk autentiseringsmekanisme. Ikke
anta at alle klienter bruker samme tokenflyt.

## Persistens

Hvert use case skal ha en tydelig eier av session eller transaksjon. Context kan
sendes gjennom porter når flere operasjoner skal være atomiske. Nestede sessions på
samme tråd er forbudt.

Historisk import skiller seg fra ordinær domenepersistens ved at rådata bevares
tapsfritt før konvertering. Dette er et avgrenset unntak fra typed-data-regelen.

## Kilder

- `settings.gradle.kts`
- `build.gradle.kts`
- `README.md`
- `client/.../ClientsBuilder.kt`
- `common/infrastructure/.../persistence/`
- `.nais/dev-gcp.yaml`
- `.nais/dev-gcp-q1.yaml`
- `.nais/prod-gcp.yaml`
- `docs/historisk-import-og-revurdering.md`
