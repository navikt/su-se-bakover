# Lokal utvikling

## Oppsett

```bash
  colima start
```
Colima trenger kun å startes én gang, dersom både frontend og backend skal kjøres lokalt.

Kjør `./get_started.sh` for å konfigurere opp hooks. Denne setter hook pathen for git lokalt for dette prosjektet.
Nå kan alle git hooks legges inn under `scripts/hooks`.

sjekkliste:

1. ha riktig gradle versjon (kan finnes i `build.gradle.kts` - let etter `gradleVersion =`)
2. ha riktig java versjon (kan finnes i `build.gradle.kts` - let etter `jvmTarget =`)
3. La prosjektet ditt bruke riktig java versjon (høyreklikk prosjektnavn -> `Open module settings` og set riktig java
   versjon)
    - Du kan laste ned java versjon ved bruk av SDKman
4. Ikke ha database porten i bruk av noe annet (hvis du for eksempel lastet ned postgresql via homebrew og startet den)

Får du problemer med Colima, Testcontainers eller embedded Postgres, se [Feilsøking](feilsoking.md).

## Starte applikasjon lokalt

Kan startes lokalt fra `bootstrap/src/main/kotlin/.../Application.kt` sin `fun main(...)`. Krever at `start-dev.sh` skriptet
i [su-se-fremover](https://github.com/navikt/su-se-framover#kj%C3%B8re-lokalt) kjører
Kjør LocalApplicationWithTestdata

Innlogging og bytte av saksbehandlerident lokalt er beskrevet i [Autentisering lokalt](autentisering-lokalt.md).

#### PDF-generering lokalt

Lokalt lager appen PDF med su-pdfgen eller en stub. Slik bruker du pdf-bygger for maler med `pdfByggerDev = true`:

1. Start containerne: `docker compose up -d`
2. Sett `PDFBYGGER_LOCAL=true` i `.env` i repository-roten. Opprett filen hvis den mangler.
   Sett også `PDFGEN_LOCAL=true` hvis du vil sammenligne med ekte PDF fra su-pdfgen.
3. Start appen på nytt.

Begge miljøvariablene har standardverdien `false` hvis de ikke er satt. `PDFBYGGER_LOCAL` velger om
aktiverte maler bruker lokal pdf-bygger. `PDFGEN_LOCAL` velger mellom lokal su-pdfgen og en stub for gammel
generator. Flaggene starter ikke tjenestene.

| `PDFBYGGER_LOCAL` | `PDFGEN_LOCAL` | Maler med `pdfByggerDev = true` | Andre maler | PDF-tjenester som må kjøre |
|---|---|---|---|---|
| `false` | `false` | Bare stub-PDF | Bare stub-PDF | Ingen |
| `false` | `true` | PDF fra su-pdfgen | PDF fra su-pdfgen | su-pdfgen på 8081 |
| `true` | `false` | Bare PDF fra pdf-bygger, uten stub-vedlegg | Bare stub-PDF | pdf-bygger på 8082 |
| `true` | `true` | PDF fra pdf-bygger med PDF fra su-pdfgen som vedlegg | PDF fra su-pdfgen | pdf-bygger på 8082 og su-pdfgen på 8081 |

Sammenligning skjer lokalt bare når begge flaggene er `true`. Da legges sidene fra su-pdfgen etter sidene
fra pdf-bygger i samme PDF, slik at du kan kontrollere dem manuelt. Appen sammenligner ikke innholdet
automatisk. `docker compose up -d` starter Postgres og pdf-bygger, men ikke su-pdfgen. Su-pdfgen må
startes separat hvis `PDFGEN_LOCAL=true`.

Hvis en nødvendig generator returnerer feil, feiler hele PDF-genereringen. Backend-loggen oppgir hvilken
generator som feilet, med lokale tips om port, oppstart eller flagg. Feilmeldingen til frontend er fortsatt
generell. I dev sammenlignes fortsatt begge ekte dokumenter, uavhengig av de lokale flaggene.

Pdf-bygger svarer på `localhost:8082`, fordi su-pdfgen bruker 8081. Imaget finnes bare for AMD64, så Colima
må kunne emulere AMD64 på Mac med ARM.

I `docker-compose.yml`, under `services.pdf-bygger.environment`, står:

```yaml
JAVA_TOOL_OPTIONS: "-Dio.ktor.development=true"
```

Innstillingen `-Dio.ktor.development=true` aktiverer utviklingsmodus i pdf-bygger og slår av tokenkontrollen.
Det er derfor det falske tokenet
fra `AzureClientStub` kan brukes: Appen sender bestillingen med tokenet til `localhost:8082`, og containeren
lager en ekte PDF uten å validere tokenet. Med tokenkontroll aktivert ville det falske tokenet ikke blitt
godtatt. Du trenger dermed ikke hente et ekte Azure-token for dette lokale kallet. Oppsettet gjelder bare
den lokale pdf-bygger-containeren, ikke autentiseringen i backend eller i dev og produksjon.

Den ekte klienten lokalt er et godkjent avvik fra Kotlin-instruksjonene: `lokal-pdf-bygger-ekte-klient` i
`.github/ai-historikk/avvik.jsonl`.

## Database (Postgres)

Lokal database startes med `docker compose up`

Hvis man ønsker å resette hele databasen og starte fra scratch er det enkleste å slette volumet ved å
kjøre `./resetdb.sh`

Kopiering av data fra dev og backup/import er beskrevet i [Database](database.md).
