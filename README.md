# su-se-bakover

## Applikasjon for saksbehandling av supplerende stønad

### Lokalt oppsett
#### Gradle
For å få tilgang til å hente ut packages fra https://github.com/navikt/ kreves det autentisering mot Github package registry.
Dette kan gjøres ved å spesifisere følgende variabler i `~/.gradle/gradle.properties`:
`githubUser=x-access-token` og `githubPassword="mitt SSO enabled access token med repo+read packages access generert på github"`

#### Database (Postgres)
Lokal database startes med `./docker-compose up`

Hvis man ønsker å resette hele databasen og starte fra scratch er det enkleste å slette volumet.

```sh
docker-compose down
docker volume rm su-se-bakover_supstonad-db-local
docker-compose up
```

#### Starte applikasjon lokalt

Kan startes lokalt fra web/src/main/kotlin/.../Application.kt sin `fun main(...)`
Krever environmentvariablene:
* AZURE_REQUIRED_GROUP
* AZURE_CLIENT_SECRET

Dette kan man enten legge inn i `Run Configuration` i IntelliJ eller lage en `.env` fil på rot-nivå.

Spør en fra teamet, eller hent de fra `Vault` under team-secrets for  `supstonad`

## Ktlint

Hvordan kjøre Ktlint:
* Fra IDEA: Kjør Gradle Task: su-se-bakover->Tasks->formatting->ktlintFormat
* Fra terminal:
   * Kun formater: `./gradlew ktlintFormat`
   * Formater og bygg: `./gradlew ktlintFormat build` eller kjør `./lint_and_build.sh`
   * Hvis IntelliJ begynner å hikke, kan en kjøre `./gradlew clean ktlintFormat build`

Endre IntelliJ autoformateringskonfigurasjon for dette prosjektet:
* `./gradlew ktlintApplyToIdea`

## Upgrade versions
* Check for newest versions: `./gradlew dependencyUpdates --refresh-dependencies`
* Automatically use newest versions: `./gradlew useLatestVersions --refresh-dependencies`
