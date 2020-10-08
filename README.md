# su-se-bakover

## Applikasjon for saksbehandling av supplerende stønad

### Lokalt oppsett
#### Gradle
For å få tilgang til å hente ut packages fra https://github.com/navikt/ kreves det autentisering mot Github package registry.
Det gjør du ved å lage en ny fil i .gradle-mappa i hjemmemappa: 

`$ vim ~/.gradle/gradle.properties`

I denne filen skriver du: 
```
githubUser=x-access-token
githubPassword={et access token som du lager på GitHub}
```
Access tokens lager du på: https://github.com/settings/tokens. Tokenet skal kun ha  `repo + read packages access`. 
Husk å kopiere tokenet før du går videre. 

Deretter må tokenet autentiseres med SSO-tilgang. Det gjør du i listen over tokens.  

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
