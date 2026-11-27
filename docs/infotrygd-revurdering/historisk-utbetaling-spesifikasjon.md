# Utbetaling ved historisk revurdering

Status: Meldingsbygger og transport fra del 1 er koblet til historisk behandlingsflyt for utprøving i Q1.
Endringene i del 2 er ikke kompilert, testkjørt eller prøvd mot Oppdrag. Dokumentet beskriver ikke
en ferdig produksjonsimplementasjon eller en verifisert meldingskontrakt med Oppdrag.
Domene- og XML-testene fra første utkast var tidligere bekreftet grønne; det bekrefter ikke del 2.

Spesifikasjonen bygger videre på kildevalideringen i branchen
`bau/valider-kildetype-historisk-revurdering`. Den endrer ikke reglene for ordinær revurdering.
Import, beregning og behandlingsflyt er beskrevet i
[historisk-import-og-revurdering.md](historisk-import-og-revurdering.md).

## Målet

Vi skal kunne revurdere valgte historiske måneder, også når personen ikke har tidligere SU-utbetalinger
eller løpende ytelse. Månedene utenfor valgt periode skal ha samme økonomiske resultat som før.

Ved første revurdering håndterer vi utbetalingen fra Infotrygd. Etter iverksettelse bygger senere
revurderinger av de samme månedene på vedtaket fra vårt system. Dette følger samme prinsipp som ordinær
søknad og revurdering: et nytt vedtak erstatter tidligere resultat og blir grunnlaget for neste revurdering.

```text
Vedtak fra Infotrygd for valgte måneder
                  |
                  v
Første historiske revurdering
  Teknisk opphør mot Infotrygd
  + eventuelle NY-erstatningslinjer fra vårt system
                  |
             iverksettelse
                  |
                  v
Historisk vedtak fra vårt system
                  |
                  v
Senere historisk revurdering
  Korriger resultatet i vårt system
  Ikke send nytt opphør mot Infotrygd
```

Overgangen gjelder bare månedene som er revurdert. Andre måneder på samme sak kan fortsatt ha Infotrygd
som gjeldende kilde.

## Avklarte krav

- Én revurdering skal ha én gjeldende kildetype i hele valgt periode: Infotrygd eller tidligere historiske
  revurderingsvedtak fra SU-appen. En blanding skal avvises med en eksplisitt domenefeil. Ulike vedtak-ID-er
  innenfor samme kildetype er tillatt.
- Den tekniske instruksen mot Infotrygd er bare tillatt når gjeldende kilde er Infotrygd.
  Behandlingskanalen eller bevarte Infotrygd-referanser er ikke tilstrekkelig.
- En senere revurdering av vårt historiske vedtak skal fortsatt bruke den historiske behandlingskanalen.
  Den skal ikke flyttes til ordinær revurdering. Når SU-appen eier gjeldende vedtak og utbetaling, skal vi
  ikke først sende et særskilt opphør mot Infotrygd. Vi korrigerer vår egen utbetaling.
- Beregning og simulering bytter ikke gjeldende kilde. Kilden byttes når et iverksatt historisk vedtak
  legges over tidslinjen.
- Før videre behandling av utbetalingen må ferske gjeldende vedtaksdata kontrolleres mot de låste
  månedsreferansene. Før iverksettelse må kontrollen gjentas.
- Første korrigering skal kunne sendes som ett samlet oppdrag med teknisk opphør og eventuell erstatningsytelse.
  Erstatningslinjer med nye linje-ID-er skal ha `kodeEndringLinje = NY`. Oppdragets `kodeEndring`
  er et separat felt og kan være `ENDR` selv om de nye linjene har `NY`.
  Den nye ytelsen skal inneholde fullt korrekt månedsbeløp, ikke en løs differansebetaling.
- Ved faktisk opphør av ytelsen skal det ikke sendes erstatningsytelse for månedene som opphører.
- Den valgte perioden består av hele kalendermåneder. Flere sammenhengende måneder kan bare grupperes
  dersom meldingsformatet og øvrige linjefelt tillater det.
- Vi skal ikke automatisk sende alle senere Infotrygd-måneder på nytt. Behov for andre tekniske linjer må
  begrunnes med faktisk meldingskontrakt og observerte resultater.

Teknisk opphør av gammel utbetaling og ny ytelse i samme oppdrag er ikke det samme som å blande faglig
ytelse og opphør i én beregning. Dagens begrensninger mot blandet faglig resultat og mot samtidig
etterbetaling og feilutbetaling beholdes.
Vanlig korrigering av en SU-utbetaling kan fortsatt bruke opphørslinjer når resultatet krever det.
Det som ikke skal gjentas, er det innledende særskilte opphøret mot Infotrygd.

### Korreksjon etter Q1-utprøving og sammenligning med BA

Det tidligere forsøksformatet brukte `ENDR` på erstatningslinjer med nye ID-er. Et Q1-forsøk ble
avvist med «DELYTELSE-ID/LINJE-ID ved endring finnes ikke». Nye erstatningslinjer er derfor endret
til `NY`, etter verifisering av samme skille i BA og ordinær SU-mapping.

I [familie-oppdrags simuleringsmapper](https://github.com/navikt/familie-oppdrag/blob/94c163d5ea1659facec1bba27ab66ecc7e3b26bb/src/main/kotlin/no/nav/familie/oppdrag/simulering/SimulerBeregningRequestMapper.kt)
styres linjens `NY`/`ENDR` av `erEndringPåEksisterendePeriode`, uavhengig av oppdragets endringskode.
[BA-testen for endret migreringsdato](https://github.com/navikt/familie-ba-sak/blob/fff4fe2c642d9ea1fc4edd329f752864c4fed80b/src/test/resources/cucumber/oppdrag/endretMigreringsdato.feature)
gjenbruker en eksisterende periode-ID for opphøret og lager en ny periode-ID for erstatningen.
Kolonnen «Kode endring» i denne testen gjelder oppdraget, ikke den enkelte linjen.

Opphørsformatet i SU-kandidaten er ikke endret av denne korreksjonen.
[Generatorversjonen BA bruker](https://github.com/navikt/familie-utbetalingsgenerator/blob/49defa2ff9d7ec324d7ce5e459a84873db5bc3b3/src/main/kotlin/no/nav/familie/felles/utbetalingsgenerator/Utbetalingsgenerator.kt)
opphører ikke en kjede uten en eksisterende siste andel. Det verifiserte BA-eksempelet bekrefter derfor ikke
førstegangsoppdrag med nye opphørs-ID-er i SU. Opphør, referanser og virkning utenfor revurderingsperioden
må fortsatt prøves i Q1 før hele meldingsformatet kan regnes som avklart.

## Arbeidsforutsetninger om Oppdrag

Følgende legges til grunn for arbeidet. De skal ikke behandles som hindringer som må avklares på nytt før
vi kan lage en meldingskandidat:

- Oppsettet mellom gammelt Infotrygd-fagområde for supplerende stønad og `SUALDER` finnes.
- Oppdrag identifiserer gamle oppdrag gjennom person og konfigurert fagområde. Vi trenger ikke gamle
  Infotrygd-linjereferanser for denne mekanismen.
- Første oppdrag på `SUALDER` kan inneholde `OPPH`, som er Oppdrags statuskode for opphør.
- SU-appens saksnummer brukes som `fagsystemId`, identifikatoren for vårt oppdrag.
- Kravgrunnlag etter korrigeringen skal kunne behandles i vårt system.

Dette fastsetter ikke de konkrete XML-feltene eller hvordan Oppdrag avgrenser virkningen av et opphør.
Disse delene må prøves. Barnetrygdens gamle fagområde `IT05` skal ikke brukes som fagområde for SU.

## Hva som finnes i dagens kode

| Del | Verifisert oppførsel |
| --- | --- |
| `GjeldendeHistoriskInfotrygdVedtaksdata` | Bygger gjeldende resultat fra originalprojeksjonen og senere iverksatte månedsresultater. |
| `HistoriskInfotrygdMånedskilde` | Skiller `OriginalProjeksjon` fra `Revurderingsvedtak`. |
| `HistoriskInfotrygdRevurdering` | Låser vedtaksreferanser per måned og validerer én kildetype ved opprettelse og ny kontroll av gjeldende vedtak. |
| `UtbetalingRequestMapper` | Mapper vanlig `Ny` uten opphørsstatus. Vanlig `Endring` sendes som endring av en eksisterende linje. |
| `Utbetaling` | Krever en ikke-tom liste med ordinære utbetalingslinjer. |
| `SimuleringBodyXmlBuilder` | Har en intern variant som tar `OppdragRequest` og eksplisitt simuleringsperiode. |
| `SimuleringResponseMapper` | Filtrerer månedsresultater på vårt saksnummer og kjente fagområder, og avviser flere beholdte utbetalinger i samme måned. |
| `SimuleringProxyClientGcp` | Implementerer en egen historisk klientkontrakt med ufiltrert, typet respons og rå XML. Historisk service og route bruker denne kontrakten. Ordinær mapping er uendret. |
| `HistoriskInfotrygdRevurderingPostgresRepo.lagreVedtak` | Åpner sin egen transaksjon. |
| `FerdigstillVedtakService` | Slår opp ordinært vedtak ved ferdigstilling etter godkjent utbetalingskvittering. Historisk ferdigstilling må kobles inn. |
| Historisk `/iverksett`-route | Returnerer fortsatt konflikt. Produksjonsiverksettelse er ikke åpnet. |

Den ubrukte domenemetoden `forsøkIverksettelse()` er ikke den aktive sperren. Sperren i routen må beholdes
til den faktiske utbetalings- og iverksettelsesflyten er klar.

Vanlig `Endring.Opphør` forutsetter en eksisterende SU-linje i vår betalingshistorikk. Vi skal ikke lage
en kunstig tidligere SU-utbetaling eller svekke kjedeinvariantene for å representere et opphør mot Infotrygd.
At dagens modell ikke kan uttrykke dette direkte, er ikke bevis på en begrensning hos Oppdrag.

## Grunnlaget fra del 1

Første del inneholder en ren meldingsbygger med XML-tester. Byggeren skal ikke lagre eller iverksette noe,
gjøre nettverkskall eller åpne en ny route.

Del 1 inneholder også transport i den eksisterende simuleringsklienten, uten kobling til behandlingsflyten.
Del 2 kobler disse delene til service, lagring og route, som beskrevet nedenfor.

### Domenets utbetalingsgrunnlag

Kodeutkastet ligger i `domain/.../historisk/revurdering/utbetaling/HistoriskInfotrygdOppdragsgrunnlag.kt`.

Grunnlaget bygges fra revurderingen, dens beregning og ferske gjeldende vedtaksdata fra samme låste
projeksjon. Domenet skal kontrollere at behandlingen kan brukes, at beregningen dekker valgt periode,
at vedtaksreferansene ikke er endret, og at gjeldende kilde er Infotrygd.

Resultatet skal skille mellom teknisk opphør mot Infotrygd og eventuelle nye ytelsesbeløp. Et teknisk
opphør skal ikke gjøres om til en ordinær SU-endringslinje.

Kodeutkastet avviser fortsatt historiske måneder der gjeldende vedtak kommer fra vårt system.
Dette er en leveranseavgrensning for senere korrigering, ikke et forbud mot SU-utbetaling i en annen
periode. Sperren mot enhver eksisterende SU-utbetaling fra del 1 er fjernet i del 2.
Kildevalideringen gjenbrukes, ikke kopieres til meldingsbyggeren.

### Meldingsbygger i klientmodulen

Kodeutkastet ligger i
`client/.../oppdrag/simulering/historisk/HistoriskInfotrygdOppdragRequestBuilder.kt`.
Byggeren er intern i klientmodulen og returnerer et `UtbetalingRequest.OppdragRequest` for simulering.
Klientmodulen har allerede en avhengighet til domenemodulen.

Innverdiene omfatter utbetalingsgrunnlaget, saksnummer, personident, utbetalings-ID, behandler,
avstemmingsdata og nødvendige eksplisitte valg for det uverifiserte opphørsformatet.
Utbetalings-ID-en skal brukes som felles `henvisning` i meldingen.

Ukjente felt skal ikke få standardverdier som ser verifiserte ut. En kandidat skal merkes som
utforskende. Manglende nødvendige valg skal gi typed feil, ikke en delvis eller tilsynelatende gyldig melding.
Oppdragets endringskode og opphørslinjenes endringskode, datoer og sats er eksplisitte innverdier uten
standardverdier. Erstatningslinjenes kode er derimot fast `NY`. Linje-ID-er og eventuelle referanser til
foregående linjer i samme meldingskandidat må velges eksplisitt. Utkastet lager én opphørslinje per valgt måned.
Opphørslinjens statusdato følger valgt måned; oppgitte vedtaksdatoer er uverifiserte formatvalg og beviser
ikke hvilken periode Oppdrag påvirker.

Eksisterende `buildXmlRequestBody(simuleringsperiode, request)` kan brukes uten å endre vanlig
utbetalingsmapping. Ingen nye modulavhengigheter, utvidelse av `internal`-synlighet eller endringer i ordinær
`trengerNyttOppdrag()` er planlagt i første leveranse.
Den eksisterende XML-byggeren for simulering skriver linjens saksbehandler-ID `SU` som attestant-ID.
Del 2 beholder dette som i ordinær simulering. Det betyr ikke at behandlingen er attestert;
håndteringen ved faktisk iverksettelse må avklares separat.

### Tilfeller byggeren må skille mellom

Alle radene nedenfor forutsetter at gjeldende kilde er Infotrygd.

| Gjeldende resultat | Nytt resultat | Foreslått håndtering |
| --- | --- | --- |
| Ytelse | Ytelse med endret beløp | Teknisk opphør av gammel utbetaling og fullt korrekt ny ytelse i samme oppdrag. Eksakt format må prøves. |
| Ytelse | Opphør | Teknisk opphør uten ny ytelse. Eksakt format må prøves. |
| Ytelse | Ytelse med uendret beløp | Vedtak uten beløpsendring er tillatt. Om det trengs et betalingsoppdrag og teknisk opphør, er ikke bestemt. |
| Ingen ytelse | Ytelse | Ny ytelse er nødvendig. Behovet for teknisk opphør må avklares. |
| Ingen ytelse | Opphør | Behovet for et betalingsoppdrag må avklares. |

Infotrygd som kilde er et tillatelsesvilkår, ikke en regel om at alle måneder automatisk skal ha
opphørsinstruks. Åpne tilfeller skal ikke få en ny fagregel gjennom en teknisk standardverdi.

## Perioder og faktisk simulering

Eksempel: Saksbehandler revurderer mars til mai 2018. Personen har ingen løpende ytelse.
Meldingskandidaten skal beskrive korrigeringen for mars til mai, med teknisk opphør og eventuelle nye
fulle månedsbeløp. Den skal ikke automatisk ta med juni og senere Infotrygd-måneder.

Før produksjonsretning velges, må vi se hvilken periode Oppdrag faktisk påvirker. At XML-en bare nevner
valgte måneder, beviser ikke at virkningen er avgrenset til dem. Vi skal heller ikke anta at resten av
den gamle kjeden opphører.

Ved faktisk simulering må vi kunne velge en bredere simuleringsperiode enn revurderingsperioden.
Vi må undersøke hele responsen, også opplysninger fra gammelt fagområde eller annet `fagsystemId`.
Ordinære filtre skal ikke fjernes globalt.

### Transporten fra del 1

`SimuleringProxyClientGcp.simulerHistoriskUtbetaling(request, simuleringsperiode)` tar
meldingskandidaten og en eksplisitt simuleringsperiode. Kalleren bruker det validerte
`HistoriskInfotrygdOppdragsgrunnlag` og meldingsbyggeren før dette kallet.
Metoden er intern i klientmodulen. Del 2 bruker den gjennom `HistoriskSimuleringClient` i domenemodulen,
implementert av samme `SimuleringProxyClientGcp`. Det er ikke lagt til modulavhengigheter eller utvidet
`internal`-synlighet. Det ordinære `SimuleringClient`-grensesnittet er uendret.

Historisk og ordinær simulering deler HTTP-kallet til `/simulerberegning`, autentisering med
OBO- eller systemtoken og mapping av proxyfeil. Ordinær simulering bruker fortsatt
`SimuleringResponseMapper` med sine eksisterende filtre.

Den interne transportmetoden returnerer `HistoriskSimuleringTransportrespons` med hele den eksisterende
`SimulerBeregningResponse`-modellen og rå XML. Perioder, fagområder, oppdragsreferanser og detaljer
filtreres ikke bort, heller ikke ved ukjente klassekoder eller flere oppdrag i samme periode.
Infomeldinger og fravær av beregning beholdes som i proxyens respons. Et svar uten beregning
gjøres ikke om til en nullutbetaling. Ugyldig XML gir teknisk feil, også ved HTTP 200.
Del 2 mapper responsen til en egen historisk modell. Ordinær `Simulering` brukes ikke:
den har bare én utbetaling per måned, og den ordinære mapperen filtrerer på saksnummer, fagområde og
kjente klassekoder. Råsvaret alene gjør ikke den filtrerte modellen egnet for historisk resultatvisning.
Tolkning av økonomisk resultat på tvers av fagområder gjenstår.

Ingen kall mot et eksternt miljø er gjort som del av kodeutkastet. Tilkobling og miljøvalg er
forutsetninger for senere utprøving, ikke nye klienter eller miljøendringer i dette utkastet.
Reelle persondata og rå meldinger fra miljøene skal ikke legges i ordinær logg, dokumentasjon eller
testfasiter. Rå forespørsel og respons ved feil i XML-tolkningen går bare til sikkerlogg.
Testene bruker syntetiske data.

Simulering alene bekrefter ikke at faktisk iverksettelse, kvittering, kravgrunnlag og senere
revurderinger virker. Dette må prøves i egnet testmiljø før produksjonsiverksettelse åpnes.

## Del 2: Behandlingsflyt og kontrakt til frontend

Kompatibilitet: ikke nødvendig for det tidligere simuleringsfeltet fra del 1.
Produksjon: simuleringsflyten er ikke lansert. Konsumenter/data: ingen for dette feltet, avklart i oppgaven.
Eksisterende behandlinger, beregninger og importdata beholdes. Den eksisterende JSON-kolonnen brukes;
ingen tidligere Flyway-migrering er endret.

`HistoriskInfotrygdRevurderingService` kontrollerer behandlingsstatus, beregning og gjeldende vedtak
før klientkallet. Kallet gjøres uten å holde en database-session åpen. Etter svaret kontrolleres
vedtakene og SU-utbetalingshistorikken på nytt. Resultatet lagres bare dersom behandlingen fortsatt
er uendret. Sammenligningen og oppdateringen skjer i én kort transaksjon med radlås.

Resultatet lagres sammen med en kopi av beregningen. Ny beregning fjerner tidligere simulering,
også ved identiske beløp. `SIMULERT` krever et vellykket klientkall, beregningsresultat for riktig person
og minst én returnert beregningsperiode. En infomelding uten beregning regnes ikke som nullutbetaling
eller vellykket simulering. Disse kontrollene bekrefter teknisk gyldighet, ikke at meldingskandidaten
har riktig økonomisk virkning.

Meldingskandidaten bruker saksnummeret som `fagsystemId`. Oppdragets endringskode er `NY` uten tidligere
SU-utbetaling og `ENDR` med tidligere SU-utbetaling. Opphørslinjene har `NY`, nullsats og den enkelte
revurderingsmåneden som vedtaksperiode og statusdato. Eventuelle erstatningslinjer har `NY`, fullt
nytt månedsbeløp og ingen referanse til tidligere linjer. Dette er eksplisitte valg for utprøving.
De beviser ikke at kobling til eksisterende linjer er unødvendig eller at senere utbetalinger er upåvirket.
Det bygges ikke nye linjer for senere måneder.

### Endepunkt og svar

`POST /historisk/alderssak/revurderinger/{revurderingId}/simuler` krever saksbehandlerrolle og
samme persontilgang som øvrige historiske ruter, inkludert eksisterende avgrensning for Q1-testmodus.
Vellykket lagring auditeres som endring. Avvist persontilgang og klient-/domenefeil følger eksisterende
auditmønster.

Forespørselen angir hele kalendermåneder og må dekke revurderingsperioden. Den kan dekke flere måneder:

```json
{
  "simuleringsperiode": {
    "fraOgMed": "2018-03-01",
    "tilOgMed": "2018-12-31"
  }
}
```

Vellykket kall returnerer `200` med oppdatert `HistoriskInfotrygdRevurderingResponse`.
Feltet `simulering` er `null` før simulering og etter ny beregning. Når resultatet finnes, inneholder det:

| Felt | Innhold |
| --- | --- |
| `simuleringsperiode` | Perioden forespørselen ba Oppdrag simulere. |
| `simulert` | Tidspunktet resultatet ble mottatt og knyttet til beregningen. |
| `harEksisterendeSuOppdrag` | Om saken hadde oversendte SU-utbetalinger ved simuleringen. |
| `resultat` | Person, beregningsdato, faggruppe, Oppdrags samlede beløp og alle beregningsperioder. |
| `resultat.perioder[].utbetalinger[]` | Alle fagområder og `fagsystemId`, oppdragsreferanser, mottaker, forfall, feilkonto og detaljer. |
| `infomelding` | Eventuell infomelding, også når beregningen finnes. |

Detaljer beholder beløp, perioder, tilbakeføring, klassekode, klassetype og øvrige returnerte referanser.
Ukjente koder filtreres ikke bort. `resultat.beløp` skal ikke vises som beregnet SU-etterbetaling:
Oppdrags beløp kan omfatte andre ytelser. Rå XML lagres, men inngår ikke i frontend-responsen.
Vanlig henting av behandlingen returnerer samme lagrede resultat uten nytt klientkall.
JSON-formen er låst i `HistoriskSimuleringJsonTest`.

| HTTP-status | Feilkode |
| --- | --- |
| `400` | `ugyldig_historisk_simuleringsperiode` |
| `401` / `403` | Eksisterende autentiserings- og tilgangsfeil. |
| `404` | Eksisterende feil for ukjent historisk behandling. |
| `409` | `historisk_simulering_ikke_aktivert`, `historisk_simulering_grunnlag_endret`, `historisk_simulering_ugyldig_vedtaksgrunnlag` eller `historisk_simulering_ugyldig_tilstand`. |
| `502` | `historisk_simulering_klientfeil` eller `historisk_simulering_ugyldig_resultat`. |

Dette er backendens kontraktforslag til den separate frontend-PR-en. Frontend må støtte kall,
`SIMULERT`, resultatvisning, feilkoder og `MANGLER_SIMULERING` før håndhevingen aktiveres.

### Aktivering og avgrensning

`HISTORISK_SIMULERING_AKTIVERT` aktiverer utprøvingen bare i `su-se-bakover-q1` i `dev-gcp`.
Øvrige miljømanifester har verdien `false`. Local og Test bruker stub eller injisert fake, aldri en
ekte historisk simuleringsklient. Den lokale standardstubben svarer eksplisitt at simuleringen ikke er
tilgjengelig, fremfor å konstruere et økonomisk resultat.

`HISTORISK_SIMULERING_KREVES_FOR_ATTESTERING` er `false` i alle manifestene. Flagget styrer både
kravet ved sending og sperregrunnen i responsen. Dermed låses ikke dagens frontend av en sperregrunn
den ennå ikke kan håndtere. Kontroll av gjeldende vedtak gjelder også når flagget er av.
Iverksettelsesruten er fortsatt sperret.

### Utprøving i Q1

Utprøvingen er **ikke gjennomført**. Notatene om overgang fra Infotrygd, sammenkobling og kravgrunnlag
er spørsmål til utprøvingen, ikke dokumentasjon av observert Oppdrag-oppførsel.
Registrer avidentifiserte observasjoner, valgt periode, meldingskandidat og resultat for hvert tilfelle.
Ikke legg persondata eller rå meldinger i dokumentasjonen.

| Tilfelle | Det som skal dokumenteres | Status |
| --- | --- | --- |
| Bare tidligere Infotrygd-utbetaling, ingen løpende ytelse | Gammel og ny utbetaling i valgt periode; hvilke fagområder og oppdragsreferanser som returneres. | Ikke prøvd |
| Infotrygd i 2018 og SU-utbetaling i en senere periode | Om valgt kandidat fungerer på samme `fagsystemId`, og om teknisk kobling til eksisterende linjer trengs. | Ikke prøvd |
| Rent opphør | Resultatet uten erstatningsytelse og hvordan tidligere utbetaling vises. | Ikke prøvd |
| Bredere simuleringsperiode | Virkning før og etter revurderingsperioden, inkludert senere SU-ytelse. | Ikke prøvd |
| Flere fagområder og oppdragsreferanser | Hva som kan summeres som SU-resultat, og hva som tilhører andre ytelser. | Ikke prøvd |

At en person har senere løpende ytelse, er ikke en faglig avvisningsgrunn for en tidligere historisk
periode. Hvordan Oppdrag behandler meldingen må likevel observeres. Kravgrunnlag, kvittering og senere
korrigering av SU-utbetaling inngår ikke i denne leveransen.

## Videre produksjonsarbeid

Dette er nødvendige arbeidspunkter, ikke en godkjent detaljarkitektur:

1. **Representasjon og betalingshistorikk:** Finn en typet representasjon av Infotrygd-instruksen utenfor
   ordinær SU-linjekjede. Nye ytelseslinjer må bli ekte SU-linjer som senere revurderinger kan korrigere.
   Gjenbruk eksisterende lagring og transport der det er mulig. Ingen ny tabell er besluttet.
2. **Rent opphør:** Oppdrag uten ny ytelse må kunne lagres, lastes og følges opp uten kunstige ytelseslinjer.
   Dagens ikke-tomme `Utbetaling` og obligatoriske `utbetalingId` på historisk vedtak må håndteres eksplisitt.
   Det samme gjelder vedtak uten betalingsoppdrag. Dette er ikke løst ved bare å legge til nye `Ny`-linjer.
3. **Eksisterende SU-oppdrag:** Avklar kobling til vår betalingskjede og eventuell rekonstruksjon av senere
   SU-linjer. Dette er noe annet enn å sende senere Infotrygd-måneder på nytt. Uendrede måneders resultat skal bevares.
4. **Iverksettelse:** Én orkestrator skal kontrollere ferskt vedtaksgrunnlag, attestering og simulering, og
   lagre historisk vedtak, behandling og nødvendig betalingsinformasjon med samme transaksjonskontekst.
   `lagreVedtak` må kunne bruke denne konteksten uten å starte en nestet transaksjon.
5. **Publisering og feil:** Følg mønsteret for manuell iverksettelse med publisering sist i transaksjonen.
   Feil som skal rulle tilbake må utløse eksplisitt avbrudd. `Left` alene gir ikke rollback.
   Ny utsending må bruke samme identitet og lagrede melding, ikke lage et nytt oppdrag ved hvert forsøk.
6. **Kvittering og ferdigstilling:** Både koblingen til utbetalingen og ferdigstilling av historisk vedtak
   må støttes. Det er ikke tilstrekkelig at kvitteringen kan lagres på utbetalingen.
7. **Kravgrunnlag:** Verifiser returnerte identifikatorer og sørg for oppslag fra `utbetalingId` til
   historisk vedtak, sak og behandling. Senere revurderinger skal fortsatt gå i historisk kanal.
8. **Ny revurdering:** Når de valgte månedene er overtatt av vårt vedtak, skal neste revurdering ikke sende
   Infotrygd-opphør. Etter et rent opphør kan senere gjeninnvilgelse kreve nye SU-linjer selv om ingen
   SU-ytelseslinjer ble opprettet ved det første opphøret.

Løsningen må avklare hvordan instruksen og meldingen bevares for etterprøvbarhet, gjenlesing og retry.
At XML kan lagres, er ikke alene en beslutning om hvilken domenetilstand som skal lagres.
Endringer i modulansvar, modulavhengigheter eller eksisterende synlighet krever et eget valg før implementering.
Bakoverkompatibilitet og eventuelle datamigreringer må vurderes mot faktisk produksjonsstatus og lagrede data.

## Det som fortsatt må bestemmes eller prøves

| Tema | Konkret spørsmål |
| --- | --- |
| Meldingsformat | Godtar Oppdrag kombinasjonen av nye erstatningslinjer med `NY`, oppdragets `NY`/`ENDR`, opphørslinjens endringskode, `OPPH`, datoer og sats? |
| Periodeeffekt | Hvordan avgrenses korrigeringen til valgte måneder, og trengs det tekniske linjer utenfor dem? |
| Eksisterende SU-oppdrag | Hvordan kobles historisk korrigering til eksisterende SU-linjer på samme saksnummer? |
| Ingen økonomisk endring | Når kreves betalingsoppdrag eller teknisk opphør ved uendret beløp eller allerede opphørt måned? |
| Rent opphør og ingen melding | Hvordan representeres historisk vedtak og betaling når det ikke finnes nye ytelseslinjer, eller ikke skal sendes noen melding? |
| Simuleringsresultat | Hvordan viser responsen gammel og ny utbetaling, og hva må til for korrekt nettoresultat og kryssjekk? |
| Kravgrunnlag | Hvilket fagområde, `fagsystemId` og `utbetalingId` kommer tilbake i de aktuelle tilfellene? |

## Tester og godkjenningskriterier

Kildevalideringen har egne tester på den underliggende branchen og skal ikke implementeres på nytt her.

Kodeutkastet inneholder disse testene:

- `HistoriskInfotrygdOppdragsgrunnlagTest` følger originalt Infotrygd-vedtak gjennom første revurdering og
  et iverksatt historisk SU-vedtak til neste revurdering. Bare første revurdering får Infotrygd-grunnlag.
  Det testes både med ytelse og rent opphør i første vedtak. Samme test bruker den faktiske
  overlagringen av iverksatte månedsresultater, ikke bare en manuelt byttet kildetype.
- Domenetestene avviser også endret vedtaksgrunnlag, uendret beløp, allerede opphørt gammel ytelse og
  beregning som ikke dekker hele valgt periode.
- `HistoriskInfotrygdOppdragRequestBuilderTest` låser XML-kandidater med teknisk `OPPH` og
  erstatningslinjer med `NY` og fullt nytt beløp. Den tester også rent opphør uten erstatningslinjer,
  felles henvisning, manglende formatvalg, kolliderende linje-ID-er og ukjente linjereferanser.
- `SimuleringProxyClientGcpTest` sender meldingskandidater gjennom klienten til lokal WireMock.
  De nye testene dekker OBO- og systemtoken, bredere simuleringsperiode, rent opphør, infomelding uten
  beregning, proxyfeil og ugyldig XML. De kontrollerer at flere oppdrag, andre fagområder og ukjente
  klassekoder beholdes, og at råsvaret ikke endres. Eksisterende tester dekker ordinær simulering.

Dette er domene-, XML- og klienttester uten database eller eksternt Oppdrag-kall. Klienttestene gjør
HTTP-kall bare til lokal WireMock. Testene håndhever utkastets kildegrense, meldingsform og transport,
men tester ikke en ferdig senere SU-korrigeringsflyt. Den flyten er ikke koblet inn ennå.

Meldingsbyggeren skal testes med fast XML-fasit for hver valgt formatkandidat. Testene skal dekke første
oppdrag uten tidligere SU-utbetaling, nye fulle beløp, rent opphør uten erstatningsytelse, felles
utbetalingsreferanse og eksplisitt avvisning av tilfeller første leveranse ikke støtter.
Kontrollen av kilde og uendrede vedtaksreferanser skal dekke at grunnlaget har endret seg etter beregning.

Ved senere integrasjon skal vi også teste eksisterende SU-oppdrag, etterfølgende revurdering uten nytt
Infotrygd-opphør, gjeninnvilgelse etter rent opphør, kvittering, ferdigstilling, kravgrunnlag og ny utsending.
Databaseendringer krever databaseintegrasjonstester.

XML-tester dokumenterer hva vi sender, ikke at Oppdrag godtar meldingen eller gir riktig periodeeffekt.
Utprøving i testmiljø må vise at valgt periodes resultat blir korrekt, at andre måneders resultat bevares,
og at hele oppfølgingen kan gjennomføres.

Del 2 har nye service-, database-, route- og kontraktstester. De dekker blant annet simulering og
attestering, rent opphør, endret grunnlag, klientfeil, ny beregning, gjenlesing og audit. Testene er
ikke kjørt. Minste kontroll av endringen er:

```sh
./gradlew :client:test --tests '*SimuleringProxyClientGcpTest' --tests '*HistoriskInfotrygdOppdragRequestBuilderTest'
./gradlew :domain:test --tests '*HistoriskInfotrygdRevurderingTest' :service:test --tests '*HistoriskInfotrygdRevurderingServiceTest' :database:test --tests '*HistoriskInfotrygdRevurderingPostgresRepoTest' :web:test --tests '*HistoriskSimulering*' :common:infrastructure:test --tests '*ApplicationConfigTest' :web-regresjonstest:compileKotlin
./gradlew :client:spotlessCheck :domain:spotlessCheck :service:spotlessCheck :database:spotlessCheck :web:spotlessCheck :test-common:spotlessCheck :common:infrastructure:spotlessCheck :web-regresjonstest:spotlessCheck
```
