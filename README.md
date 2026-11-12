# su-se-bakover

Backend for saksbehandling av supplerende stønad (SU). Frontend er
[su-se-framover](https://github.com/navikt/su-se-framover).

## Kom i gang

1. Installer verktøy og sett opp tilgang til GitHub Packages:
   [Installasjon og tilganger](docs/installasjon-og-tilganger.md).
2. Start Colima og lokal database, og kjør `./get_started.sh`:
   [Lokal utvikling](docs/lokal-utvikling.md).
3. Start su-se-framover med `start-dev.sh` og deretter `LocalApplicationWithTestdata`.

## Dokumentasjon

| Kapittel | Innhold |
|----------|---------|
| [Installasjon og tilganger](docs/installasjon-og-tilganger.md) | verktøy, IntelliJ-lisens og token for GitHub Packages |
| [Lokal utvikling](docs/lokal-utvikling.md) | Colima, git hooks, sjekkliste, oppstart og lokal database |
| [Autentisering lokalt](docs/autentisering-lokalt.md) | mock-oauth, bytte av saksbehandlerident, Azure og STS |
| [Database](docs/database.md) | tilkobling til dev/prod, kopi fra dev, backup/import og PDF fra databasen |
| [Feilsøking](docs/feilsoking.md) | Colima/Testcontainers og embedded Postgres |
| [Kodestil](docs/kodestil.md) | Spotless og ktlint |
| [Drift](docs/drift.md) | Kubernetes, metrikker, alerts og IBM MQ |
| [Vedlikehold](docs/vedlikehold.md) | Dependabot og oppgradering av Gradle, Kotlin og Java |
| [Historikk](docs/historikk.md) | utdatert on-prem-oppsett og migrerte Infotrygd-data |

Moduldokumentasjon:

- ./hendelse/[README](hendelse/README.md)
- ./kontrollsamtale/[README](kontrollsamtale/README.md)
- ./tilbakekreving/[README](tilbakekreving/README.md)
- ./vilkår/institusjonsopphold/[README](vilkår/institusjonsopphold/README.md)

Infotrygd revurdering:
- [innsiktsarbeid](docs/infotrygd-revurdering/historisk-import-og-revurdering.md)

## Statistikk

Se https://confluence.adeo.no/spaces/TESUS/pages/514138632/su-topics

## Regel i kode

[Se Regelspesifisering.kt](common/domain/src/main/kotlin/no/nav/su/se/bakover/common/domain/regelspesifisering/Regelspesifisering.kt)
