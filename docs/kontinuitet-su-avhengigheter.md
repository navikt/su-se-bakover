# Kontinuitet og systemavhengigheter i SU

**Status:** Foreløpig arbeidsdokument. Ikke godkjent domenedokumentasjon og ikke fastsatte kontinuitetskrav.

Dokumentet avgrenser vurderingen til SU-systemet og hvordan andre systemer kan påvirke innsending og registrering av søknader og behandling av saker. Flytene og avhengighetene nedenfor er kandidater for videre undersøkelse, ikke en full verifikasjon av kode eller driftsoppsett.

Alder og uføre vurderes samlet per flyt, etter avtalt avgrensning. Eventuelle forskjeller i nødvendige oppslag, frister eller konsekvenser beskrives som unntak der de påvirker vurderingen.

## Statusmerking

- **DOKUMENTERT** betyr at en lokal domenekontekst beskriver flyten eller nevner systemgrensen. Det bekrefter ikke oppetidskrav eller oppførsel ved nedetid.
- **ANTAKELSE** betyr at dette er en mulig avhengighet eller virkning som må undersøkes.
- **VURDERING** betyr at punktet må avgjøres av fag, systemeier eller drift. Det er ikke et vedtatt krav.

Ingen kritikalitet, RTO, RPO eller MTPD er fastsatt her. Ikke bruk utkastet alene som grunnlag for beredskap eller prioritering.

## Kandidatflyter og steg

ID-ene brukes i vurderingstabellen. Brevgenerering, journalføring og distribusjon inngår i flyten som utløser brevet. De er ikke egne hovedprosesser her. Fritekstbrev står som en egen kandidatflyt.

| Prosess-/steg-ID | Flyt og steg | Status og hva som må undersøkes |
|---|---|---|
| INN-01 | Innsending og mottak av søknad | **DOKUMENTERT:** `su-se-framover` kaller SU-backend. **ANTAKELSE:** innsendingen krever tilgjengelig kanal og backend samtidig. Avklar kanalene, kvittering til søker og hvordan søknader håndteres hvis registreringen feiler. |
| INN-02 | Registrering, journalføring og oppgave | **DOKUMENTERT:** Joark/SAF og Oppgave finnes blant backendens integrasjonsgrenser. **ANTAKELSE:** de kan inngå i mottak eller registrering. Avklar hvilke søknadskanaler og steg som bruker hvert system, og om mottatt innhold kan registreres senere. |
| SØK-01 | Opprette søknadsbehandling og hente eller registrere grunnlag | **DOKUMENTERT:** søknadsbehandling bygger på journalført søknad og oppgave. PDL/personoppslag er nevnt som integrasjonsgrense. Avklar hvilke oppslag som kreves for hvert steg, og om saksbehandler kan fortsette med allerede tilgjengelige data. |
| SØK-02 | Vilkår, grunnlag, beregning og eventuell simulering | **DOKUMENTERT:** innvilgelse krever beregning og simulering før attestering; avslag kan gå til attestering uten dette. Simuleringsproxyen er grensen mot simulering i Oppdrag. Avklar når integrasjonen må svare synkront, og om arbeidet kan lagres og fortsette senere. |
| SØK-03 | Attestering, vedtak og utbetaling | **DOKUMENTERT:** ved iverksettelse opprettes vedtak, og innvilgelse kan sende utbetaling til Oppdrag. Brev og videre dokumentflyt følger behandlingen når den skal sende brev. Avklar feil og omstarter mellom lagring, publisering, kvittering og brevflyt. |
| REV-01 | Opprette revurdering og hente eller oppdatere grunnlag | **DOKUMENTERT:** revurdering vurderer tidligere vedtaksdata for en periode. **ANTAKELSE:** behovet for eksterne oppslag varierer med årsak og grunnlag. Kartlegg dette per revurderingsårsak. |
| REV-02 | Vurdere, beregne og simulere revurdering | **DOKUMENTERT:** flyten går gjennom beregning og simulering. Avklar hvilke eksterne data som er nødvendige, og om de kan gjenbrukes eller må hentes på nytt. |
| REV-03 | Attestere, iverksette og sende eventuell utbetalingsendring | **DOKUMENTERT:** iverksettelse kan opprette nytt vedtak, sende utbetalingsendring og utløse brevflyt. Avklar håndtering av avbrudd nær utbetalings- eller andre frister. |
| ST-01 | Stanse eller gjenoppta ytelse | **DOKUMENTERT:** stans og gjenopptak er egne utbetalingsoperasjoner med krav til siste oversendte utbetaling. **VURDERING:** kartlegg konsekvens og handlingsrom ved nedetid særskilt, også ved tidsnære utbetalinger. |
| KLA-01 | Registrere og behandle klage | **DOKUMENTERT:** klage registreres med journalpost, oppgave og mottaksdato. Avklar hvilke systemer som må være tilgjengelige for mottak, fristregistrering og intern behandling. |
| KLA-02 | Attestere og oversende klage til klageinstansen | **DOKUMENTERT:** en klage som skal oversendes, går til attestering før oversending; Kabal er nevnt som integrasjonsgrense. Avklar om oversending krever svar der og da, og hvordan feil, returhendelser og ny overføring håndteres. |
| REG-01 | Regulering, inkludert eksterne beløp og utbetalingspublisering | **DOKUMENTERT:** regulering kan bruke AAP- og Pesys-data og kan sende utbetaling. Automatisk regulering har en egen retry-strategi ved publiseringsfeil. Avklar kjøreplan, datatilgjengelighet og behandling av forsinkede eller manglende data. |
| BREV-01 | Fritekstbrev, journalføring og distribusjon | **DOKUMENTERT:** dokumentflyten skiller mellom generering, lagring, journalføring og distribusjon. PDF-generator, Joark/SAF og distribusjon er kandidater for vurdering. Avklar om brev kan lagres og journalføres senere, og hvordan distribusjonsforsøk gjenopptas. |

## Vurder hvert system per steg

Bruk én rad per system og prosess-/steg-ID. Ikke gi et system én samlet kritikalitet hvis virkningen varierer mellom steg. Fyll ut tabellen med belegg fra kode, konfigurasjon, driftsrutiner og faglige krav før dere trekker konklusjoner.

| Prosess-/steg-ID | System eller integrasjon | Hva stopper ved nedetid? Hva kan fortsette? | Synkront krav eller utsatt etterarbeid? | Hvilke data finnes allerede i SU? | Feil, retry og trygg gjenopptakelse | Nær utbetalingsfrist eller annen frist | Status/belegg |
|---|---|---|---|---|---|---|---|
| INN-01–INN-02 | `su-se-framover`, Joark/SAF, Oppgave | Avklar separat om søknaden kan mottas, kvitteres, journalføres og få oppgave. Ikke slå disse stegene sammen. | Avklar hvor svaret må foreligge før søker eller saksbehandler kan gå videre. | Avklar om søknadsinnhold og mottakstidspunkt er varig lagret før hvert eksternt kall. | Avklar duplikatvern, retry, kø eller manuell etterregistrering. | Undersøk hvordan faktisk mottakstidspunkt og eventuelle frister bevares. | **ANTAKELSE**; systemgrensene er nevnt i domenekonteksten, men avhengighetsrekkefølgen er ikke verifisert her. |
| SØK-01–SØK-03 | PDL/personoppslag, Oppgave, dokument- og brevflyt | Finn hvilke oppslag som blokkerer vurderingen, og hvilke oppgaver eller brev som kan vente uten å miste saksgrunnlag. | Avklar per oppslag og per dokumentsteg. | Skil mellom lagrede saksdata og data som må være ferske på behandlingstidspunktet. | Avklar feilvisning, retry, gjenopptak og hvordan delvis gjennomført behandling oppdages. | Vurder både søknadsfrister og mulige konsekvenser for sårbare søkere. | **ANTAKELSE**; kartlegg konkrete kall og krav. |
| SØK-02–SØK-03, REV-02–REV-03, ST-01, REG-01 | Simulering, Oppdrag og kvitteringer | Skill mellom å beregne, simulere, lagre og publisere. Avklar også hva Oppdrag gjør med oppdrag det allerede har mottatt mens SU er nede. | **DOKUMENTERT:** simulering inngår før aktuelle iverksettelser; publisering og kvittering er egne steg. Synkrone krav og køegenskaper må undersøkes. | Avklar hvilke simuleringsresultater, utbetalingsoppdrag og statuser SU har lagret. | Undersøk idempotens, retry, kvitteringsbehandling og risiko for dobbelt eller manglende oversending. | Avklar tiltak og ansvar ved avbrudd nær planlagt utbetaling eller andre frister. | **DOKUMENTERT** flytoversikt; integrasjonsoppførsel og driftsavtaler må verifiseres. |
| REV-01–REV-03, REG-01 | AAP, Pesys og andre eksterne grunnlagsdata | Avklar hvilke behandlinger som faktisk trenger hvert system, og om saksbehandler kan arbeide med allerede innhentede data. | Avklar ferskhetskrav og om innhenting kan skje før eller etter andre steg. | Registrer kilde, periode, tidspunkt og eventuelle feilkoder som er tilgjengelige i SU. | Avklar om manglende data skal gi venting, manuell vurdering eller ny innhenting. | Vurder om forsinkelsen påvirker rettigheter eller frister, ikke bare produksjonsvolum. | **DOKUMENTERT:** AAP/Pesys er nevnt i reguleringskonteksten. Bruk i andre steg er **ANTAKELSE**. |
| KLA-01–KLA-02, BREV-01 og brevsteg i øvrige flyter | Joark/SAF, Kabal, PDF-generator og distribusjon | Skill mellom å produsere innhold, lagre dokument, journalføre, oversende og distribuere. Avklar hvilke steg som kan vente. | Avklar per overgang; ikke anta at alle dokumentsteg må lykkes i samme kall. | Undersøk om dokument, journalpost-ID, oversendelsesstatus og forsøk er lagret. | Avklar retry, duplikatvern, returhendelser og kontroll av at dokumentet nådde riktig mottaker. | Vurder klagefrister og andre rettslige frister særskilt. | **DOKUMENTERT:** komponentene og flytstegene er nevnt i domenekonteksten. Konkret oppførsel må verifiseres. |

### Særskilt om Oppdrag

At SU er utilgjengelig, betyr ikke automatisk at Oppdrag stanser arbeidet med oppdrag som allerede er mottatt. Undersøk mottaksbekreftelse, kø, videre behandling, kvitteringer og ansvar for oppfølging på hver side av grensen. Behandle dette som et eget kontinuitetsspørsmål, adskilt fra SU sin evne til å lage og sende nye oppdrag.

## Grunnlag for prioritering

Ikke likestill antall saker eller produksjonsvolum med alvorlighetsgrad. Vurder separat:

- hvor mange søknader eller behandlinger som blir forsinket
- arbeidstid og årsverk som trengs for å ta igjen et etterslep
- kalenderbasert tid fra mottak til avgjørelse, inkludert venting på systemer eller eksterne svar
- rettslige frister, utbetalingsdatoer og andre tidsavhengige konsekvenser
- konsekvenser for personer som er avhengige av stønaden, særlig sårbare brukere
- om forsinkelsen kan rettes opp, og om den kan føre til tap av rettighet, feil utbetaling eller manglende informasjon

Intern statistikk og saksstatistikk kan underbygge volum, daglig produksjon, restanse og behandlingstid. Statistikkleveranser er ikke en kritisk prosess i denne vurderingen. Bruk ikke produksjonsmål alene som mål på kalenderbasert behandlingstid eller konsekvens for den enkelte.

## Foreløpig volumgrunnlag

Kilde er innlimte, aggregerte resultater fra brukerens kjøring av PostgreSQL-spørringer for 1. august til 31. oktober 2026. Agenten har ikke kjørt spørringene eller kontrollert produksjonsdatabasen. Perioden har 65 mandag–fredag-dager; dette er ikke en bemanningskalender.

Det ble registrert 1 868 søknader: 1 818 digitale og 50 papirsøknader. Av disse ble 1 864 registrert mandag–fredag, tilsvarende 28,68 per slik dag. Registreringstidspunktet i SU er ikke nødvendigvis faktisk mottaksdato for papirsøknader. Mislykkede innsendingsforsøk og søknader som ennå ikke er registrert, inngår ikke.

Behandlingsuttrekket teller hver behandling ved første registrerte utfall, ikke alle arbeidsoperasjoner eller senere utfall etter retur. Alder og uføre er summert:

| Flyt | Iverksatt | Oversendt til klageinstansen | Avsluttet eller avbrutt | Totalt |
|---|---:|---:|---:|---:|
| Søknadsbehandling | 1 861 | 0 | 73 | 1 934 |
| Revurdering | 641 | 0 | 110 | 751 |
| Gjenopptak | 369 | 0 | 2 | 371 |
| Stans | 457 | 0 | 2 | 459 |
| Klage | 31 | 26 | 14 | 71 |
| Regulering av grunnbeløp | 22 | 0 | 3 | 25 |
| Tilbakekreving | 2 | 0 | 4 | 6 |
| **Sum** | **3 383** | **26** | **208** | **3 617** |

Stansene i dette uttrekket er merket automatiske. De øvrige radene er merket manuelle. Dette beskriver statistikkens behandlingsmetode, ikke målt arbeidsinnsats. Perioden dokumenterer ikke volumet ved den ordinære årlige reguleringen.

Omtrent 26,2 søknadsbehandlinger ble iverksatt per mandag–fredag-dag, beregnet ved å summere de avrundede gjennomsnittene for alder og uføre. Ikke divider hele periodens antall på 65 for å gjenskape dette målet: totalantallet inkluderer også helgeaktivitet.

**VURDERING:** Tallene underbygger omfanget av mulig utsatt produksjon, men fastsetter ikke kritikalitet eller akseptabel avbruddstid. Antall registrerte søknader og antall søknadsvedtak gjelder ikke nødvendigvis samme søknader, og differansen er ikke et mål på restanseendring.

Statistikkens `mottatt_tid` settes i dagens mapper til behandlingens opprettelsestid. Medianene fra uttrekket beskriver derfor forløp fra dette tidspunktet til første utfall, ikke nødvendigvis fra faktisk søknadsmottak. De måler ikke aktiv arbeidstid. Gruppevise medianer kan ikke summeres eller gjennomsnittsberegnes til en felles median.

Gjennomsnittsvolum brukes som grunnlag for omfang. Aktivitetstopper og aldersfordeling inngår ikke i den avtalte vurderingen. Før konsekvenser og tidskrav vurderes, trengs særlig relevante frister og avbruddstidspunkt, og en verifisert oversikt over hvilke steg hver integrasjon blokkerer. Bemanning, faktisk arbeidstid og tilgjengelig ekstrakapasitet trengs dersom innhenting av etterslep skal tallfestes.

### Åpne behandlinger i statistikken

Kilde er brukerens innlimte resultat fra restansespørringen, mottatt 11. november 2026. Tallene er summert på tvers av alder og uføre og viser behandlinger med siste statistikkstatus utenfor `IVERKSATT`, `AVSLUTTET`, `AVBRUTT` og `OVERSENDT`.

| Flyt | Åpne behandlinger |
|---|---:|
| Søknadsbehandling | 662 |
| Revurdering | 277 |
| Tilbakekreving | 212 |
| Klage | 37 |
| Regulering av grunnbeløp | 19 |
| Gjenopptak | 1 |
| **Sum** | **1 208** |

Av disse er 164 registrert til attestering: 130 søknadsbehandlinger, 32 revurderinger og 2 tilbakekrevingsbehandlinger. Uttrekket inneholder ingen åpne stansbehandlinger.

Dette er statistikkens registrerte beholdning, ikke en kontroll mot de operative behandlingstabellene. Det er ikke avklart om alle radene fortsatt representerer reelt utestående arbeid. Søknader uten opprettet behandling og klager som allerede er oversendt til klageinstansen, inngår ikke i denne beholdningen. Alderskolonnene fra uttrekket brukes ikke videre.

## Historisk underlag

En verdivurdering datert 7. mai 2025 er historisk underlag, ikke dokumentasjon på dagens situasjon. Opplysninger derfra om on-prem, 24 timer, moderat klassifisering, sosialhjelp som reserve eller andre forhold må verifiseres før noen bruker dem som nåværende fakta eller krav.

## Uavklarte punkter

- Hvilke søknadskanaler finnes, og når regnes en søknad som mottatt og registrert?
- Hvilke integrasjoner er nødvendige i hvert steg, og hvilke kall kan utsettes?
- Hvilke data kan SU bruke etter nedetid, og hvilke data må hentes på nytt?
- Hvilke retry-, kø-, duplikat- og gjenopptaksmekanismer gjelder per integrasjon?
- Hvordan håndterer Oppdrag oppdrag det allerede har mottatt mens SU er utilgjengelig?
- Hvilke lovbestemte frister, utbetalingsfrister og konsekvenser for brukere skal styre prioriteringen?
- Hvilke dagens driftsforhold og klassifiseringer gjelder? Verdivurderingen fra 7. mai 2025 må ikke brukes uten ny bekreftelse.
- Hvilke kilder kan dokumentere volum, daglig produksjon, restanse, arbeidstid og kalenderbasert behandlingstid hver for seg?

## Kildegrunnlag og avgrensning

**DOKUMENTERT som kart, ikke full kodeverifikasjon:** `.github/domenekontekst/systemoversikt.md`, `saksgangen.md`, `behandling.md`, `utbetaling.md`, `brev.md`, `regulering.md` og `eksterne-repos.md`. De beskriver kjente flyter og nevner integrasjonsgrenser, men fastsetter ikke kontinuitetskrav eller kritikalitet.

Volumtolkningen bygger også på `database/.../statistikk/SøknadStatistikkRepoImpl.kt`, `domain/.../statistikk/SakStatistikk.kt` og `service/.../statistikk/StatistikkVisningService.kt`. De er kontrollert som grunnlag for spørringene og tolkningen, ikke som bevis på produksjonsdataenes fullstendighet.

Dette utkastet legger ikke til logg eller annen dokumentasjon; repositoryets historikkregler krever ikke en egen loggoppføring for et ordinært arbeidsdokument.
