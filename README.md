#su-se-bakover

##Applikasjon for saksbehandling av supplerende stønad

### Lokalt oppsett
#### Database (Postgres)
Lokal database startes med `./docker-compose up`

Hvis man ønsker å resette hele databasen og starte fra scratch er det enkleste å slette volumet.

```sh
$ docker-compose down
$ docker volume rm su-se-bakover_supstonad-db-local
```

#### Starte applikasjon lokalt

Applikasjonen kan startes lokalt etter å ha spesifisert nødvendige miljøvariabler definert i filen `application.conf`.
Verdier som ikke har default verdi finner man i `Vault` under team-secrets for  `supstonad`

## Ktlint

Hvordan kjøre Ktlint:
* Fra IDEA: Kjør Gradle Task: su-se-bakover->Tasks->formatting->ktlintFormat
* Fra terminal: `./gradlew ktlintFormat`

Endre IntelliJ autoformateringskonfigurasjon for dette prosjektet:
* `./gradlew ktlintApplyToIdea`
