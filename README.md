# su-se-bakover

## Applikasjon for saksbehandling av supplerende stønad

### Lokalt oppsett

#### Database (Postgres)
Lokal database startes med `docker compose up`

Hvis man ønsker å resette hele databasen og starte fra scratch er det enkleste å slette volumet ved å kjøre `./resetdb.sh`

#### Starte applikasjon lokalt
Kan startes lokalt fra web/src/main/kotlin/.../Application.kt sin `fun main(...)`. Krever at `start-dev.sh` skriptet
i [su-se-fremover](https://github.com/navikt/su-se-framover#kj%C3%B8re-lokalt) kjører

#### Autentisering
su-se-framover tar seg av autentisering (backend for frontend (BFF)) og kaller su-se-bakover med on-behalf-of tokens (per bruker).

Lokalt kjøres det opp en mock oauth2-server på http://localhost:4321 .
Se https://github.com/navikt/su-se-framover#mock-oauth-server for mer informasjon.

##### For testing mot Azure
Legg inn følgende variabler i [.env]():
- AZURE_APP_CLIENT_ID
- AZURE_APP_WELL_KNOWN_URL
- AZURE_APP_CLIENT_SECRET

Kan kopiere `AZURE_APP_WELL_KNOWN_URL` fra .env.azure.template
Disse hentes fra kjørende pod i det miljøet du vil teste mot.
Merk: `AZURE_APP_CLIENT_SECRET` roteres automatisk.

For da å bruke gruppe-claimet som kommer fra Azure må man også endre implementasjonen av `getGroupsFromJWT` i [./web/src/main/kotlin/no/nav/su/se/bakover/web/Extensions.kt]().

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

## Autoformatting / linting

Benytter oss av spotless: https://github.com/diffplug/spotless/tree/main/plugin-gradle
Som har støtte for forskjellig verktøy som ktlint, ktfmt, diktat, prettier.

### Installer plugin:
- IntelliJ: https://plugins.jetbrains.com/plugin/18321-spotless-gradle og https://github.com/ragurney/spotless-intellij-gradle
- VS Code: https://marketplace.visualstudio.com/items?itemName=richardwillis.vscode-spotless-gradle

### Hvordan kjøre Spotless:
* Hvis du har pluginen; Meny -> Code -> Reformat Code with Spotless
* Det finnes en gradle task; `spotlessApply` som kan keybindes eller knyttes til on-save (må muligens bruke FileWatcher-plugin)

### Hvordan diagnosere Spotless:
`./gradlew spotlessDiagnose`

### Hvis du fremdeles vil bruke ktlint direkte:
* Installer ktlint https://github.com/pinterest/ktlint/releases/ (inntil plugin støtter dette. Kan vurdere legge binæren i prosjektet)
* ktlint applyToIDEAProject (denne overstyrer da IntelliJ sine formateringsregler så godt det går.)

### Kjente feil:
* Kjør `rm ./.git/hooks/pre-commit ./.git/hooks/pre-push` for å slette gamle hooks hvis den feiler ved commit

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

## Upgrade gradlew
1. Find the lastest version of gradle, e.g. by checking here: https://gradle.org/releases/
1. Then run `./gradlew wrapper --gradle-version <version>`
1. In `build.gradle.kts` you also have to change the gradle version. E.g. search for `gradleVersion =`

## Upgrade java version
1. In `build.gradle.kts` and search for `jvmTarget = `
1. In `.github/workflows/*.yml` and search for `java-version`
1. In `Dockerfile` replace `FROM navikt/java:<version>`

## Kubernetes
1. Check out https://github.com/navikt/kubeconfigs
1. Set context: `kubectl config set-context dev-fss` 
1. Try to get pods: `kubectl get pods`, and follow the auth info

* Set team-namespace as default: `kubectl config set-context --current --namespace=supstonad`
* Describe pod: `kubectl describe pod su-se-bakover`

## Dependabot

Vi bruker Github sin innebygde dependabot: https://github.com/navikt/su-se-bakover/network/updates
Se og `.github/dependabot.yaml`
Denne vil opprette PRs en gang i uka på dependencies som ikke kjører siste versjon.

## Snyk

Scanner etter dependencies med kjente sikkerhetshull og gir forslag til tiltak. Se https://app.snyk.io/login og logg inn
via `SSO`

### Kjør Snyk lokalt

1. Installer Snyk
2. `snyk auth`
3. `snyk test --all-sub-projects --trust-policies --policy-path=.snyk`

Evaluer innholdet i `.snyk`-fila: `snyk policy`