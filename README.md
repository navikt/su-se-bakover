#su-se-bakover

##Applikasjon for saksbehandling av supplerende stønad
### Lokalt oppsett
#### Gradle
For å få tilgang til å hente ut packages for su-meldinger kreves det autentisering mot Github package registry.
Dette kan gjøres ved å spesifisere følgende variabler i `~/.gradle/gradle.properties`:
`githubUser=x-access-token"` og `githubPassword="mitt SSO enabled access token med repo+read/write packages access generert på github"`
#### Database (Postgres)
Lokal database startes med `./docker-compose up`
#### Starte applikasjon lokalt
Applikasjonen kan startes lokalt etter å ha spesifisert nødvendige miljøvariabler definert i filen `application.conf`. 
Verdier som ikke har default verdi finner man i `Vault` under team-secrets for  `supstonad`