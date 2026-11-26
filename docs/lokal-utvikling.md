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

Lokalt lager appen PDF med pdf-bygger eller en stub:

1. Start containerne: `docker compose up -d`
2. Sett `PDFBYGGER_LOCAL=true` i `.env` i repository-roten. Opprett filen hvis den mangler.
3. Start appen på nytt.

`PDFBYGGER_LOCAL` har standardverdien `false`. Da får alle maler en stub-PDF, og ingen PDF-tjeneste
trenger å kjøre. Med `true` lager pdf-bygger på `localhost:8082` alle PDF-er. Flagget starter ikke tjenesten.

Hvis pdf-bygger returnerer feil, feiler PDF-genereringen. Backend-loggen oppgir hvilken mal som feilet,
med lokale tips om port og oppstart. Feilmeldingen til frontend er generell.

Imaget for pdf-bygger finnes bare for AMD64, så Colima må kunne emulere AMD64 på Mac med ARM.

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
