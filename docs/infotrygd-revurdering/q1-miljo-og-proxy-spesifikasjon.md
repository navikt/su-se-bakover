# Q1-miljø og egen Oppdrag-proxy

Status per 13. november 2026: Spesifikasjon for miljøoppsett og tilgangsbestillinger.
Eksisterende konfigurasjon og Git-historikk er kontrollert. Foreslåtte endringer er
ikke implementert eller deployet, og kjørende tjenester er ikke kontrollert.

## Mål og avgrensning

Historisk Infotrygd skal testes i eksisterende `su-se-bakover-q1`, med egen
database, økonomi q1 og persondata fra PDL q1. Felles testtjenester gjenbrukes.
Vi oppretter ikke egne instanser av skjerming, kodeverk, kontaktregister eller
PDF-generator.

Første miljøleveranse omfatter import, oppslag, revurdering, simulering, utbetaling
og brev. Tilbakekrevingsbehandling er utenfor første leveranse. Pensjon, AAP,
skatteoppslag og klage inngår ikke. Direkte adresseoppslag gjennom Regoppslag
bestilles ikke nå. Behovet må vurderes dersom frontend bruker adressesjekken.
Dokdistfordeling bruker selv Regoppslag internt; denne avhengigheten beholdes hos
tjenesteeieren.

Dette dokumentet åpner ikke historisk iverksettelse. Sperren i dagens route skal
beholdes til den faktiske utbetalingsflyten er klar. Meldingsinnhold og
iverksettelse beskrives i
[historisk-utbetaling-spesifikasjon.md](historisk-utbetaling-spesifikasjon.md).

## Bekreftet historikk for økonomi

Backend brukte tidligere disse q1-innstillingene:

| Innstilling | Historisk q1-verdi |
| --- | --- |
| Direkte simulering, tidligere `SIMULERING_URL` | `https://cics-q1.adeo.no/oppdrag/simulerFpServiceWSBinding` |
| Tilbakekreving, `TILBAKEKREVING_URL` | `https://cics-q1.adeo.no/oppdrag/K231CW80` |
| `MQ_QUEUE_MANAGER` | `MQLS02` |
| `MQ_HOSTNAME` | `b27apvl220.preprod.local` |
| `MQ_PORT` | `1413` |
| `MQ_CHANNEL` | `Q1_SU_SE_BAKOVER` |
| `MQ_SEND_QUEUE_UTBETALING` | `QA.Q1_231.OB04_OPPDRAG_XML` |
| `MQ_REPLY_TO` | `QA.Q1_SU_SE_BAKOVER.OPPDRAG_KVITTERING` |
| `MQ_SEND_QUEUE_AVSTEMMING` | `queue:///QA.Q1_234.OB29_AVSTEMMING_XML?targetClient=1` |
| `MQ_TILBAKEKREVING_MOTTAK` | `QA.Q1_SU_SE_BAKOVER.OPPDRAG_KRAVGRUNNLAG` |

Det gamle FSS-manifestet ble fjernet 14. januar 2026. Konfigurasjonen for direkte
simulering fra backend ble fjernet 15. januar. Vi skal ikke gjeninnføre denne
klienten; dagens backend bruker proxyen.

Begge endringene til q2 ble merget 2. november 2026:

- Backend-endringen byttet MQ-kanal, utbetalingskø, kvitteringskø, avstemming og
  kravgrunnlagskø i ordinært dev-oppsett fra q1 til q2. Eget
  `.nais/dev-gcp-q1.yaml` har fortsatt q1-verdiene.
- Proxy-endringen byttet både simulering og tilbakekreving fra `cics-q1` til
  `cics-q2`.

Historikken dokumenterer konfigurasjonen, ikke at endepunktene og køene fortsatt
er operative eller at servicebrukeren har nødvendige rettigheter.

Kilder:

https://github.com/navikt/su-se-bakover/pull/3078

https://github.com/navikt/supstonad-proxy-fss/pull/28

https://github.com/navikt/su-se-bakover/commit/f3aec49c6d3d89d73b802939914c852ad36ef0c9

https://github.com/navikt/su-se-bakover/commit/6de67e0ea1b98624804f131245e315d1826e3880

## Foreslått q1-proxy

Vi bruker samme proxykode og image-format, men en separat Nais-applikasjon med
egen identitet og konfigurasjon. Det trengs ikke en fork av proxyproduktet eller
en alternativ SOAP-klient i backend.

```text
su-se-bakover-q1 i dev-gcp
  | Entra-token for q1-proxyen
  v
supstonad-proxy-q1 i dev-fss
  | eksisterende SOAP- og tokenutveksling
  v
Oppdrag q1

su-se-bakover i dev-gcp
  v
supstonad-proxy i dev-fss
  v
Oppdrag q2, uendret
```

### Identitet og konfigurasjon

Verdiene i denne tabellen er forslag til den nye applikasjonen, ikke eksisterende
ressurser:

| Felt | Foreslått verdi |
| --- | --- |
| Repository | `navikt/supstonad-proxy-fss` |
| Nais-applikasjon | `supstonad-proxy-q1` |
| Cluster og namespace | `dev-fss`, `supstonad` |
| Nytt manifest | `.nais/dev-q1.yaml` |
| Ny ingress | `https://supstonad-proxy-q1.dev-fss-pub.nais.io` |
| Backendens `SUPSTONAD_PROXY_CLIENT_ID` | `api://dev-fss.supstonad.supstonad-proxy-q1` |
| `SIMULERING_OPPDRAG_URL` | `https://cics-q1.adeo.no/oppdrag/simulerFpServiceWSBinding` |
| `TILBAKEKREVING_URL` | `https://cics-q1.adeo.no/oppdrag/K231CW80` |

Det nye manifestet bygger på eksisterende `.nais/dev.yaml`. Behold etablerte
helseendepunkter, logging, tenant, gruppeclaims og montering av eksisterende
testservicebruker. Ikke kopier hemmelige verdier til manifestet.

Bare `su-se-bakover-q1` i `supstonad` på `dev-gcp` skal legges til som innkommende
applikasjon i q1-manifestet. Ikke bruk jokertegn eller tillat hele namespacet.
Proxyen skal validere token mot sin egen, Nais-genererte client-ID. Dagens
`application.conf` leser allerede denne fra `AZURE_APP_CLIENT_ID`.
Backendens rolle- og persontilgang skal fortsatt kontrolleres før økonomikallet.

`Config.kt` krever `TILBAKEKREVING_URL` ved oppstart selv om første leveranse bare
tester simulering. Den må derfor peke på q1, aldri q2. At verdien er satt,
betyr ikke at tilbakekrevingsbehandling er ferdig eller godkjent for testing.
Eksisterende proxy eksponerer også tilbakekrevingsfunksjoner; en eventuell
ytterligere ruteavgrensning må besluttes eksplisitt og er ikke implementert her.

### Deployment

Legg til en egen, manuelt startet q1-workflow, foreslått navn
`.github/workflows/deploy-proxy-q1.yml`. Gjenbruk bygg-, image- og deploymønsteret
fra `deploy-proxy.yml`, men deploy bare `.nais/dev-q1.yaml` til `dev-fss`.

Ikke endre målmiljøet eller standardoppførselen i eksisterende dev- eller
produksjonsworkflow. Q1 og q2 skal kunne deployes uavhengig. En felles image-versjon
kan brukes når samme kode er ønsket i begge miljøer.

Kilde for dagens konfigurasjon og deploymønster:

https://github.com/navikt/supstonad-proxy-fss/blob/main/.nais/dev.yaml

https://github.com/navikt/supstonad-proxy-fss/blob/main/.github/workflows/deploy-proxy.yml

https://github.com/navikt/supstonad-proxy-fss/blob/main/server/src/main/kotlin/Config.kt

https://github.com/navikt/supstonad-proxy-fss/blob/main/server/src/main/resources/application.conf

## Endringer i backendens q1-manifest

Bare `.nais/dev-gcp-q1.yaml` skal endres som del av miljøkoblingen:

| Konfigurasjon | Planlagt q1-mål |
| --- | --- |
| `SUPSTONAD_PROXY_URL` | `https://supstonad-proxy-q1.dev-fss-pub.nais.io` |
| `SUPSTONAD_PROXY_CLIENT_ID` | `api://dev-fss.supstonad.supstonad-proxy-q1` |
| `PDL_URL` | `https://pdl-api-q1.dev-fss-pub.nais.io` |
| `PDL_CLIENT_ID` | `api://dev-fss.pdl.pdl-api-q1` |
| `DOKARKIV_URL` | `https://dokarkiv.dev-fss-pub.nais.io` |
| `DOKARKIV_CLIENT_ID` | `api://dev-fss.teamdokumenthandtering.dokarkiv-q1` |
| `SAF_URL` | `https://saf.dev-fss-pub.nais.io` |
| `SAF_CLIENT_ID` | `api://dev-fss.teamdokumenthandtering.saf-q1` |
| `DOKDIST_URL` | `https://dokdistfordeling-q1.dev-fss-pub.nais.io` |
| `DOKDIST_CLIENT_ID` | `api://dev-fss.teamdokumenthandtering.dokdistfordeling-q1` |
| `KODEVERK_URL` | `http://kodeverk-api.team-rocket` |

Dokarkiv og SAF bruker ingress uten q1-suffiks for q1. Dette er kontrollert mot
deres `q1-config.json`; ikke utled miljøet fra vertsnavnet alene. Dagens
`KODEVERK_URL` i q1 peker på den offentlige kodeverkadressen. Bruk felles
dev-tjeneste som allerede er konfigurert i ordinært dev-oppsett.

Oppdater relevante `accessPolicy.outbound.rules` og `external`-verter i samme
endring. Behold q1-innstillingene for IBM MQ fra tabellen over. Ikke fall tilbake
til q2 dersom q1 ikke svarer.

Ikke fjern annen oppstartskonfigurasjon automatisk fordi en funksjon er utenfor
scopet. Klientbygging og bakgrunnsjobber må kontrolleres før eksisterende
konfigurasjon, rettigheter eller jobber eventuelt fjernes.

## Tilgangsbestillinger og bidragsmåte

Alle nye forespørsler skal gjelde bare denne kalleren og bare test:

```yaml
- application: su-se-bakover-q1
  namespace: supstonad
  cluster: dev-gcp
```

Eksisterende tillatelser til `su-se-bakover` skal beholdes. Vi ber ikke om
produksjonstilgang eller utvidede personrettigheter som en del av miljøoppsettet.
Rolle- og persontilgang gjelder fortsatt.

| Tjeneste | Mål og mekanisme | Forespørsel |
| --- | --- | --- |
| PDL | `pdl-api-q1`, applikasjonstoken og token på vegne av saksbehandler | Kalleren mangler i q1-manifestet. README viser til konsumentdokumentasjonen og `#pdl`, men beskriver ikke en egen PR-prosess. Bekreft bestillingsmåte og nødvendige rettigheter. Alder bruker behandlingsnummer `B237` i dagens klient. |
| Dokarkiv | `dokarkiv-q1`, applikasjonstoken | README ber andre team lage PR i miljøets konfigurasjonsfil. Legg til kalleren i `AZURE_IAC_RULES` i `nais/q1-config.json`. Ikke endre REST-STS-listen for Entra-kall. |
| SAF | `saf-q1`, applikasjonstoken og token på vegne av saksbehandler | README ber om PR med nytt innslag i `AZURE_IAC_RULES` i `nais/q1-config.json`. Avklar nødvendige temaroller; ikke kopier brede roller fra andre konsumenter. |
| Dokdistfordeling | `dokdistfordeling-q1`, applikasjonstoken | README ber om issue for henvendelser. Be om preautorisering av kalleren i q1. Q1-konfigurasjonen bruker `ENTRA_ID_PREAUTH`. |
| Skjerming | Felles `skjermede-personer-pip` i dev-gcp | Kalleren mangler i nettverks-/applikasjonslisten. README viser til egen dokumentasjon, men angir ikke om forespørselen skal være issue eller PR. |
| Kodeverk | Felles `kodeverk-api` i dev-gcp | Kalleren mangler i listen. README og issue-oppsett viser til Team Rocket og felles kodeverk. Ingen eksplisitt regel om PR for tilgang er funnet. |
| Kontaktregister | Felles `digdir-krr-proxy` i dev-gcp | README ber uttrykkelig om issue med malen `pre-authorize-applikasjon.md`, caller-identitet og lenke til behandlingskatalogen. |
| Microsoft Graph | Eksisterende Graph-tjeneste, applikasjonstoken | Kontroller q1-appens nødvendige rettigheter for navneoppslag til brev. Dette er ikke et nytt q1/q2-miljø eller en vanlig Nais-allowlist. |

Egne applikasjoner, inkludert `su-pdfgen` og Oppdrag-proxyen, håndteres i eget
team uten eksterne tilgangsforespørsler. Proxyoppsettet er beskrevet over.
For `su-pdfgen` legges kalleren til i et dev-avgrenset manifest eller en
miljøavgrenset mal. Dagens `nais.yml` brukes både i dev og prod; ikke legg inn en
q1-regel som også deployes til prod. Dagens PDF-klient sender ikke Entra-token.

Historikktjenesten tillater allerede `su-se-bakover-q1` med eksplisitte
tabellroller. Den trenger ingen ny caller-oppføring ut fra dagens manifest.
Oppgave tas bare med hvis den valgte historiske arbeidsflyten faktisk bruker
oppgavefunksjoner.

En fork er en kopi av et repository under en annen eier. Den er bare nødvendig
for å sende en kode-PR hvis man ikke kan pushe en branch til originalrepoet og
repositoryet tillater forks. Et issue krever verken fork eller kodeendring.
Følg tjenesteeierens bestillingsmåte fremfor å opprette både issue og PR uten behov.

De sju eksterne tjenesterepoene i tabellen over har issues aktivert ved kontroll
13. november 2026. Dette bekrefter ikke at den enkelte bruker eller
automatiseringsklient får opprette issues. PDL, skjerming, kodeverk og
kontaktregisteret er private repoer. Deres fork-policy er ikke verifisert;
lesetilgang alene er ikke belegg for at en fork kan opprettes.

Kontaktregisterets eksisterende SU-forespørsel inneholder formålslenken som kan
brukes som utgangspunkt. Den gir ikke automatisk tilgang til q1-appen:

https://github.com/navikt/digdir-krr/issues/1243

https://behandlingskatalog.intern.nav.no/process/purpose/SUPPLERENDESTOENAD

Bidrags- og tilgangsveiledninger:

https://github.com/navikt/dokarkiv/blob/master/README.md

https://github.com/navikt/saf/blob/master/README.md

https://github.com/navikt/dokdistfordeling/blob/master/README.md

https://github.com/navikt/digdir-krr/blob/main/README.md

https://github.com/navikt/digdir-krr/blob/main/.github/ISSUE_TEMPLATE/pre-authorize-applikasjon.md

https://github.com/navikt/pdl/blob/master/readme.md

https://github.com/navikt/skjerming/blob/main/README.md

https://github.com/navikt/felleskodeverk/blob/main/README.md

## Rekkefølge og godkjenning

1. Send nødvendige tilgangsforespørsler etter tjenesteeiernes rutiner. Bestilling
   og godkjent PR er ikke det samme som ferdig deployet tilgang.
2. Deploy q1-proxyen uten å endre eksisterende proxy. Bekreft faktisk målmiljø,
   q1-audience, servicebrukerens rettigheter og de to helseendepunktene.
3. Koble backend q1 til den nye proxyen og q1-tjenestene når mottakertilgangene er
   deployet. Kontroller at frontend fortsatt bare bruker backend q1.
4. Bruk testpersoner med sammenhengende data i historikk, PDL q1 og Oppdrag q1.
   Historisk testmodus erstatter ikke persondataene som brev og økonomi trenger.
5. Test simulering først. Faktisk utbetaling skal ikke testes før den historiske
   iverksettelsesflyten er ferdig og godkjent.
6. Før reduksjoner prøves, avklar hvor eventuelle kravgrunnlag blir sendt og hvem
   som følger dem opp. Utsatt tilbakekrevingsbehandling betyr ikke at slike
   meldinger kan ignoreres.

Følgende skal kontrolleres av operatøren etter deployment:

- Q1-proxyen er klar, og faktisk simulering går til Oppdrag q1.
- Ordinær proxy og q2-simulering er uendret.
- Riktig q1-backend kan kalle proxyen; manglende eller feil audience avvises.
- PDL-oppslag virker både med brukertilgang og med applikasjonstoken for brev.
- PDF-generering og oppslag av saksbehandlernavn fungerer.
- Et testbrev journalføres og kan leses i q1-dokumentmiljøet.
- Distribusjon bruker samme q1-journalpost og avtalt testmottaker.
- Når utbetaling er implementert, sendes oppdraget til q1 og riktig kvittering
  knyttes til riktig utbetaling i q1-databasen.

Følg applikasjonenes egne etiketter i logger, metrikker og alarmer. Overvåk
autentiseringsfeil, tekniske simuleringsfeil, feilede kvitteringer og dokumentjobber
som ikke blir ferdige. Køansvarlig må kunne oppdage opphopning på q1-køene.
Ikke legg persondata eller rå SOAP-/MQ-meldinger i ordinær logg, issues eller PR-er.

## Rollback

Ved feil stoppes videre testing med sideeffekter. Behold q2 uendret. Rull
q1-proxyen tilbake til en kjent fungerende q1-versjon hvis en slik finnes; ellers
stopp q1-proxyen til feilen er rettet. Ikke bruk q2 som automatisk reserve.

En rollback kan ikke trekke tilbake utbetalinger eller distribusjonsbestillinger
som allerede er sendt. Disse må følges opp med de aktuelle tjenesteeierne.
Tilgangsoppføringer kan fjernes separat uten å endre eksisterende konsumenters
tilgang.
