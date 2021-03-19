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

#### Autentisering
su-se-framover tar seg av autentisering og kaller oss med on-behalf-of tokens.

Lokalt kjøres det opp en mock oauth2-server på http://localhost:4321.
Se https://github.com/navikt/su-se-framover#mock-oauth-server for mer informasjon.

##### For testing mot Azure
Legg inn følgende variabler i [.env]():
- AZURE_APP_CLIENT_ID
- AZURE_APP_WELL_KNOWN_URL
- AZURE_APP_CLIENT_SECRET

Disse hentes fra kjørende pod i det miljøet du vil teste mot.
Merk: `AZURE_APP_CLIENT_SECRET` roteres automatisk.

For å da bruke gruppe-claimet som kommer fra Azure må man også endre implementasjonen av `getGroupsFromJWT` i [./web/src/main/kotlin/no/nav/su/se/bakover/web/Extensions.kt]().

##### For testing mot Sts lokalt
Bytt dette i AuthenticationConfig.kt
```diff
- verifier(jwkStsProvider, stsJwkConfig.getString("issuer"))
+ verifier(jwkStsProvider, "https://security-token-service.nais.preprod.local"))
```

Bytt dette i StsClient.kt
```diff
override fun jwkConfig(): JSONObject {
    val (_, _, result) = wellKnownUrl.httpGet().responseString()
    return result.fold(
-        { JSONObject(it) },
+        { JSONObject(it.replace("nais.preprod.local", "dev.adeo.no")) },
        { throw RuntimeException("Could not get JWK config from url $wellKnownUrl, error:$it") }
    )
}
```

Legg disse i .env
```shell
USE_STUB_FOR_STS=false
STS_URL=https://security-token-service.dev.adeo.no
```

#### Uten innlogging i Azure (default)
Applikasjonen vil selv generere gyldige jwt-tokens for inkommende kall. Ved behov kan innholdet i disse konfigureres i `JwtStub.kt`.

Merk at tokens som genereres automatisk av appen kun vil være gyldige for den aktuelle instansen som genererte den.
For å unngå at man må starte helt fra "login" når applikasjonen restartes, kan man heller slette gamle `access_token`fra browser,
dette fører til at det gjenværende `refresh_token` benyttes til å generere nye.

#### Med innlogging i Azure
Ved behov for "ekte" innlogging mot Azure kan dette aktiveres for lokal utvikling ved å endre konfigurasjonen i filen `AuthenticationConfig.kt`.
For at dette skal fungere må man i tillegg bytte ut Azure-stubben i `StubClientsBuilder.kt` med en faktisk Azure-klient.

Bruk av faktisk klient krever miljøvariabelen:
* AZURE_APP_CLIENT_SECRET

Dette kan man enten legge inn i `Run Configuration` i IntelliJ eller lage en `.env` fil på rot-nivå. Se `.env.template`
som eksempel.

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

### Experiment writing prometheus queries and view graphs
preprod: https://prometheus.dev-fss.nais.io
prod: https://prometheus.prod-fss.nais.io

### View ongoing alerts and manage silences
preprod: https://alertmanager.dev-fss.nais.io
prod: https://alertmanager.prod-fss.nais.io

### Kubectl commands
* View alerts: `kubectl --namespace=supstonad get alerts`
* Describe alert: `kubectl --namespace=supstonad describe alert su-se-bakover`
* Delete alert: `kubectl --namespace=supstonad delete alert su-se-bakover`
* Deploy new alert: Just run the `alerts-deploy.yml` github actions workflow

## Upgrade dependency versions
* Check for newest versions: `./gradlew dependencyUpdates --refresh-dependencies`
* Automatically use the newest versions: `./gradlew useLatestVersions --refresh-dependencies`

## Upgrade gradlew
1. Find the lastest version of gradle, e.g. by checking here: https://gradle.org/releases/
1. Then run `./gradlew wrapper --gradle-version <version>`
1. In `build.gradle.kts` you also have to change the gradle version. E.g. search for `gradleVersion =`

## Upgrade java version
1. In `build.gradle.kts` and search for `jvmTarget = `