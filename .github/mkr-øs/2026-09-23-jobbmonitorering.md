# Jobbmonitorering av kritiske dataflyter

| Felt | Verdi |
|---|---|
| Dato | 23. september 2026 |
| Frekvens | Tertialsvis |
| Ansvarlig rolle | Teknologileder |
| Løpende oppfølging | Alle utviklere i team Supplerende stønad |
| Teammedlemmer | [Nais-konsollet](https://console.nav.cloud.nais.io/team/supstonad/members) |

## Formål og avgrensning

Kontrollen skal avdekke feil i dataflyter som kan påvirke økonomiske
transaksjoner, regnskap, rapportering eller føre til andre vesentlige feil. Andre
vesentlige feil omfatter blant annet manglende journalføring, manglende
brevdistribusjon og klager som ikke blir behandlet.

Vi klassifiserer konsekvensen slik:

- **Direkte økonomisk:** Feilen kan opprette, endre, stanse eller feilutbetale en
  ytelse eller et tilbakekrevingskrav.
- **Indirekte økonomisk:** Feilen kan forsinke eller hindre en behandling som
  senere kan påvirke økonomi.
- **Annen vesentlig konsekvens:** Feilen påvirker for eksempel rettssikkerhet eller
  arkivplikt uten å endre økonomi direkte.
- **Kun rapportering:** Feilen påvirker statistikk eller rapportering, men ikke
  økonomiske transaksjoner.

## Kontrollmekanismer

Alle `ERROR`-logger fra `su-se-bakover` utløser et felles Loki-varsel. Varselet
ser etter nye `ERROR`-logger de siste 15 minuttene og sendes til Slack-kanalen
`#su_alerts_prod`. Alle utviklere i team Supplerende stønad følger kanalen og har
ansvar for å undersøke varsler.

`GET /api/drift/jobber/status` viser siste kjøring, status, feilmelding og
forventet intervall for registrerte jobber. Endepunktet sender ikke varsler. Vi
bruker det til å kontrollere at jobbene har kjørt innen forventet tid.

For samlejobbene `KvitteringshendelserJobb`, `Tilbakekreving` og `Dokument` betyr
`FULLFØRT` at jobbsyklusen kjørte ferdig. Det betyr ikke at alle hendelsene ble
behandlet uten feil. En enkelthendelse kan logge `ERROR`, bli stående for ny
behandling og samtidig la samlejobben fortsette.

Vi trenger ikke en egen samlet visning av antall utestående hendelser per
konsument så lenge hver feil:

1. logger `ERROR` til Slack
2. beholder hendelsen for ny behandling
3. ikke hindrer at andre hendelser behandles

Denne vurderingen må tas opp på nytt hvis en konsument kan feile uten `ERROR`,
eller hvis utestående hendelser kan blokkere resten av behandlingen.

## Kontroll mot de særskilte kravene

### Feil som ikke nødvendigvis oppdages av en person

Automatiske flyter må ha en teknisk kontroll når ingen saksbehandler ser at data
mangler. Dette gjelder særlig:

- utbetalinger som er sendt til OS uten at det kommer kvittering
- klager som er sendt til Klageinstansen uten at det kommer svar
- avstemming som ikke blir kjørt fordi kjøreplanen mangler datoer
- eksterne oppslag som feiler mens batchen fortsetter
- personhendelser som blir stående uten PDL-vurdering
- hendelser som aldri produseres til et Kafka-topic
- feil i automatiske overføringer til rapportering

Flytene vi selv starter eller behandler, har kontroll i applikasjonen.
Fullstendig fravær av Kafka-hendelser kan ikke oppdages av konsumenten alene.
Produsenten må overvåke at hendelsene blir produsert.

### Feilmelding der prosessen fortsetter

Flere jobber fortsetter med neste hendelse når én hendelse feiler. Dette hindrer
at én feil stopper hele batchen. Feilen er dekket når den logger `ERROR` til Slack
og den feilede hendelsen beholdes for ny behandling eller manuell oppfølging.

Dette gjelder blant annet:

- utbetalingskvitteringer
- tilbakekreving
- dokumenthåndtering
- fradragssjekk
- personhendelser
- institusjonsopphold
- utfall fra Klageinstans

Jobbstatus alene er ikke kontrollbevis for disse flytene. Vi må også kontrollere
`ERROR`-varslene.

## Kontrollmatrise

| Dataflyt | Konsekvens | Hva kan gå uoppdaget? | Kontrollmekanisme | Fortsetter etter feil? | Retry eller oppfølging |
|---|---|---|---|---:|---|
| Utbetaling til OS | Direkte økonomisk | Ikke noe kjent ved publiseringsfeil | MQ-feil gir rollback og `ERROR` til Slack | Nei | Iverksettingen fullføres ikke |
| Kvittering fra OS | Direkte økonomisk | En kvittering som aldri kommer | MQ- og behandlingsfeil gir `ERROR`. En egen jobb kontrollerer hver time i Oppdrags åpningstid om en utbetaling har ventet minst to åpningstimer | Ja | MQ eller hendelsesbehandlingen forsøker igjen |
| Ferdigstilling etter utbetalingskvittering | Direkte økonomisk | Ikke noe kjent | Feil på enkelthendelsen gir `ERROR` | Ja | Hendelsen beholdes for ny behandling |
| Grensesnittsavstemming mot OS | Direkte økonomisk kontroll | Manglende eller avvikende transaksjoner | Daglig avstemming. Feil gir `ERROR` og `FULLFØRT_MED_FEIL` | Ja, mellom fagområder | Ny kontroll og manuell oppfølging ved avvik |
| Konsistensavstemming mot OS | Direkte økonomisk kontroll | Avvik mellom våre utbetalinger og OS | Avstemming på datoer fra økonomiområdet. Feil gir `ERROR` og feilstatus | Ja, mellom fagområder | Jobben kjører flere ganger samme dag |
| Kjøreplan for konsistensavstemming | Indirekte økonomisk | Kjøreplanen kan gå tom uten at dagens jobb feiler | Jobben kontrollerer i Oppdrags åpningstid og logger `ERROR` når siste planlagte dato er mindre enn to måneder frem i tid | Ja | Nye datoer innhentes fra økonomiområdet |
| Kravgrunnlag fra OS | Direkte økonomisk | Et kravgrunnlag som ikke blir behandlet | Koblings- og behandlingsfeil gir `ERROR` | Ja | Hendelsen beholdes og forsøkes igjen |
| Tilbakekreving | Direkte eller indirekte økonomisk | Feil på en enkelthendelse kan skjules av at samlejobben er `FULLFØRT` | Hver konsument logger `ERROR` | Ja | Hendelsen beholdes normalt for ny behandling |
| Fradragssjekk mot Pesys og AAP | Indirekte økonomisk | Eksterne oppslag kan feile mens resten av batchen fullføres | Eksterne feil logger `ERROR`. Resultatet registreres som `EKSTERN_FEIL` i nøkkeltallene | Ja | Følges opp fra varselet og resultatet |
| Automatisk behandling av personhendelser | Direkte økonomisk | En hendelse kan bli stående ubehandlet | Behandlingsfeil gir `ERROR`. Etter gjentatte feil kreves manuell oppfølging | Ja | Hendelsen forsøkes igjen frem til grensen for automatiske forsøk |
| PDL-vurdering av bostedsadressehendelser | Indirekte økonomisk | Hendelsen kan bli stående uvurdert ved vedvarende PDL-feil | `IkkeTilgangTilPerson` og `Ukjent` logger `ERROR` | Ja | Hendelsen forblir uvurdert og forsøkes igjen |
| PDL-treffadresse ved oppgaveoppretting | Annen vesentlig konsekvens | Oppgaven kan mangle en supplerende treffadresse | Feilen logger `WARN`. Selve oppgaven opprettes og blir synlig for en saksbehandler | Ja | Saksbehandleren følger opp oppgaven |
| Oppgaver fra personhendelser | Indirekte økonomisk | En oppgave kan mangle | Oppgavefeil gir `ERROR` | Ja | Hendelsen beholdes for ny behandling |
| Institusjonsopphold | Indirekte økonomisk | Fullstendig fravær av hendelser hos produsenten | Behandlingsfeil gir `ERROR` | Ja | Hendelsen beholdes. Produsentens overvåking må bekreftes |
| Utfall fra Klageinstans | Indirekte økonomisk og vesentlig for rettssikkerheten | Fullstendig fravær av hendelser hos produsenten | Mapping- og behandlingsfeil gir `ERROR` | Ja | Feilen undersøkes fra Slack-varselet |
| Oversendelse av klage til Kabal | Annen vesentlig konsekvens | Ikke noe kjent ved HTTP-feil | Oversendelsen er synkron. HTTP-, token- og nettverksfeil gir `ERROR`, transaksjonen rulles tilbake og saksbehandleren får feil | Nei | Saksbehandleren kan forsøke oversendelsen på nytt |
| Svar på oversendt klage | Annen vesentlig konsekvens og mulig indirekte økonomisk konsekvens | Et forventet svar som aldri produseres | Kabal sender svar som Kafka-hendelser. Konsum-, mapping- og behandlingsfeil gir `ERROR`. En jobb kontrollerer hver time i åpningstiden og logger én samlet `ERROR` når klager har ventet mer enn seks måneder uten en prosessert Klageinstans-hendelse | Ja | Feilende lagrede hendelser markeres for manuell oppfølging. Seks måneder er en operativ kontrollgrense, ikke en bekreftet lovfrist |
| Automatisk stans ved manglende oppmøte | Direkte økonomisk | En sak kan bli stående uten stans | Feil per sak gir `ERROR` | Ja | Saken vurderes ved senere kjøring |
| Journalføring i Joark | Annen vesentlig konsekvens | Et dokument kan mangle i arkivet | Journalføringsfeil gir `ERROR` | Ja | Hendelsen beholdes for ny behandling |
| Distribusjon av brev | Annen vesentlig konsekvens | Brukeren kan mangle et vedtak eller annet brev | Distribusjonsfeil gir `ERROR` | Ja | Gjentatt `ERROR` og retry er akseptert; feilen følges opp fra Slack |
| Påminnelse om ny stønadsperiode | Indirekte økonomisk | En påminnelse kan utebli | Jobb- og utsendelsesfeil gir `ERROR` | Ja | Jobbstatus viser om jobben har kjørt |
| Innkalling til kontrollsamtale | Indirekte økonomisk | En innkalling kan utebli | Jobb- og utsendelsesfeil gir `ERROR` | Ja | Jobbstatus og Slack brukes til oppfølging |
| Stønadstatistikk til BigQuery | Kun rapportering | Manglende eller feil rapporteringsdata | BigQuery-jobbstatus og antall skrevne rader kontrolleres før data markeres som sendt | Nei | Usendte rader forsøkes igjen |
| Sakstatistikk til BigQuery | Kun rapportering | En feilet dato sendes ikke automatisk på nytt neste døgn | BigQuery-feil og feil radantall gir `ERROR` | Nei | Manuell oppfølging fra Slack-varselet er akseptert |
| Søknadsstatistikk | Kun rapportering | Manglende rapporteringsdata | Jobbstatus og `ERROR` | Avhenger av senderen | Manuell oppfølging |
| Fritekstavslagsstatistikk | Kun rapportering | Manglende rapporteringsdata | Jobbstatus og `ERROR` | Avhenger av senderen | Manuell oppfølging |
| Statistikkvisning | Kun rapportering | Manglende aggregater | Jobbstatus og `ERROR` | Avhenger av bestillingen | Manuell oppfølging |
| Database, backup og replikering | Plattformkritisk | Feilet backup eller replikering | Cloud SQL er konfigurert med høy tilgjengelighet, automatisk backup og point-in-time recovery | Ikke relevant | Plattformansvar, se punktet som skal avklares |

## Sakstatistikk

Den ordinære jobben sender gårsdagens data. En kjøring 24. september sender data
for 23. september. Hvis sendingen av 22. september feilet dagen før, blir ikke
22. september automatisk med i neste sending.

Dette er akseptert fordi overføringsfeilen gir `ERROR` i Slack og kan følges opp
manuelt. Flyten påvirker bare rapportering.

## Gjentatt varsel om kjøreplanen

Konsistensavstemmingsjobben kjører hver fjerde time innenfor Oppdrags ordinære
åpningstid, mandag til fredag kl. 06–21. Når det er mindre enn to måneder igjen
av kjøreplanen, logger hver kjøring samme `ERROR` frem til nye datoer er lagt inn.
Det sendes ikke varsler om kjøreplanen om natta eller i helger.

Dette er et bevisst gjentatt varsel. Det skal stoppe når teamet legger inn nye
datoer. Varselet påvirker ikke lenger statusen for dagens avstemming.

## Kontroll av svar fra Klageinstansen

Kontrolljobben forsøker å kjøre hver time i Oppdrags åpningstid på virkedager. Den
finner oversendte klager som ikke har en prosessert Klageinstans-hendelse. Hvis oversendelsen er eldre enn seks
kalendermåneder, logger jobben én samlet `ERROR` med antall klager. Varselet lister
også intern klage-ID og sak-ID for hver berørte klage, slik at teamet kan undersøke
sakene. Fødselsnummer, saksnummer og andre personopplysninger logges ikke.

Seks måneder er en operativ kontrollgrense for oppfølging. Det er ikke dokumentert
som en lovfrist for Klageinstansen. Når jobben finner gamle klager, gjentas
varselet hver time i åpningstiden til systemet har registrert en
Klageinstans-hendelse. Hvis teamet undersøker klagen og godtar videre
ventetid, må avviket dokumenteres. Systemet har ikke en egen status for en slik
godkjenning.

## Rettet i denne gjennomgangen

| Tidligere mangel | Retting |
|---|---|
| En utbetaling kunne bli stående uten kvittering uten noe nytt feilsignal | En ny jobb varsler med `ERROR` etter to timer innenfor Oppdrags åpningstid og kontrollerer hver time mens Oppdrag er åpent |
| Kjøreplanen for konsistensavstemming kunne gå tom uten varsel | Jobben varsler med `ERROR` når siste dato er mindre enn to måneder frem i tid |
| Kjøreplanvarselet gjorde selve avstemmingsjobben `FULLFØRT_MED_FEIL` | Varselet er skilt fra jobbresultatet |
| Tom, tilsiktet kjøreplan i dev og lokalt ga `ERROR` | Tom kjøreplan utenfor produksjon varsles ikke |
| Eksterne fradragsoppslag logget bare `WARN` mens batchen fortsatte | Feil fra Pesys og AAP logger `ERROR` |
| `AlleredeKjørtForMåned` ga `FULLFØRT_MED_FEIL` | Tilstanden gir nå `JobbResultat.Ok` |
| Stønadstatistikk kontrollerte ikke resultatet fra BigQuery | Jobbfeil og antall skrevne rader kontrolleres før data markeres som sendt |
| PDL-feil kunne la en bostedsadressehendelse stå uvurdert med bare `WARN` | `IkkeTilgangTilPerson` og `Ukjent` logger nå `ERROR`, mens hendelsen beholdes for retry |
| Den nye kontrolljobben manglet navn og beskrivelse i jobbstatus | Jobben er registrert i `JobbNavn` |
| En klage kunne bli stående uten svar fra Klageinstansen uten et nytt feilsignal | En jobb kontrollerer hver time i åpningstiden og logger én samlet `ERROR` når oversendte klager har ventet mer enn seks måneder uten en prosessert Klageinstans-hendelse. Varselet oppgir antall, klage-ID og sak-ID |

## Oppfølgingsrutine

Alle utviklere i team Supplerende stønad følger `#su_alerts_prod`. Teamet håndterer
varslene slik:

1. Den som tar et varsel, undersøker loggene og avklarer hvilken dataflyt som
   feilet.
2. Feilen klassifiseres som direkte økonomisk, indirekte økonomisk, annen
   vesentlig konsekvens eller kun rapportering.
3. Teamet kontrollerer om prosessen stoppet, om den fortsetter, og om data blir
   forsøkt på nytt.
4. Feil som ikke kan løses som del av den løpende oppfølgingen, registreres i JIRA
   med ansvarlig, frist og eventuell økonomisk eller annen vesentlig konsekvens.
5. Teknologileder og produktleder informeres når en kritisk dataflyt mangler
   monitorering, eller når en feil ikke er løst innen fristen.
6. Utestående saker overleveres til resten av teamet. Teamet trenger ikke en egen
   sheriff-rolle så lenge alle utviklerne følger kanalen og ansvaret for hvert
   varsel blir tydelig plassert.
7. Ved den tertialvise gjennomgangen kontrollerer teknologileder at alle varsler og
   avvik i perioden er fulgt opp.

Rutinen angir foreløpig ikke en fast responstid eller løsningsfrist. Dette må
avklares før den tertialvise kontrollen kan konkludere med om feil er løst innen
fastsatt tidsfrist. Avklaringen bør skille mellom tid for første vurdering og tid
for endelig løsning, og mellom direkte økonomiske feil, andre vesentlige feil og
feil som bare påvirker rapportering.

For hvert avvik dokumenterer vi:

- hva som gikk galt
- hvilken frist som ble brutt
- hva teamet gjorde
- om feilen påvirket økonomi, rettssikkerhet eller rapportering
- om tilsvarende feil kan skje igjen uten at noen oppdager det

## Aksepterte forhold

- Vi trenger ikke en egen kødybde eller samlet visning av utestående hendelser når
  hver feil logger `ERROR`, data beholdes og resten av behandlingen fortsetter.
- En permanent feilende dokumenthendelse kan gi gjentatte varsler og retry hvert
  minutt. Dette er akseptert fordi feilen blir synlig i Slack og ikke blir
  behandlet som vellykket.
- Sakstatistikk henter ikke automatisk inn en dato som feilet. `ERROR` og manuell
  oppfølging er tilstrekkelig fordi flyten bare gjelder rapportering.
- Saksbehandlernes arbeidsfrister og kapasitet inngår ikke i denne tekniske
  kontrollen.
- Regulering kjøres og kontrolleres manuelt én gang i året.

## Avklares senere

Cloud SQL er konfigurert med PostgreSQL 17, høy tilgjengelighet, automatisk backup,
21 beholdte backuper og point-in-time recovery. Teamet må avklare med Nais eller
plattformmiljøet:

- hvem som overvåker feil i backup og replikering
- hvordan teamet varsles
- om gjenoppretting er testet
- hvilke mål som gjelder for gjenopprettingstid og mulig datatap

Dette punktet påvirker ikke ferdigstillingen av kontrollen for applikasjonens
jobber og dataflyter, men skal følges opp separat.
