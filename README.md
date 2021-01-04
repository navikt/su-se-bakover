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

#### Uten innlogging i Azure (default)
Applikasjonen vil selv generere gyldige jwt-tokens for inkommende kall. Ved behov kan innholdet i disse konfigureres i "TODO".

#### Med innlogging i Azure
Ved behov for "ekte" innlogging mot Azure kan dette aktiveres for lokal utvikling ved å endre konfigurasjonen i filen `AuthenticationConfig.kt`.

Dette krever miljøvariablene:
* AZURE_APP_CLIENT_SECRET
* AZURE_GROUP_VEILEDER
* AZURE_GROUP_SAKSBEHANDLER
* AZURE_GROUP_ATTESTANT

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

## Metrics
Vi bruker Prometheus for å samle inn metrikker.
Se https://doc.nais.io/observability/metrics.

## Alerts
`alerts.yml` deployes automatisk vha. `.github/workflows/alerts-deploy.yml` og benytter [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/) for å sette opp alerts basert på metrikker.
Se https://doc.nais.io/observability/alerts.

## Upgrade versions
* Check for newest versions: `./gradlew dependencyUpdates --refresh-dependencies`
* Automatically use newest versions: `./gradlew useLatestVersions --refresh-dependencies`
