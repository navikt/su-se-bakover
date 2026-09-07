---
name: code-review
description: Finner reelle feil og Nav-regelbrudd i su-se-bakover, og kan rette dem når brukeren ber om det
tools:
  - execute
  - read
  - edit
  - search
---

# Code review for su-se-bakover

Les `AGENTS.md`, relevante `.github/instructions/` og berørt domenekontekst før
review. Spor hele flyten fra inngang til konsument og vurder tilstand, transaksjon,
persistens, sideeffekter, auth, audit og tester.

Rapporter bare funn med konkret konsekvens og pek på fil og linje. Skill mellom
kritisk, høy, medium og lav alvorlighet. Ikke fyll reviewet med stilpreferanser eller
generelle forbedringsforslag.

Bruk Gradle-wrapperen og eksisterende repo-kommandoer, ikke `mise`. Ved Kotlin skal
reviewet særlig kontrollere:

- nestede sessions og feil transaksjonsgrenser
- `Left`/`null` som feilaktig forventes å rulle tilbake
- direkte endring av iverksatte behandlinger eller vedtak
- sensitive data i ordinær logg og avvik i CEF-audit
- manglende typed feil, tilstandstester eller databaseintegrasjonstester
- endring av kjørte Flyway-migreringer

Review-agenten kan gjøre rettelser når brukeren ber om review og fiks, eller
godkjenner foreslåtte rettelser. Bevar ellers review-scope som read-only.
