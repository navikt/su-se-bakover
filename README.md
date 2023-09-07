# su-se-bakover

## Applikasjon for saksbehandling av supplerende stønad

### Lokalt oppsett

sjekkliste:

1. ha riktig gradle versjon (kan finnes i `build.gradle.kts` - let etter `gradleVersion =`)
2. ha riktig java versjon (kan finnes i `build.gradle.kts` - let etter `jvmTarget =`)
3. La prosjektet ditt bruke riktig java versjon (høyreklikk prosjektnavn -> `Open module settings` og set riktig java
   versjon)
    - Du kan laste ned java versjon ved bruk av SDKman
4. Ikke ha database porten i bruk av noe annet (hvis du for eksempel lastet ned postgresql via homebrew og startet den)

#### Starte applikasjon lokalt

Kan startes lokalt fra web/src/main/kotlin/.../Application.kt sin `fun main(...)`. Krever at `start-dev.sh` skriptet
i [su-se-fremover](https://github.com/navikt/su-se-framover#kj%C3%B8re-lokalt) kjører

#### Database (Postgres)

Lokal database startes med `docker compose up`

Hvis man ønsker å resette hele databasen og starte fra scratch er det enkleste å slette volumet ved å
kjøre `./resetdb.sh`

### Hvordan kunne koble til Test/prod baser fra lokal maskin

1. `sudo nano /etc/hosts`
2. Legg inn følgende:
    ```
    # NAV POSTGRES PREPROD URL
    10.183.160.87 b27dbvl009.preprod.local
    # NAV POSTGRES PROD URL
    10.53.20.112 A01DBVL011.adeo.no
    ```
3. lagre

#### Backup og import av database

For backup av testmiljøets database, må du hente brukernavn og password (admin - se Koble til database i preprod/prod)
fra vault.

Backup

1. For å ta en kopi av databasen kan du bruke følgende script i
   terminalen `pg_dump -U username -W -h remote_hostname -p remote_port -F c database_name > filnavn.dump`
    - eksempel for lokalt `pg_dump -U user -h localhost -p 5432 -F c supstonad-db-local > filnavn.dump`
        - password `pwd`
    - eksempel for
      test `pg_dump -U brukernavnFraVault -h b27dbvl009.preprod.local -p 5432 -F c supstonad-db-dev > filnavn.dump`

Import

1. For å legge inn dataen i basen kan du kjøre
   følgende `pg_restore -h remote_host -p remote_port -U brukernavn -d database_name filnavn.dump`
    - eksempel for lokalt `pg_restore -c --if-exists -h localhost -p 5432 -U user -d supstonad-db-local filnavn.dump`
        - password er `pwd`
        - Terminal vil kanskje gi deg en del 'errors' pga roller etc. Disse kan du se bort ifra
        - Hvis du samtidig inspecter filen (f.eks nano) vil du kanskje se en del encoding issues. Disse var heller ikke
          et problem ved import

### Troubleshooting lokal/embedded postgres

Dersom man får feilene
- `running bootstrap script ... 2022-08-30 16:10:20.342 CEST [53606] FATAL:  could not create shared memory segment: Cannot allocate memory`
- `java.lang.IllegalStateException: Process [/var/folders/l_/c1b_7t2n39j48k7sbpp54zy00000gn/T/embedded-pg/PG-8eddc1e460ca1c5597350c162933683c/bin/initdb, -A, trust, -U, postgres, -D, /var/folders/l_/c1b_7t2n39j48k7sbpp54zy00000gn/T/epg3835758492450081687, -E, UTF-8] failed`

kan du prøve disse 2 mulighetene:

1. Hvis du ikke har; legg inn
   * `export LC_CTYPE="en_US.UTF-8"`
   * `export LC_ALL="en_US.UTF-8"`

   i din terminal profil (f.eks .zshrc). Dersom det ikke fikser problemet, kan du også prøve punktet over.

2. øke shared memory:
   * `sudo sysctl kern.sysv.shmmax=104857600` eller en annen ønsket verdi, for eksempel `524288000`
   * `sudo sysctl kern.sysv.shmall=2560` eller en annen ønsket verdi, for eksempel `65536`

#### Autentisering

su-se-framover tar seg av autentisering (backend for frontend (BFF)) og kaller su-se-bakover med on-behalf-of tokens (
per bruker).

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

For da å bruke gruppe-claimet som kommer fra Azure må man også endre implementasjonen av `getGroupsFromJWT`
i [./web/src/main/kotlin/no/nav/su/se/bakover/web/Extensions.kt]().

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

Applikasjonen vil selv generere gyldige jwt-tokens for inkommende kall. Ved behov kan innholdet i disse konfigureres
i `JwtStub.kt`.

Merk at tokens som genereres automatisk av appen kun vil være gyldige for den aktuelle instansen som genererte den.
For å unngå at man må starte helt fra "login" når applikasjonen restartes, kan man heller slette gamle `access_token`fra
browser,
dette fører til at det gjenværende `refresh_token` benyttes til å generere nye.

#### Med innlogging i Azure

Ved behov for "ekte" innlogging mot Azure kan dette aktiveres for lokal utvikling ved å endre konfigurasjonen i
filen `AuthenticationConfig.kt`.
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

- IntelliJ: https://plugins.jetbrains.com/plugin/18321-spotless-gradle
  og https://github.com/ragurney/spotless-intellij-gradle
- VS Code: https://marketplace.visualstudio.com/items?itemName=richardwillis.vscode-spotless-gradle

### Hvordan kjøre Spotless:

* Hvis du har pluginen; Meny -> Code -> Reformat Code with Spotless
* Det finnes en gradle task; `spotlessApply` som kan keybindes eller knyttes til on-save (må muligens bruke
  FileWatcher-plugin)

### Hvordan diagnosere Spotless:

`./gradlew spotlessDiagnose`

### Hvis du fremdeles vil bruke ktlint direkte:

* Installer ktlint https://github.com/pinterest/ktlint/releases/ (inntil plugin støtter dette. Kan vurdere legge binæren
  i prosjektet)
* ktlint applyToIDEAProject (denne overstyrer da IntelliJ sine formateringsregler så godt det går.)

### Kjente feil:

* Kjør `rm ./.git/hooks/pre-commit ./.git/hooks/pre-push` for å slette gamle hooks hvis den feiler ved commit

## Metrics

Vi bruker Prometheus for å samle inn metrikker.
Se https://doc.nais.io/observability/metrics.

## Alerts

`alerts.yml` deployes automatisk vha. `.github/workflows/alerts-deploy.yml` og
benytter [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/) for å sette opp
alerts basert på metrikker.
Se https://doc.nais.io/observability/alerts.

### Experiment writing prometheus queries and view graphs

preprod: https://prometheus.dev-fss.nais.io
prod: https://prometheus.prod-fss.nais.io

### View ongoing alerts and manage silences

preprod: https://alertmanager.dev-fss.nav.cloud.nais.io/#/alerts
prod: https://alertmanager.prod-fss.nav.cloud.nais.io/#/alerts

### Kubectl commands

* View alerts: `kubectl --namespace=supstonad get alerts`
* Describe alert: `kubectl --namespace=supstonad describe alert su-se-bakover`
* Delete alert: `kubectl --namespace=supstonad delete alert su-se-bakover`
* Deploy new alert: Just run the `alerts-deploy.yml` GitHub actions workflow

## Upgrade gradlew

1. Find the lastest version of gradle, e.g. by checking here: https://gradle.org/releases/
2. Then run `./gradlew wrapper --gradle-version <version>`
3. In `build.gradle.kts` you also have to change the gradle version. E.g. search for `gradleVersion =`

## Upgrade kotlin/ktor

1. in `gradle.properties` look for `ktorVersion` or `kotlinVersion`
2. Replace the version with the one you want
3. Load gradle changes

## Upgrade java version

1. In `build.gradle.kts` and search for `jvmTarget = `
2. In `.github/workflows/*.yml` and search for `java-version`
3. In `Dockerfile` replace `FROM ghcr.io/navikt/baseimages/temurin:<version>`

## Kubernetes

#### Hvis du har brukt gamle måten før, kan du gjøre disse tingene:
1. Fjern KUBECONFIG pathen i shellet ditt
2. fjern kubeconfig-repoet `navikt/kubeconfig`

#### Et par ting du må gjøre hvis du har lyst til å gjøre noe snacks i kubernetes. 
1. Du må ha nais-cli - https://docs.nais.io/cli/
2. Du må også ha gcloud-cli (kanskje?) - https://cloud.google.com/sdk/docs/install
3. log inn i gcloud - `gcloud auth login --update-adc` 
4. Kjør `nais kubeconfig` - Dette vil hente ned alle Clusterene.
   1. merk: Kjør `nais kubeconfig -io` for å hente on-prem clusters også (dev-fss/prod-fss)
5. Sett context du hr lyst til å bruke
   1. for eksempel: `kubectl config use-context dev-fss`
   2. Hvis du må sette namespace i tillegg: `kubectl config set-context --current --namespace=supstonad`
6. Nå skal du kunne kjøre `kubectl get pods` og få listet alle podene våre

## IBM MQ (oppdrag/utbetaling/simulering)

Dersom man ønsker å inspisere meldingene i preprod-miljøet:

* Via VmWare (utvikler image): http://a34drvw006.devillo.no:8000/ navn på hostname/lø ligger i `nais-dev.json`.

I preprod: Dersom man ønsker å slette/endre/legge til meldinger eller flytte fra backoff til hovedkø, kan man
bruke https://github.com/jmstoolbox/jmstoolbox

* Finn hostname/kø i `nais-dev.json`.
* Ping hostname for å få IP `10.53.17.118`
* Finn username/password i vault: https://vault.adeo.no/ui/vault/secrets/serviceuser/show/dev/srvsupstonad
* Legg inn channel: `Q1_SU_SE_BAKOVER`
* Legg inn queueManager `MQLS02`
* Finn en passende Ibm mq jar: https://github.com/jmstoolbox/jmstoolbox/wiki/2.1-Setup-for-IBM-MQ

## Dependabot

Vi bruker Github sin innebygde dependabot: https://github.com/navikt/su-se-bakover/network/updates
Se og `.github/dependabot.yaml`
Denne vil opprette PRs en gang i uka på dependencies som ikke kjører siste versjon.

## Koble til database i preprod/prod

1. Via naisdevice (fungerer kun med nav image) eller via vmware
2. Finner settings i `nais-dev.json` og `nais-prod.json`.
3. Hent brukernavn/passord på https://vault.adeo.no/ui/vault/secrets (login med oidc). Åpne konsollen og velg ønsket
   rolle:
    1. `vault read postgresql/preprod-fss/creds/supstonad-db-dev-readonly`
    2. `vault read postgresql/preprod-fss/creds/supstonad-db-dev-user`
    3. `vault read postgresql/preprod-fss/creds/supstonad-db-dev-admin`
    4. `vault read postgresql/prod-fss/creds/supstonad-db-prod-readonly`
    5. `vault read postgresql/prod-fss/creds/supstonad-db-prod-user`
    6. `vault read postgresql/prod-fss/creds/supstonad-db-prod-admin`

## Migrerte data fra Infotrygd

Det er opprettet en replikeringsdatabase med data fra den eksisterende su-alder løsningen i Infotrygd (test).
Tilgang til databasen kan bestilles via identrutinen.

Tilkobling: `jdbc:oracle:thin:@a01dbfl033.adeo.no:1521/infotrygd_suq` (personlig tilgang testet ok med SQLDeveloper i
VDI)

## Select og se PDF i database

1. `SELECT encode(generertdokument, 'base64') FROM dokument where revurderingId = '<REPLACE_ME>';`
2. `base64 --decode -i input.base64 -o output.pdf`