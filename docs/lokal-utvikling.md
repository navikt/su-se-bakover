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

## Database (Postgres)

Lokal database startes med `docker compose up`

Hvis man ønsker å resette hele databasen og starte fra scratch er det enkleste å slette volumet ved å
kjøre `./resetdb.sh`

Kopiering av data fra dev og backup/import er beskrevet i [Database](database.md).
