# Historisk import og revurdering

## Avgrensning

Alle måneder som finnes i den aktive Infotrygd-projeksjonen skal kunne revurderes. Den historiske beregningen og
det historiske vedtaket brukes som opprinnelig resultat.

Sammenligningen er:

1. gammelt resultat hentes fra det historiske vedtaket/beregningen,
2. den valgte perioden beregnes med reglene og satsene som gjelder for perioden,
3. differansen mellom gammelt og nytt resultat brukes videre i revurderingen,
4. måneder utenfor den valgte perioden beholdes uendret.

At et gammelt beløp finnes er dermed nok til å bevare perioden. Det er ikke nok til å avgjøre en endring i en måned
som revurderes. Måneden må beregnes på nytt for å finne korrekt differanse.

## Faglig status for historisk revurdering

Statusen beskriver løsningen per 25. september 2026. «Implementert» betyr at flyten finnes i kode. «Planlagt»
beskriver avtalt oppførsel som ikke er ferdig koblet sammen. Spørsmålene nederst er ikke beslutninger.

### Slik skal løsningen fungere for fag

- Alle revurderinger av Infotrygd-vedtak skal behandles i en egen kanal. De skal ikke behandles som ordinære
  revurderinger i SU-appen.
- Saksbehandler velger en sammenhengende periode med hele kalendermåneder. Saksbehandler kan velge en del av en
  vedtaksperiode, men hver valgt måned må være dekket av et historisk vedtak.
- En behandling kan omfatte flere historiske vedtak. Vedtakene kan ha overlappende eller tilgrensende perioder.
- Alle måneder som er representert av historiske vedtak kan revurderes, også opphør og andre måneder uten ytelse.
- En person kan ha flere åpne historiske revurderinger samtidig, men de åpne behandlingene kan ikke gjelde samme
  måned.
- Saksbehandler velger satsvariant `EN`, `EU`, `EO` eller `EV` for hver periode. Saksbehandler skal ikke skrive inn
  satsbeløpet. `EV` kan bare velges fra januar 2016. Systemet skal avvise `EV` for eldre måneder.
- Systemet foreslår satsvarianten som er registrert i de historiske dataene. Saksbehandler kan velge en annen
  variant. Systemet finner satsbeløpet ut fra måned og valgt variant. For 2006 til 2010 brukes G-faktor. Fra mai
  2011 brukes årsbeløpet i den historiske satsserien.
- Saksbehandler registrerer fradrag med de samme typene som brukes ved ordinær revurdering. Saksbehandler skal
  registrere fradragsgrunnlaget, ikke sluttbeløpet. Ordinære regler for fradrag som tilhører ektefellen gjelder
  også i denne kanalen.
- Historiske perioder med mulig forsørgingstillegg skal ikke beregnes automatisk. Opprinnelig lov § 5 ga
  40 prosent av grunnbeløpet per barn under 18 år som mottakeren forsørget og bodde sammen med. Etter
  pensjonsomleggingen viser `T_BEREGN_FAKTOR.TILL_BARN_PROS` 20 prosent; Prop. 14 L (2014–2015) beskriver dette
  som 20 prosent av minste pensjonsnivå med høy sats per barn. Prosentskiftet er dermed også et skifte i
  beregningsgrunnlag og skal ikke tolkes som en halvering av tillegget.
- Prop. 14 L (2014–2015) kapittel 7 avviklet forsørgingstilleggene for stønadsperioder eller nye stønadsperioder
  som startet tidligst 1. januar 2015. Supplerende stønad ble gitt for 12 måneder om gangen, så en tidligere
  stønadsperiode kunne beholde tillegget inn i 2015. `T_BEREGN_FAKTOR` viser først null fra satsraden 1. mai 2015;
  denne datoen kan ikke brukes alene som rettslig skjæringstidspunkt.
- Når valgt periode berører et vedtak som tilhører en stønadsperiode startet før 1. januar 2015, skal frontend
  vise det historiske utbetalte månedsbeløpet og et tydelig varsel med lovgrunnlaget. Saksbehandler må bekrefte
  at beløpet er kontrollert for mulig forsørgingstillegg før beregning og attestering. Systemet skal ikke forsøke
  å rekonstruere tillegget automatisk, fordi alle 69 733 importerte `T_ROLLE`-rader mangler verdier i
  `BARN_TYPE`, `BT_1_*`, `BT_2_*` og `BT_S_*`.
- Flere delperioder kan ha ulike satsvarianter og fradrag. Beregningen har månedsoppløsning.
- Systemet sammenligner resultatet som gjelder før revurderingen med den nye beregningen. «Før» bygges fra den
  låste Infotrygd-projeksjonen og eventuelle tidligere iverksatte historiske revurderinger. Det lagres ikke en
  ekstra kopi av det gamle resultatet.
- Behandlingen lagrer hvilket vedtak som gjaldt for hver måned da behandlingen ble opprettet. Før iverksettelse
  bygges vedtaksdataene på nytt. Behandlingen stoppes hvis et annet historisk vedtak senere har overtatt en av
  månedene.
- Et positivt månedsbeløp under 2 prosent av full stønad til enslig gir opphør. Null eller negativt resultat gir
  også opphør. Et beløp som er nøyaktig lik grensen gir ytelse.
- Flyten skal støtte opphør for hele eller deler av perioden. En senere periode kan innvilges igjen.
- Opphør følger samme skille som i ordinær revurdering. Saksbehandler velger en opphørsgrunn når opphøret skyldes
  et manuelt vurdert vilkår, blant annet formue eller utenlandsopphold. `FOR_HØY_INNTEKT` og
  `SU_UNDER_MINSTEGRENSE` kan ikke velges manuelt, men utledes av beregningen. Søkerens og ektefellens inntekter
  inngår som fradrag etter de ordinære EPS-reglene. Når beregnet ytelse blir null eller negativ, utledes
  `FOR_HØY_INNTEKT`. Når positiv ytelse er lavere enn 2 prosent av full enslig sats, utledes
  `SU_UNDER_MINSTEGRENSE`.
- Innvilgelse etter et opphør krever en egen begrunnelse.
- Perioden skal stoppe ved siste Infotrygd-vedtak, senest mai 2026, og før første måned med innvilget ytelse i
  SU-appen. Hvis overgangen inneholder feil på både Infotrygd-siden og SU-app-siden, behandles sidene separat.
- Behandlingen skal ha forhåndsvarsel, beregning, simulering, attestering, vedtak og brev. Som i ordinær
  revurdering velger saksbehandler om det skal sendes forhåndsvarsel. Historiske revurderinger gjenbruker
  ordinære revurderingsvedtaksbrev, brevvalg, fritekstlagring og forhåndsvarselmønster. Endringer i periode,
  sats, fradrag eller resultat etter at varselet er sendt, krever et nytt varsel eller et eksplisitt valg om at
  nytt varsel ikke er nødvendig.
- En behandling som berører flere historiske vedtak skal gi ett samlet vedtak.
- En revurdering uten beløpsendring kan ferdigstilles og gi vedtak.
- En behandling skal ikke blande etterbetaling og feilutbetaling. Hvis beregningen gir begge deler, deler
  saksbehandler perioden i separate, ikke-overlappende behandlinger med én økonomisk retning i hver.
- Hvis en senere historisk revurdering endrer resultatet fra en tidligere historisk revurdering, skal den ta
  hensyn til kravgrunnlag og eventuell tilbakekrevingsbehandling på samme måte som ordinære revurderinger i
  SU-appen.
- En senere revurdering av måneder som har fått kravgrunnlag etter en historisk Infotrygd-revurdering, skal fortsatt
  behandles i den historiske Infotrygd-kanalen. Den ordinære revurderingskanalen skal ikke overta disse månedene.
- Selve tilbakekrevingsbehandlingen kan bruke den felles tilbakekrevingsmodulen. Kravgrunnlagets `utbetalingId`
  skal peke på den historiske revurderingens vedtak og gir dermed koblingen til Infotrygd-kanalen. Periodene i
  kravgrunnlaget brukes til å kontrollere hvilke måneder tilbakekrevingen gjelder.
- Hvis en historisk stønad med positiv ytelse mangler Oppdrag-ID, skal systemet sperre simulering og iverksettelse
  og forklare saksbehandleren at utbetalingslinjene ikke kan identifiseres.
- Ordinære krav til attestering og habilitet gjelder også for historiske revurderinger.
- En avsluttet behandling sperrer ikke perioden. En senere endring behandles som en ny revurdering.
- Historiske revurderinger gjenbruker dagens vedtaksbrev og brevvalg for ordinær revurdering. Brevet fyller inn
  strukturerte behandlingsdata og kombineres med saksbehandlers fritekst. Det viser perioder, gammelt og nytt
  beløp, økonomisk retning, sats, fradrag, opphørsgrunner og eventuell gjeninnvilgelse.
- Den historiske behandlingen skal ikke konstruere en kunstig `VilkårsvurderingerRevurdering` eller ordinær
  `Beregning`. `VilkårsvurderingerRevurdering.Alder` forutsetter at alle ordinære vilkår finnes, mens den
  historiske kanalen bare har vilkårene som faktisk er vurdert og beregningen som faktisk er utført.
  Vedtaksbrev gjenbrukes gjennom en felles brevgrunnlags-wrapper med to adaptere:
  ordinær revurdering mapper dagens vilkår og beregning til brevgrunnlaget, og historisk revurdering mapper sine
  periodiserte vurderinger og månedsresultater til det samme brevgrunnlaget. Wrapperen inneholder ferdige
  beregningsperioder, satsoversikt, om mottakeren har ektefelle, opphørsgrunner, opphørsperiode, behandlere og
  fritekst. Den skal ikke inneholde eller kreve hele behandlingsmodellen.
- Import og konvertering skal kjøres én gang i produksjon. Når projeksjonen tas i bruk av
  revurderingsbehandlingene, er den aktiv og låst.

### Hvor langt vi har kommet

Ferdig eller koblet inn:

- tapsfri import av Infotrygd-data
- normalisert projeksjon av historiske stønader, vedtak og månedsbeløp
- personoppslag og månedlig originaltidslinje fra én eksplisitt, fullført projeksjon
- regler for hvilket historisk vedtak som gjelder når perioder overlapper
- historisk satsserie for `EN`, `EU`, `EO` og `EV` fra 2006 til 2026
- separat behandlingsdomene med opprettelse, beregnet status, attestering, underkjenning og avslutning
- egen databasetabell for historiske revurderinger
- månedsvise referanser til vedtakene behandlingen bygger på
- kontroll som hindrer overlappende åpne historiske revurderinger for samme sak
- optimistisk versjonskontroll ved oppdatering
- egne routes og service for opprettelse, henting, attestering, underkjenning og avslutning
- rolle- og persontilgang samt CEF-audit på de historiske route-flatene
- historisk revurderingsvedtak med egen tabell, månedsresultater og unik kobling til `utbetalingId`
- direkte oppslag fra `utbetalingId` til historisk vedtak og revurdering uten en egen kildemarkør på kravgrunnlaget
- eksplisitt sperre mot iverksettelse mens kontrakten med Oppdragssystemet er uavklart

Pågår:

- regelspesifisert månedsberegning fra satsvariant og typed fradrag
- historisk G-beregning for 2006 til 2010
- minstegrensen på 2 prosent
- lagring av satsvalg, fradrag, beregningsresultat og komplett regeltre

Gjenstår:

- route og request-/response-modeller for å registrere beregningsgrunnlag og starte beregning
- visning av gammelt og nytt resultat og differansen per måned
- forhåndsvarsel med saksbehandlers valg og krav om nytt varsel etter endringer
- varsel og eksplisitt bekreftelse ved mulig historisk forsørgingstillegg
- direkte valgt opphør på grunn av formue, utenlandsopphold eller annet faglig grunnlag
- simulering mot Oppdragssystemet
- opprettelse av historisk revurderingsvedtak i den faktiske iverksettelsesflyten
- kontroll mot endret vedtaksgrunnlag i den faktiske iverksettelsesflyten
- attestering som oppretter og iverksetter vedtak
- vedtaksbrev og egen begrunnelse ved innvilgelse etter opphør
- sperre i ordinær revurdering som avviser måneder som tilhører Infotrygd-kanalen
- route-tester og komplette tester av beregningsregeltreet
- produksjonsrutine som markerer den ene godkjente projeksjonen som aktiv og låst

### Faglige avklaringer

Disse spørsmålene må fortsatt avklares:

1. **Manglende FM-rad:** Bekreft om fravær av en `FM`-rad betyr at vedtaket hadde null kroner i fradrag. Hvis det
   er Infotrygds lagringsregel, kan projeksjonen trygt bruke null. Hvis en `FM`-rad kan mangle på grunn av
   ufullstendige data, må behandlingen varsle om usikkert gammelt beløp.

### Frontendvarsel om historisk forsørgingstillegg

Backend skal avgjøre kontrollbehovet fra stønadsperiodens startdato og returnere det sammen med vedtaksperioden:

```json
{
  "stonadsperiodeFraOgMed": "2014-08-01",
  "kreverKontrollAvHistoriskForsorgingstillegg": true,
  "historiskUtbetaltManedsbelop": 4698
}
```

Frontend skal ikke utlede kontrollbehovet fra vedtakets dato eller fra satsraden 1. mai 2015. Når flagget er
`true`, skal følgende tekst vises før saksbehandler kan fortsette:

> **Kontroller mulig forsørgingstillegg**
>
> Denne stønadsperioden startet før 1. januar 2015. Etter reglene som gjaldt da, kunne supplerende stønad
> inneholde forsørgingstillegg for barn under 18 år. Historiske data viser ikke om det utbetalte beløpet
> inneholdt et slikt tillegg.
>
> Kontroller det viste historiske månedsbeløpet før du fortsetter. Du har ansvar for at beløpet som brukes som
> tidligere utbetalt ytelse, er korrekt.

Frontend skal vise denne obligatoriske bekreftelsen:

> Jeg har kontrollert det historiske månedsbeløpet og vurdert om det inneholder forsørgingstillegg.

Bekreftelsen sendes som `harBekreftetKontrollAvHistoriskForsorgingstillegg`. Backend skal avvise beregning og
attestering når kontroll kreves og bekreftelsen mangler. Bekreftelsen nullstilles dersom periode eller historisk
utgangspunkt endres.

Frontend skal vise lovgrunnlaget ved varselet:

- Opprinnelig § 5: Ytelsen ble økt med 40 prosent av grunnbeløpet per barn under 18 år som mottakeren forsørget
  og bodde sammen med.
- Prop. 14 L (2014–2015) kapittel 7: Før avviklingen var tillegget 20 prosent av minste pensjonsnivå med høy sats
  per barn. Forsørgingstilleggene ble avviklet for stønadsperioder eller nye stønadsperioder som startet tidligst
  1. januar 2015.
- Kilde: [Prop. 14 L (2014–2015), kapittel 7](https://www.regjeringen.no/no/dokumenter/prop.-14-l-20142015/id2343957/?ch=7).

### Tekniske spørsmål som krever faglig konsekvensvurdering

Disse spørsmålene gjelder integrasjonen, men svarene bestemmer hva saksbehandler og mottaker opplever:

1. Hvordan finner vi de gamle utbetalingslinjene i Oppdrag for hver historisk stønad og måned?
2. Kan `T_STONAD.OPPDRAG_ID` og `T_DELYTELSE.LINJE_ID` brukes i oppslaget, og hvordan finner vi linjene når
   Oppdrag-ID mangler?
3. Hvordan kobles de gamle linjene til et oppdrag som bruker SU-appens saksnummer som fagsystem-ID og nye ID-er
   for nye linjer?
4. Hvordan skal opphør og senere gjeninnvilgelse representeres i Oppdrag når flere historiske vedtak inngår i én
   behandling?
5. Kan Oppdrag simulere korrigeringer så langt tilbake i tid, og hvilke perioder eller statuser avvises? Oppdrag
   må varsles før historiske korrigeringer tas i bruk.
6. Hvilken informasjon fra Utbetalingsreskontro trengs for å beregne korrekt etterbetaling eller tilbakebetaling?

## Råimport

- `SupstonadHistoriskClient` kan hente tabelloversikt og henter antall rader og paginerte uttrekk.
- Importtjenesten krever det avtalte tabellsettet og et stabilt skjema. Driftsruten bruker foreløpig det hardkodede
  skjemaet i `TABELLER_MED_KOLONNER` fordi tabelloversikt-endepunktet i kilden ikke er klart. Skjemaet i hver
  uttrekksside kontrolleres fortsatt mot dette skjemaet.
- Alle verdier lagres som rå JSONB, og database-`NULL` bevares forskjellig fra tom tekst.
- Rader og iterator-checkpoint lagres i samme transaksjon per side.
- Etter at importen er opprettet, markerer klientfeil, skjemaavvik, radbreddeavvik, stillestående iterator eller
  avvik mot forhåndstalt antall hele importen som `FEILET`. Det er ingen gjenopptakelse; start en ny import.
- Tjenesten avviser en ny import dersom en import allerede pågår. Driftsruten starter importen asynkront og svarer
  alltid HTTP 202 på en gyldig startforespørsel; en intern avvisning blir derfor foreløpig bare logget og
  returneres ikke som HTTP 409.
- Sletteforsøk på en pågående import avvises med HTTP 409; en ikke-eksisterende import gir HTTP 404.
- Uttrekksdata logges ikke.

## Forutsetning for de historiske vedtakene

Alle vedtakene fra Infotrygd i dette uttrekket gjelder supplerende stønad for alder. Projeksjonen skal derfor ikke
forsøke å utlede stønadstype fra de historiske radene — alle saker er alder. Klassifiseringsnivå 02 med kodene
`EN`, `EO`, `EU` og `EV` beskriver bosituasjon innenfor en alderssak.

## Steg 3: historisk aldersmodell

Det er lagt til en separat historisk aldersmodell og en prosjektør som knytter råtabellene sammen via `STONAD_ID`,
`VEDTAK_ID` og personløpenummer. Modellen dekker:

- sammenhengende stønad og opphør,
- vedtaksperiode, behandlingstype, resultat og saksreferanse,
- stønadsklassifisering og relasjon til ektefelle/partner/samboer,
- valgt beregningsgrunnlag, årsinntekter og delytelseslinjer,
- endringskoder og beslutning/godkjenning.

Dette er den transiente konverteringsmodellen. Oppslagsprojeksjonen persisterer bare feltene som trengs for
personkobling, vedtaksoversikt og månedsbeløpsperioder. Tolket bosituasjon fra klassifiseringsnivå 02 og årlig
ytelsesbeløp fra SU-detaljene persisteres også. Øvrige klassifiseringer, roller, inntekter, SU-detaljer,
fullstendige beslutningsdata og rå delytelseslinjer er fortsatt tilgjengelige i det tapsfrie JSONB-snapshotet, men
persisteres ikke i de normaliserte projeksjonstabellene.

Kjente behandlingstyper (`S`, `R`, `MG`, `MO`, `GO`, `MS`, `MB`, `FL`, `K`), resultater
(`I`, `DI`, `FI`, `IN`, `Ø`, `R`, `O`, `U`, `A`, `AN`),
stønadsklasser (`EN`, `EO`, `EU`, `EV`) og dokumenterte opphørskoder tolkes. Råkoden beholdes alltid. En ukjent kode
gir et projeksjonsavvik, men fører ikke til tap av rådata. Avvik og forbehold returneres internt fra
konverteringstjenesten. Antall avvik per avvikstype og forbehold persisteres sammen med projeksjonen.

Projeksjonen skal tilby et eget historisk utgangspunkt til opprettelse av revurdering. Den skal ikke konstruere et
kunstig moderne `VedtakSomKanRevurderes`, fordi dagens UUID-er, vilkår og grunnlag ikke finnes én-til-én i Infotrygd.
Den separate historiske revurderingstjenesten bruker oppslagene til å bygge originaltidslinjen for personen og
perioden som skal revurderes.

Modellen, rådatakonverteringen, persisteringen og oppslagsflatene er implementert. Konverteringen oppretter en
importversjonert projeksjon, lagrer normaliserte stønader, vedtak og månedsbeløp batchvis og markerer deretter
projeksjonen `FULLFØRT`. En projeksjon med status `PÅGÅR` eller `FEILET` er ikke
synlig for personoppslag. Driftsruten oppretter projeksjonen før den starter konverteringen asynkront på
`Dispatchers.IO`. HTTP 202-responsen inneholder projeksjons-ID-en:

```json
{"projeksjonId":"00000000-0000-0000-0000-000000000000"}
```

En ordinær konvertering startes med:

```text
POST /drift/supstonadhistorisk/import/{importId}/konverter
```

En avgrenset dry-run startes med:

```text
POST /drift/supstonadhistorisk/import/{importId}/konverter?maksAntallStonader=100
```

Grensen gjelder antall rader fra `T_STONAD`. Hver rad konverteres sammen med alle tilhørende vedtak og øvrige
vedtaksdata. Lesingen stopper når grensen er nådd; resten av importen leses ikke. Hver kjøring får egen
projeksjons-ID, og `historisk_alder_projeksjon` lagrer `dry_run`, `maks_antall_stonader`, faktisk
`antall_stonader`, status og tidspunkter. Deldataene fra dry-run beholdes i de samme normaliserte tabellene med
egen projeksjons-ID. Ordinære personoppslag ignorerer dry-runs og velger bare siste fullførte projeksjon med
`dry_run = false`.

Konverteringer av mer enn 500 stønader bruker fire parallelle workers. Én sekvensiell produsent leser
rådatasider med maksimalt 500 stønader og sender dem til en begrenset kanal. Første ledige worker tar neste side,
konverterer den og lagrer i batcher på 50. Dry-runs med høyst 500 stønader kjøres sekvensielt. Hver worker samler
egne avvik; disse slås sammen før projeksjonen fullføres.

Migreringene V295–V297 bygger indekser på `historisk_import_rad` med vanlig `CREATE INDEX` og kjører `ANALYZE`.
De er allerede kjørt i utviklingsmiljøet og skal ikke endres. Før første produksjonsdeploy må ingen historisk
import skrive til tabellen, fordi indeksbyggingen blokkerer slike skriv til migreringene er ferdige.

Frontend og andre driftsklienter kan hente alle kjøringer for importen, inkludert dry-runs, med:

```text
GET /drift/supstonadhistorisk/import/{importId}/konverteringer
```

Listen er sortert nyeste først. Hver kjøring viser projeksjons-ID, `PÅGÅR`, `FULLFØRT` eller `FEILET`, dry-run-flagg,
grense, antall stønader som hittil er lagret, antall avvik gruppert per avvikstype, forbehold, tidspunkter og
eventuell feilbeskrivelse. Antallet oppdateres i samme transaksjon som hver lagrede batch og er derfor også
tilgjengelig hvis kjøringen feiler. Fullført resultat og avviksoppsummering logges med projeksjons-ID; uventede feil
logges med stacktrace og lagres som feilbeskrivelse på kjøringen. Frontend finner kjøringen ved å matche
projeksjons-ID-en fra HTTP 202-responsen mot `id` i listen.

```text
Frontend                         Backend
   |                                |
   | POST .../konverter[?maksAntallStonader=100] |
   |------------------------------->|
   | 202 { projeksjonId }           |
   |<-------------------------------|
   |                                |
   | GET .../konverteringer         |
   |------------------------------->|
   | liste: finn id, status=PÅGÅR   |
   |<-------------------------------|
   |          poller                |
   | GET .../konverteringer         |
   |------------------------------->|
   | finn id: FULLFØRT / FEILET     |
   |<-------------------------------|
```

En fullført ordinær projeksjon blir automatisk gjeldende for personoppslag dersom den er den nyeste fullførte
ordinære projeksjonen. Dette bestemmes ved oppslagstidspunktet av
`siste_fullførte_historiske_alder_projeksjon()`. Det finnes derfor ingen egen aktiveringsoperasjon. En dry-run har
`dry_run = true` og filtreres alltid bort, også etter at den er fullført.

`hentVedtaksperioder(personident)` henter alle vedtaksperioder for personen direkte fra
`historisk_alder_vedtak`. Vedtakene er allerede lagret sammen med hver konverterte batch. `fullførProjeksjon`
markerer derfor bare projeksjonen som fullført og bygger ingen separat ytelsestidslinje. En slik tidslinje eller
andre avledede ytelsesperioder skal først innføres når et konkret oppslagsbehov krever det.

### Rolle per importert tabell

Alle de 16 avtalte tabellene lagres tapsfritt. Konvertereren bruker 13 av dem:

Kolonner og datatyper i kildeskjemaet er dokumentert separat i
[`infotrygd-suq-datamodell.sql`](infotrygd-suq-datamodell.sql). Filen er referansedokumentasjon, ikke en
database-migrering.

```text
INFOTRYGD_SUQ (Oracle/COBOL)
  |
  | kildeuttrekk av tabellene i infotrygd-suq-datamodell.sql
  v
historisk_import_rad.data (tapsfri JSONB)
  |
  | HistoriskAlderDataConverter
  v
HistoriskAldersstønad / HistoriskAldersvedtak (transient, typet modell)
  |
  | HistoriskAlderProjeksjonPostgresRepo
  v
historisk_alder_* (normalisert oppslagsprojeksjon)
  |
  v
SupstonadHistoriskService (personoppslag og vedtaksperioder)
```

| Tabell | Bruk i konverteringen |
|--------|------------------------|
| `T_STONAD` | Rot for batching; stønad, personløpenummer, startdato og opphør |
| `T_VEDTAK` | Vedtak, periode, behandlingstype (`TYPE_SAK`), resultat og saksreferanse |
| `T_LOPENR_FNR` | Kobler personløpenummer til personident for stønad, rolle og delytelsesmottaker |
| `T_BELOPSTYPE` | Kodeverk for inntektsrader |
| `T_DELYTELSESTYPE` | Kodeverk og fortegn for delytelser |
| `T_KLASSENIVAA` | Tekst for klassifiseringsnivå |
| `T_STONADSKLASSE` | Historisk stønadsklasse/bosituasjon |
| `T_ROLLE` | Rolle og relatert person |
| `T_SU` | Årlig ytelsesbeløp i `BELOP_BER_GRUNNLAG` og revurderingsdato |
| `T_BEREGN_GRL` | Inntekts-/beregningsgrunnlagsrader per vedtak |
| `T_DELYTELSE` | Rå delytelser og utledning av månedsbeløp |
| `T_ENDRING` | Endringskoder som bevares som historikkinformasjon |
| `T_BESLUT` | Beslutning, godkjenning og utveksling med Oppdragssystemet |
| `T_BEREGN_FAKTOR` | Historiske beregningsfaktorer og satser; råimporteres, men brukes ikke av konvertereren |
| `T_KJOREPLAN_AVST` | Råimporteres, men brukes ikke av konvertereren |
| `T_MAP_DELYTELSE` | Råimporteres som kodeverk, men brukes ikke av konvertereren |

### Begrepsliste

| Begrep | Betydning | Hva det brukes til | Hvor det forekommer |
|--------|-----------|---------------------|----------------------|
| Råimport / snapshot | Tapsfri kopi av kildeuttrekket for én import | Sporbarhet og grunnlag for ny projeksjon uten nytt uttrekk | `historisk_import`, `historisk_import_tabell`, `historisk_import_rad.data` |
| Projeksjon | Versjonert, normalisert lesemodell av én fullført råimport | Personoppslag, vedtaksoversikt og månedsbeløpsperioder | `historisk_alder_projeksjon` og de normaliserte stønad-, vedtak- og månedsbeløpstabellene |
| Stønad | En historisk SU-alderssak knyttet til personløpenummer | Grupperer vedtak og avgrenser dem med start/opphør | `T_STONAD`, normalisert i `historisk_alder_stonad` |
| Vedtak | Historisk avgjørelse innenfor en stønad | Kilde til resultat, virkningsperiode og rekkefølge | `T_VEDTAK`, normalisert i `historisk_alder_vedtak` |
| Resultat | Utfallet registrert på vedtaket, for eksempel `FI` | Avgjør om vedtaket kan danne en ny ytelsesperiode | `T_VEDTAK.KODE_RESULTAT` |
| Vedtaksstatus | Finnes ikke som eget felt i kilden | Må utledes fra resultat, endringskoder, periode og eventuelt beslutnings-/opphørsdata | Utledet; ikke en kildekolonne |
| Baklengs vedtak / tom periode | Vedtak der FOM er etter TOM; flere har TOM dagen før FOM | Bevares som historikk, men kan ikke danne ytelsesmåneder | Utledes fra `T_VEDTAK.DATO_INNV_FOM > DATO_INNV_TOM` |
| Vedtaksperiode | Vedtakets registrerte virkningsperiode | Ytre periodegrense for vedtaket | `T_VEDTAK.DATO_INNV_FOM` og `DATO_INNV_TOM` |
| Delytelse | Beløpslinje som tilhører et vedtak | Grunnlag for å utlede sats, fradrag og vedtatt månedsbeløp | `T_DELYTELSE` |
| MS | Månedsats før fradrag | Sats i det utledede månedsbeløpet | `T_DELYTELSE.TYPE_DELYTELSE = 'MS'` |
| FM | Fradrag i månedsatsen | Trekkes fra MS. Det er ikke avklart hvordan vi kan skille null i fradrag fra en manglende FM-rad | `T_DELYTELSE.TYPE_DELYTELSE = 'FM'` |
| Inntektseier | Om en grunnlagsrad gjelder stønadsmottakeren eller ektefellen | Skiller hvilke inntekter som skal påvirke beregningen | Kodet i `T_BEREGN_GRL.TYPE_BELOP`: brukte koder ender på `M` for stønadsmottaker og `E` for ektefelle |
| Årlig ytelsesbeløp | Årsbeløpet som ble registrert for vedtaket | Historisk satsinformasjon; tilsvarer normalt den avrundede månedsatsen multiplisert med tolv | `T_SU.BELOP_BER_GRUNNLAG`; tilsvarer normalt `MS × 12` |
| Delytelsesperiode | Perioden en MS/FM-gruppe gjelder | Snevrer inn vedtaksperioden; null TOM betyr åpen periode | `T_DELYTELSE.FOM` og `T_DELYTELSE.TOM` |
| Månedsbeløp | Vedtatt beløp beregnet som MS minus FM | Beløp i ytelseskandidaten; er ikke nødvendigvis faktisk utbetalt | Utledet i konvertereren, lagret i `historisk_alder_manedsbelop` |
| Opphør | Avslutning registrert på stønaden | Avgrenser ytelseskandidater | `T_STONAD.KODE_OPPHOR`, `DATO_OPPHOR` og `TIDSPUNKT_OPPHORT` |
| Oppdragssystemet (OS) | Systemet Infotrygd sendte vedtaks- og oppdragsdata til for simulering og utbetaling | Beregnet utbetalings-/konteringslinjer og dannet utbetalingstransaksjoner | `T_STONAD.OPPDRAG_ID` og oversendingsfeltene i `T_BESLUT` |
| Utbetalingsreskontro (UR) | Utbetalingsdelen av den historiske betalingskjeden | Pengene ble utbetalt gjennom UR; SU-rutinen kunne hente «utbetalt t.o.m.» derfra | Ikke med i uttrekket; omtalt av servicerutinen `HENT-UTBET-TOM-FRA-UR` |
| Oversendt til OS | Metadata om utvekslingen mellom Infotrygd og Oppdragssystemet | Kan dokumentere at et vedtak ble sendt og at svar ble mottatt, men er ikke alene bevis på gjennomført utbetaling | `T_BESLUT.SENDT_TIL_OS`, `MOTTATT_FRA_OS` og `GODKJENT_AV_OS` |
| Endringskode | Historikkmarkør på et vedtak | Bevares uten at projeksjonen utleder en egen gyldighetsstatus | `T_ENDRING.KODE` |
| Projeksjonsavvik | Maskinelt funn om manglende nøkkel, ukjent kode, ugyldig periode eller beløp | Synliggjør datakvalitet uten å endre rådata | Returneres fra konverteringen; persisteres ikke |
| Forbehold | Kjent begrensning i datagrunnlaget eller modellen | Hindrer at uavklarte felter gis sikrere semantikk enn datagrunnlaget tillater | Returneres fra konverteringen; persisteres ikke |

## Bekreftede antagelser (verifisert august/september 2026)

Følgende er bekreftet mot kildekoden i historisk-exodus-supstonad og presys PR #2937:

1. **Kildedatabasen er SU-scopet.** Uttrekket kommer fra `infotrygd_suq` — en dedikert SU-subbase. All data er
   allerede scopet til SU alder, og konvertereren filtrerer derfor ikke på `KODE_RUTINE` eller annen ytelsestype.
2. **Datoformat er ISO.** Oracle DATE → `rs.getDate().toLocalDate().toString()` = `yyyy-MM-dd`. Oracle TIMESTAMP →
   `rs.getTimestamp().toLocalDateTime().toString()` = `yyyy-MM-ddTHH:mm:ss[.nnnnnnnnn]`. Vår `take(10)` +
   `LocalDate.parse` er korrekt. Caveat: Oracle DATE kan inneholde klokkeslett som forkastes — akseptert risiko.
3. **T_STONADSKLASSE har tre nivåer per vedtak.** Alle 199 587 vedtak har nøyaktig én rad på hvert nivå:
   - Nivå `01` har klasse `SU`.
   - Nivå `02` har klassene `EN` (103 203), `EO` (51 693), `EU` (14 604) og `EV` (30 087). Dette nivået
     representerer bosituasjon.
   - Nivå `03` har klassene `OB` (1 825) og `OR` (197 762). Den presise betydningen av disse kodene er ikke
     bekreftet.

   `T_KLASSENIVAA` beskriver bare nivåene som «Klassifisering 1 (STK1)», «Klassifisering 2 (STK2)» og
   «Klassifisering 3 (STK3)»; tabellen forklarer ikke klassekodene.
4. **KODE_RESULTAT observert i reelle SU-data.** `I`, `DI`, `FI`, `IN`, `Ø`, `R`, `O`, `U`, `A`, `AN`.
   `IN` betyr innvilget ny situasjon, `Ø` betyr økning og `R` betyr redusert. Den tidligere antagelsen om at
   `I`, `DI`, `FI`, `O`, `U`, `A` og `AN` var komplett, ble avkreftet av et reelt uttrekk i september 2026.
5. **KODE_OPPHOR er komplett.** `AN`, `AP`, `AÅ`, `FL`, `HI`, `IN`, `LU`, `SF`, `UT`, `DØ`, `UA` bekreftet.
6. **T_BEREGN_GRL joines på VEDTAK_ID.** Kildeskjemaet, ikke bare uttrekket, mangler både
   `T_BEREGN_GRL.BEREGN_GRL_ID` og `T_SU.VALGT_BEREGN_GRL`. Det finnes derfor ingen dokumentert kobling til én
   valgt grunnlagsrad. `BELOP_BER_GRUNNLAG` skal ikke tolkes som en referanse til en rad i `T_BEREGN_GRL`.
7. **T_BESLUT støtter enstegs-godkjenning.** `SAKSBEHANDLER2` er nullable. Null kan bety enten enstegs-godkjenning
   eller uferdig behandling — kontekst må avgjøre.
8. **T_MAP_DELYTELSE er et kodeverk.** Tabellen mapper `TYPE_DELYTELSE` og rutine til fagområde/fagområdereferanse,
   men brukes foreløpig ikke av konvertereren.
   Den inneholder ikke `VEDTAK_ID`, `LINJE_ID` eller `OPPDRAG_LINJE_ID`, og kan derfor ikke koble en historisk
   delytelseslinje direkte til OS/UR. Reelle SU-data viser også at flere delytelser kan ha samme `LINJE_ID` innenfor
   samme vedtak.
9. **T_ROLLE.TYPE for SU alder.** `EP` (ektefelle/partner) bekreftet i testdata. Andre rolletyper er ukjent
   for SU alder spesifikt.
10. **Beløpsfeltene svarer til SU UB.** Brukerhåndboken beskriver `Mnd. sats` som månedsbeløpet før fradrag,
    `Fradrag mnd. sats` som reduksjonen per måned og `Sum ytelse` som månedsbeløpet etter fradrag.
    `Valgt beregningsgrunnlag` er full stønad etter satsen som gjelder for tilfellet. For 199 575 av 199 587
    vedtak er `T_SU.BELOP_BER_GRUNNLAG` nøyaktig `MS × 12`. Feltet dokumenteres derfor forsiktig som det årlige
    ytelsesbeløpet Infotrygd registrerte for vedtaket, ikke som brukerens årsinntekt, selve grunnbeløpet G eller
    en kobling til `T_BEREGN_GRL`. Tolv vedtak har ulike satsversjoner i `T_SU` og `T_DELYTELSE`.
    Vedtatt månedsbeløp i uttrekket utledes som `MS - FM`. Om beløpet faktisk ble utbetalt må vurderes sammen
    med betalingsflyten mot Oppdragssystemet og UR.
11. **T_BELOPSTYPE skiller inntektseier i typekoden.** Alle beløpstyper som brukes av SU-uttrekket har kode og
    tekst som skiller stønadsmottaker (`ARBM`, `FTRM`, `KAPM`, `PENM`, `UTLM`) fra ektefelle (`ARBE`, `FTRE`,
    `KAPE`, `PENE`, `UTLE`). `M` betyr stønadsmottaker (`stm`) og `E` betyr ektefelle (`ekt`) i dette kodeverket.
    `BEHANDLING` har verdiene `VM` eller `BM` for disse radene, men betydningen brukes ikke til å fastslå eier og
    beholdes rått.
12. **Observerte behandlingstyper er dekket.** `MG` (maskinell omregning), `MO` (manuell omregning), `GO` (manuell
    G-regulering), `MS` (maskinell satsomregning), `MB` (maskinell beregning), `FL` (flyttesak) og `K` (klage)
    er dokumentert i SU-brukerhåndboken, eldre systemdokumentasjon eller Infotrygds kodehierarki og er observert
    i SU-data. `KO` (konvertering), `A` (anke) og `SØ` (søknad om økning/endring) er kjente Infotrygd-koder, men
    er ikke observert i det analyserte SU-uttrekket.
11. **Resultat `U` viderefører eksisterende stønad.** For revurdering med uendret resultat dannes det ikke ny
    vedtakslinje i SU VP eller ny linje til Oppdrag. En slik sak må derfor ikke tolkes som en ny beløpsperiode
    eller som opphør av den tidligere ytelsen.
12. **Stønads- og vedtaksdatoene er komplette, med 13 baklengse vedtaksperioder.**
    - Alle 199 587 vedtak har både FOM og TOM.
    - 13 vedtak har FOM etter TOM.
    - Alle 13 har resultat `FI` (fortsatt innvilget).
    - Behandlingstypene er `MB` (maskinell beregning) eller `R` (revurdering).
    - Vedtakene er registrert med perioder fra 2006 til 2024.
    - Flere er nøyaktig én måned baklengs, for eksempel `2006-10-01` til `2006-09-30`.
    - Andre har større avvik, for eksempel `2021-09-01` til `2021-05-31`.
    - Tre vedtak på samme stønad i 2024 har relaterte, men forskjellige baklengse perioder.

    Statusfeltene viser ikke én felles forklaring:
    - Sju har endringskode `AN` eller `UA` og er også eksplisitt annullert/uaktuelle.
    - To har endringskodene `E, O` og en opphørsdato som ligger ved den baklengse perioden. Disse støtter
      hypotesen om at den baklengse perioden representerer en opphørsmarkering.
    - Tre har bare endringskode `E`, og ett har bare `S`. Disse har ingen eksplisitt opphørsmarkør. Alle fire har
      TOM dagen før FOM og opptrer som tekniske tomme perioder i vedtakshistorikken:
      - De tre `E`-vedtakene følger umiddelbart etter et `MB`-vedtak med en ikke-baklengs periode på samme stønad.
        `MB`-vedtaket har samme FOM og en reell TOM, mens det baklengse vedtaket har TOM dagen før FOM.
      - `S`-vedtaket starter måneden etter at en foregående `MO`-periode slutter, men har TOM dagen før sin egen
        FOM.
    - Alle 13 har `OPPDRAG_ID`, mens ingen har en rad i `T_BESLUT`.

    De 13 periodene kan ikke brukes, og det finnes ikke en entydig dato å korrigere dem til. Baklengs periode kan
    være knyttet til opphør i enkelte tilfeller, men er ikke i seg selv en opphørsstatus.
    For de fire uten `AN`, `UA` eller `O` støtter nabovedtakene tolkningen «teknisk tom periode» bedre enn
    «opphør». `FI` alene betyr fortsatt innvilget og dokumenterer ikke opphør.
13. **Personkoblingen for stønader er komplett.** Alle 64 123 stønader har nøyaktig én tilhørende
    `T_LOPENR_FNR`-rad. Stønader uten personkobling lagres fortsatt i råimporten og den normaliserte
    stønadstabellen, men kan ikke finnes gjennom personbaserte oppslag.
14. **Manglende delytelses-TOM betyr åpen periode.** Av 362 302 delytelsesrader har ingen manglende FOM, mens
    170 164 mangler TOM. Projeksjonen tolker disse som åpne perioder og avgrenser dem til slutten av importmåneden.
    Seks baklengs delytelsesrader utgjør tre MS/FM-par på tre av de tretten baklengse vedtakene. Det finnes ingen
    annen periode i disse vedtakene å korrigere dem fra; parene forkastes og rapporteres som projeksjonsavvik.

## Dokumenterte endringskoder (T_ENDRING.KODE)

Endringskodene bevares som historikkmarkører uten at projeksjonen utleder en egen gyldighetsstatus:

| Kode | Betydning                  |
|------|----------------------------|
| AN   | Annullert                  |
| UA   | Uaktuell                   |
| F    | Førstegangsvedtak          |
| O    | Opphørt                    |
| E    | Endring beregningsgrunnlag |
| G    | G-regulering               |
| NY   | Ny                         |
| OO   | Overført ny løsning        |
| S    | Satsendring                |
| IN   | Nytt inntektsgrunnlag      |
| EB   | Ukjent (i SU-testdata)     |

Andre koder (AS, B, BB, H, I, KB, NB, TS, U, P, AV) er dokumentert for andre ytelser og kan forekomme
i SU-data — vi bevarer dem i `endringskoder` uten å tolke/validere dem i projeksjonen per nå.

## Lagrede beløpsperioder

Månedsbeløp med en periode og beløp som kan brukes, lagres med perioden fra delytelsen. Månedsbeløpet lagrer
også rå `TYPE_BELOP`-koder fra grunnlagsrader med samme vedtak og periode. Observerte koder er `ARBE`, `ARBM`,
`FTRE`, `FTRM`, `PENE`, `PENM` og `UTLM`, men listen er ikke uttømmende.

En kontroll av den analyserte projeksjonen fant 284 månedsbeløp. Alle 275 med FM hadde minst én grunnlagsrad med
nøyaktig samme `VEDTAK_ID`, `FOM` og `TOM`. De ni uten FM hadde ingen slike grunnlagsrader. Det fantes heller
ingen duplikate `TYPE_BELOP`-koder innen samme vedtak og periode.

Projeksjonen materialiserer ikke en egen ytelsestidslinje. Revurderingstjenesten bygger tidslinjen ved behov for én
person og valgt periode fra stønadene, vedtakene og månedsbeløpene i den låste projeksjonen.

`OPPDRAG_ID` beholdes i den transiente modellen og oppslagsprojeksjonen, men brukes ikke i tidslinjen.
`T_BESLUT.SENDT_TIL_OS`, `MOTTATT_FRA_OS` og `GODKJENT_AV_OS` konverteres også til den transiente modellen.
Systemdokumentasjonen bekrefter at vedtaks- og oppdragsdata ble sendt til
Oppdragssystemet, som returnerte en simulering og dannet utbetalingstransaksjoner, mens selve beløpet ble utbetalt
gjennom UR. Feltene kan derfor brukes som indikasjon på oversending og svar fra betalingskjeden. De dokumenterer
ikke alene at en bestemt utbetaling ble gjennomført.

```text
Infotrygd SU
  |
  | vedtak + delytelser
  | T_STONAD.OPPDRAG_ID identifiserer oppdraget
  | T_BESLUT.SENDT_TIL_OS registrerer oversending
  v
Oppdragssystemet (OS)
  |
  | simulerer og beregner utbetalings-/konteringslinjer
  | svar registreres i MOTTATT_FRA_OS og GODKJENT_AV_OS
  v
Utbetalingsreskontro (UR)
  |
  | gjennomfører utbetaling
  | historisk SU-rutine kunne hente «utbetalt t.o.m.»
  v
Mottaker
```

Gjeldende kandidat per måned utledes deretter fra:

- **Sekvens:** Ved overlappende perioder gjelder vedtaket med senest `TIDSPUNKT_REG`; numerisk `VEDTAK_ID` brukes
  som tie-breaker. Brukerhåndboken bekrefter at et nytt omregningsvedtak erstatter det forrige aktive vedtaket,
  selv om virkningsperioden starter tilbake i tid.

Denne logikken kjøres ikke når projeksjonen fullføres. Det normaliserte delsettet som trengs til senere oppslag
beholdes, mens det opprinnelige JSONB-snapshotet fortsatt er den tapsfrie kilden.

### Tidslinjeregel

Det er tilstrekkelig informasjon i uttrekket til å lage en månedlig tidslinje ved et senere behov:

1. Finn alle `T_STONAD` for personen via `T_LOPENR_FNR`.
2. Finn vedtakene for stønadene og forkast vedtak med endringskode `AN` eller `UA` og resultat `AN`.
3. Resultat `U` danner ikke en ny beløpsperiode, men viderefører den allerede gjeldende ytelsen.
4. Behold bare resultatene `I`, `DI`, `FI`, `IN`, `Ø` og `R` som ytelseskandidater.
5. Kombiner vedtaksperioden med delytelsesperioden og avgrens den med stønadens start- og opphørsdato.
   Delytelsesrader uten FOM eller med baklengs periode forkastes. Manglende delytelses-TOM betyr åpen periode og
   avgrenses til slutten av importmåneden.
6. For hver måned velges vedtaket som dekker måneden og har senest `TIDSPUNKT_REG`. Numerisk `VEDTAK_ID`
   er tie-breaker.
7. Beløpet hentes fra den valgte delytelsesperioden som dekker måneden.
8. En måned med valgt ytelsesvedtak og gyldig månedsbeløp er `Ytelse`; en måned uten dette er `IngenYtelse`.
   Tilstanden skal ikke utledes fra om beløpet er større enn null.

Denne utledningen kjøres ikke som del av konverteringen. Den kjøres når den historiske revurderingstjenesten
trenger originaltidslinjen for én person og valgt periode.

## Oppslag i den persisterte projeksjonen

Følgende oppslag er implementert uten data fra Oppdrag eller UR:

| Oppslag | Datagrunnlag | Semantikk |
|---------|--------------|-----------|
| `harSak(personident)` | `T_LOPENR_FNR` og `T_STONAD` | Personen har minst én historisk SU-stønad, uavhengig av om alle måneder ga ytelse |
| `hentVedtaksperioder(personident)` | `T_STONAD`, `T_VEDTAK`, nivå 02 i `T_STONADSKLASSE` og `T_SU` | Alle historiske vedtak med koder, periode, bosituasjon og årlig ytelsesbeløp |
| `hentMånedsbeløpForVedtak(vedtakId)` | Utledede MS-/FM-beløpsperioder i `historisk_alder_manedsbelop` | Beløpsperiodene for vedtaket i siste fullførte ordinære projeksjon |
| `hentTidslinje(personident, periode)` | Vedtaks- og månedsbeløpsperioder | Månedlig tidslinje med `Ytelse`/`IngenYtelse`, kildevedtak, bosituasjon, årlig ytelsesbeløp, sats, fradrag og utledet beløp |
| `harYtelsePåDato(personident, dato)` | Utledet tidslinje | Datoens måned er `Ytelse` |
| `harYtelseIMinstÉnMåned(personident, periode)` | Utledet tidslinje | Minst én måned i perioden er `Ytelse` |
| `harYtelseIHelePerioden(personident, periode)` | Utledet tidslinje | Alle måneder i perioden er `Ytelse` |

Navnet `harYtelse(personident, periode)` bør unngås fordi det ikke sier om delvis overlapp er nok. Oppslagene over
gjør denne forskjellen eksplisitt.

Projeksjonen persisteres i:

- `historisk_alder_projeksjon`, som styrer kjørings-ID, importversjon, dry-run-grense, behandlet antall og status,
- `historisk_alder_stonad`, med stønad-ID, personkobling, startdato og opphørsdato,
- `historisk_alder_vedtak`, med vedtak-ID, rå og tolket behandlingstype/resultat, virkningsperiode,
  registreringstidspunkt, bosituasjon og årlig ytelsesbeløp,
- `historisk_alder_manedsbelop`, med periode, sats, fradrag, rå fradragskoder og eventuell linje-ID fra
  konverteringen.

Stønadstabellen lagrer også rå og tolket opphørskode samt `OPPDRAG_ID`. Vedtakstabellen lagrer saksreferanse,
endringskoder, rå `REVURDERING_DATO` og statusfeltene for utveksling med Oppdragssystemet.

Den tidligere avledede tabellen `historisk_alder_ytelsesperiode` ble ikke lenger fylt og er fjernet i migrering
V301. Månedsbeløpsperioder leses direkte fra `historisk_alder_manedsbelop`.

Frontend henter månedsbeløpsperiodene med `POST /historisk/alderssak/manedsbelop` og body
`{"vedtakId":"<vedtak-id>"}`. Responsen er en liste med `linjeId`, `fraOgMed`, `tilOgMed`, `sats`, `fradrag`,
`fradragskoder` og utledet `beløp`. Import-ID, projeksjons-ID og personident eksponeres ikke. Endepunktet har
rollebasert tilgang, men personkontroll og audit er ikke implementert ennå.

Øvrige roller, detaljerte inntektsgrunnlag, klassifiseringer og beslutningsdata persisteres ikke her. Ved behov må
de leses fra råimporten eller få egne normaliserte tabeller. Konverteringsavvik og forbehold lagres som
oppsummeringer på projeksjonen.

Oppslag leser alltid siste fullførte ordinære projeksjon. Dersom en nyere projeksjon pågår, feiler eller er en
dry-run, fortsetter tjenesten å lese forrige fullførte ordinære versjon. Indekser dekker personoppslag og
vedtakskoblinger.

Frontend bruker følgende personruter med request-body `{"fnr":"<fødselsnummer>"}`:

```text
POST /historisk/alderssak/finnes
POST /historisk/alderssak/vedtaksperioder
```

Den første svarer med `{"harHistoriskAlderssak":true|false}`. Den andre svarer med en liste av
`HistoriskVedtaksperiode`, der Infotrygds `TYPE_SAK` eksponeres som `behandlingstypeRaw` og tolket
`behandlingstype`. Oppslaget inneholder også opphør, Oppdrag-ID, endringskoder, saksreferanse,
`REVURDERING_DATO` og statusfeltene for utveksling med Oppdragssystemet. Sakstypen er ikke et felt som utledes
fra `TYPE_SAK`: hele uttrekket gjelder `Sakstype.ALDER`. Begge rutene krever rollen Saksbehandler eller Attestant,
kontrollerer persontilgang som alderssak og auditerer oppslaget.

## Låst beløpsmodell

Reelle SU-data inneholder bare delytelsestypene `MS` (månedsats) og `FM` (fradrag månedsats).
`FRADRAG_TILLEGG` er stabilt `T` for `MS` og `F` for `FM`, `TYPE_SATS` er `M` og `TYPE_UTBETALING` er `L`.
Hele importen er kontrollert: Alle 199 587 delytelsesgrupper har nøyaktig én `MS`; 162 715 har én `FM`, mens
36 872 ikke har noen `FM`. Manglende `FM` normaliseres til null i den typede modellen, mens en eksisterende
`FM`-rad med manglende eller ugyldig beløp forkastes. Det ble ikke funnet manglende eller ugyldige beløp,
duplikate sats-/fradragslinjer eller utledede beløp som er null eller negative.

Modellen lagrer sats, fradrag og rå fradragskoder fra `T_BEREGN_GRL`-rader med samme vedtak og periode. Vedtatt
månedsbeløp utledes som `sats - fradrag` og lagres ikke separat.
Dette tilsvarer feltene `Mnd. sats`, `Fradrag mnd. sats` og `Sum ytelse` i SU UB. Oppdrag beregnet blant annet
etterbetaling og dannet utbetalingstransaksjoner, og beløpet ble utbetalt gjennom UR. Det historiske beløpet bør
derfor omtales som vedtatt eller beregnet ytelsesbeløp når vi ikke samtidig har opplysninger fra betalingskjeden.

### Historiske satser og avrunding

`T_BEREGN_FAKTOR` viser faktorene som ble brukt av SU-rutinen. Systemdokumentasjonen viser at beregningsbildet
hentet både grunnbeløp og satser gjennom servicerutinene `HENT-GRUNNBELOP` og `HENT-SATSER`. De observerte
faktorradene ligger i
[`historisk-su-beregningsfaktorer.csv`](historisk-su-beregningsfaktorer.csv).

Faktorene er råimportert, men ikke normalisert eller koblet til hvert vedtak i oppslagsprojeksjonen. Selve satsen
som ble registrert på vedtaket fremgår av MS-linjen og er derfor den mest direkte kilden ved visning av historiske
perioder.

Satsbildene er registrert i [`historisk-su-satser.csv`](historisk-su-satser.csv) og som typed Kotlin-data i
`HistoriskInfotrygdSats`. Serien inneholder alle 26 satsendringene fra januar 2006 til mai 2026. For 2006–2010
oppgir kilden G-faktorer. Fra mai 2011 oppgir den årsbeløp. `EV` finnes først fra januar 2016.

Fra 2017 til 2025 samsvarer årsbeløpene med de observerte MS-satsene etter månedsavrunding: årsbeløpet deles på
tolv og avrundes til hele kroner i MS. `MS × 12` kan dermed avvike fra årsbeløpet i satsbildet med opptil seks
kroner. Satsene for 2026 ligger utenfor det importerte datagrunnlaget.

### Hva oppslagsprojeksjonen kan vise

Den persisterte projeksjonen lagrer allerede MS som `sats` og FM som `fradrag` på månedsbeløpet. Den kan derfor
vise sats, fradrag og utledet månedsbeløp uten å bruke
`T_BEREGN_FAKTOR`.

Bosituasjon fra klassifiseringsnivå 02 og det årlige ytelsesbeløpet fra `T_SU` persisteres på vedtaket. Sats og
bosituasjon kan dermed kobles ved behov uten å tolke `T_BEREGN_FAKTOR`.

Beregningsfaktorene finnes fortsatt bare i råimporten eller den transiente konverteringsmodellen. `OPPDRAG_ID` og
oversendingsfeltene fra `T_BESLUT` persisteres i oppslagsprojeksjonen.

## Begrensninger i datagrunnlaget

- En kontroll av den aktive, fullførte projeksjonen fant to stønader uten Oppdrag-ID. Én har ingen vedtak med
  positiv ytelse eller registrert opphørsvedtak. Den andre har ett vedtak med positiv ytelse.
- Historiske revurderinger skal bruke SU-appens saksnummer som fagsystem-ID og nye ID-er for nye linjer, som
  ordinære revurderinger. Før den første korrigeringen kan sendes, må vi likevel finne de gamle
  utbetalingslinjene i Oppdrag. Oppdrag trenger dem for å sammenligne tidligere utbetalt beløp med det nye
  korrekte månedsbeløpet.
- Uttrekket har `T_STONAD.OPPDRAG_ID` for de fleste stønadene og `T_DELYTELSE.LINJE_ID` på månedsbeløpene, men vi
  har ikke bekreftet at disse identifiserer Oppdrag-linjene som skal endres. Løsningen må avklare hvordan linjene
  hentes fra Oppdrag, og hvordan stønaden med positiv ytelse uten Oppdrag-ID håndteres.
- Et annet migreringsløp har skissert følgende mønster: Opphør gamle transaksjoner, send hele det nye vedtaket på
  nytt fagområde og bruk justeringskonto mellom gammelt og nytt oppdrag. Økning utbetales på det nye fagområdet.
  Reduksjon gir feilkonto og kravgrunnlag på det gamle fagområdet. Vi må avklare om Oppdrag støtter samme mønster
  fra Infotrygd SU til `SUALDER`.
- Dagens kravgrunnlagskonsument tolker `fagsystemId` som SU-appens saksnummer og slår opp saken med dette nummeret.
  Hvis kravgrunnlaget for en historisk reduksjon kommer med Infotrygds gamle Oppdrag-ID, kan det ikke behandles av
  dagens flyt uten en mapping til SU-saken eller en endring i meldingsflyten.
- Kravgrunnlaget inneholder `utbetalingId`. Når den korrigerende utbetalingen er opprettet av den historiske
  kanalen, kan systemet finne det historiske vedtaket og revurderingen direkte fra denne ID-en. Det er mer presist
  enn å utlede opprinnelsen fra periodene, fordi flere etterfølgende historiske revurderinger kan berøre de samme
  månedene. Vi legger til grunn at Oppdrag returnerer den korrigerende utbetalingens ID også når feilkontoen
  gjelder det gamle Infotrygd-oppdraget.
- En kontroll av 195 493 kombinasjoner av historisk Oppdrag-ID og linje-ID fant ingen kombinasjoner brukt av flere
  stønader. 32 kombinasjoner var brukt i flere vedtak på samme stønad. Dette behandles som videreføring eller
  endring av samme Oppdrag-linje, ikke som en kollisjon.
- Det sammenlignbare migreringsscenarioet viser at første reduksjon etter overgangen får feilkonto og kravgrunnlag
  på det gamle fagområdet. Senere reduksjoner behandles på det nye fagområdet. Kravgrunnlagets `utbetalingId`
  brukes derfor som kobling til vedtak og sak, mens periodene brukes til å kontrollere de berørte månedene.
  Manuelle posteringer gjør at periodeoverlapp alene ikke er en sikker identifikator.
- Infotrygds `T_DELYTELSE.LINJE_ID` er en numerisk OS-linje-ID innenfor det gamle oppdraget. Den samme ID-en
  brukes på `MS` og `FM` som sammen danner ett netto månedsbeløp. Den er ikke det samme som `delytelseId`.
  I dagens løsning er `delytelseId` fagsystemets ID for oppdragslinja, mens OS tildeler `linjeId` og returnerer
  koblingen mellom dem. Importens skjemabeskrivelse inneholder bare `T_DELYTELSE.LINJE_ID`,
  `T_DELYTELSE.TYPE_DELYTELSE`, `T_MAP_DELYTELSE.TYPE_DELYTELSE` og `T_STONAD.OPPDRAG_ID`; den har ingen
  `delytelseId` eller `beslutningslinjeId`. Domenet bruker derfor `HistoriskOppdragLinjeId` for den importerte
  OS-ID-en. Før en historisk Oppdrag-mapper kan bygges, må vi hente Infotrygds opprinnelige `delytelseId` fra en
  annen kilde eller få bekreftet en OS-kontrakt for å endre eller opphøre linja med OS-identifikatorene.
- Oppdragssystemet og UR utgjorde betalingskjeden: Infotrygd sendte vedtaks-/oppdragsdata til Oppdrag, Oppdrag
  simulerte og dannet utbetalingstransaksjoner, og beløpet ble utbetalt gjennom UR. Uttrekket inneholder
  `OPPDRAG_ID` og metadata om oversending og svar fra OS, men ikke «utbetalt t.o.m.» fra UR eller en komplett
  reskontrohistorikk. Projeksjonen kan derfor vise beregnet/vedtatt månedsbeløp og indikasjon på oversending, men
  ikke alene bekrefte gjennomført utbetaling.
- Kildeskjemaet har ingen identifikator på radene i `T_BEREGN_GRL` og ingen valgt grunnlagsrad i `T_SU`.
  Grunnlagsradene kan knyttes til vedtaket, men ikke rangeres eller kobles én-til-én til
  `BELOP_BER_GRUNNLAG`. Dette kan ikke løses ved bare å utvide dagens uttrekk med eksisterende kolonner.

## Gjenstående uavklarte forhold

1. Tolv av 199 587 vedtak har `T_SU.BELOP_BER_GRUNNLAG` forskjellig fra `MS × 12`. Åtte er maskinelle
   satsomregninger med endringskode `S`, to er manuelle G-reguleringer med kode `G`, og to er revurderinger med
   kode `E`. Beløpene tilsvarer ulike historiske satsversjoner. Dette støtter at avvikene skyldes regulering eller
   periodisering i det gamle systemet, men vi skal ikke anta at `T_SU` og MS alltid ble oppdatert samtidig.
2. Den presise betydningen av `T_BELOPSTYPE.BEHANDLING`, blant annet `VM` og `BM`, er ikke bekreftet. Feltet er
   ikke nødvendig for å skille inntektseier fordi dette allerede fremgår av beløpstypekoden og teksten.
3. Klassekodene `OB` og `OR` på klassifiseringsnivå 03 mangler en bekreftet faglig betydning. De må beholdes som
   kildekoder og ikke navngis som satskategori før betydningen er dokumentert.
