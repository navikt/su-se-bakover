# Eksterne repoer og systemgrenser

**Status:** `verified` for radene under «Verifiserte repoer» og runtime-grensene som
er bekreftet lokalt. Repositorydetaljer som ikke kan leses, er `unresolved`.

Denne filen beskriver repositoryene og systemgrensene rundt `su-se-bakover`. Ikke
gjett ansvar ut fra repositorynavn alene.

## Verifiserte repoer

| Repository | Rolle |
|---|---|
| [`navikt/su-se-framover`](https://github.com/navikt/su-se-framover) | Frontend/BFF som autentiserer personlige brukere og kaller `su-se-bakover` med on-behalf-of-token |
| [`navikt/su-se-bakover`](https://github.com/navikt/su-se-bakover) | Backend, domenelogikk, persistens og integrasjoner for saksbehandling |
| [`navikt/su-pdfgen`](https://github.com/navikt/su-pdfgen) | PDF-generator med maler for supplerende stønad. `PdfClient` sender typed `PdfInnhold` serialisert som JSON til riktig mal. |

## Verifiserte runtime-grenser og repositorymapping

| Runtime-applikasjon | Repository | Verifisert relasjon |
|---|---|---|
| `supstonad-proxy` | [`navikt/supstonad-proxy-fss`](https://github.com/navikt/supstonad-proxy-fss) | `su-se-bakover` kaller `/simulerberegning` med OBO-token eller systemtoken. Repoets deployment bruker runtime-navnet `supstonad-proxy`, og proxyen er grensen mot simulering i Oppdrag. |
| `supstonad-historisk` | [`navikt/historisk-exodus-supstonad`](https://github.com/navikt/historisk-exodus-supstonad) | `SupstonadHistoriskClient` bruker systemtoken og henter paginerte uttrekk fra historiske tabeller. Repoets deployment bruker runtime-navnet `supstonad-historisk`. |

## Eksterne systemer sett fra backend

Koden har klienter eller publisher-grenser mot blant annet PDL/personoppslag,
Oppgave, Joark, SAF, Kabal, Pesys, AAP, KRR, skjerming, simulering og Oppdrag.
Dette er systemgrenser og betyr ikke nødvendigvis at hvert system har et repository
eid av SU-teamet.

## Avgrensning

`vault-iac` er uttrykkelig utenfor denne kunnskapsbasen.

Repository legges først inn som verifisert når ansvar og faktisk relasjon til
`su-se-bakover` er kontrollert mot repositoryets kode, README eller deployment.
En outbound-regel eller klient beviser en runtime-grense, men ikke alene hvilket
repository som inneholder kilden. Repositorymapping skal kontrolleres på begge sider.

## Kilder

- `README.md`
- `.nais/dev-gcp.yaml`
- `.nais/prod-gcp.yaml`
- `dokument/infrastructure/.../client/PdfClient.kt`
- `client/.../oppdrag/simulering/SimuleringProxyClientGcp.kt`
- `client/.../historisk/SupstonadHistoriskClient.kt`
