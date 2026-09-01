# Historisk import og revurdering fra 2020

## Avgrensning

Vi skal ikke beregne ytelsen på nytt for måneder før januar 2020. Den historiske beregningen og det historiske
vedtaket brukes som opprinnelig resultat. Rådata eldre enn 2020 kan fortsatt importeres for sporbarhet og for å
forstå sammenhengen mellom vedtak, delytelser og beregningsgrunnlag, men en senere projeksjon skal ikke gjøre dem om
til dagens behandlingsmodell.

For en revurdering som berører en måned fra og med januar 2020 er sammenligningen:

1. gammelt resultat hentes fra det historiske vedtaket/beregningen,
2. bare den berørte perioden fra og med januar 2020 beregnes med reglene og satsene som gjelder for perioden,
3. differansen mellom gammelt og nytt resultat brukes videre i revurderingen,
4. måneder før januar 2020 beholdes uendret.

At et gammelt beløp finnes er dermed nok til å bevare perioden. Det er ikke nok til å avgjøre en endring i en måned
fra 2020; den måneden må beregnes på nytt for å finne korrekt differanse.

## Steg 1 og 2 i dette utkastet

- `SupstonadHistoriskClient` henter tabelloversikt, antall rader og paginerte uttrekk.
- Importtjenesten krever det avtalte tabellsettet og et stabilt skjema.
- Alle verdier lagres som rå JSONB, og database-`NULL` bevares forskjellig fra tom tekst.
- Rader og iterator-checkpoint lagres i samme transaksjon per side.
- Enhver feil — klientfeil, skjemaavvik, radbreddeavvik, stillestående iterator eller avvik mot forhåndstalt antall — markerer hele importen som FEILET. Det er ingen gjenopptakelse; start en ny import.
- En ny importforespørsel avvises med HTTP 409 dersom en import allerede pågår.
- Sletteforsøk på en pågående import avvises med HTTP 409; en ikke-eksisterende import gir HTTP 404.
- Uttrekksdata logges ikke.

## Forutsetning for de historiske vedtakene

Alle vedtakene fra Infotrygd i dette uttrekket gjelder supplerende stønad for alder. Projeksjonen skal derfor ikke
forsøke å utlede stønadstype fra de historiske radene — alle saker er alder. Kodene `EN`, `EO`, `EU` og `EV` beskriver
stønadsklassifisering/bosituasjon innenfor en alderssak.

## Steg 3: historisk aldersmodell

Det er lagt til en separat historisk aldersmodell og en prosjektør som knytter råtabellene sammen via `STONAD_ID`,
`VEDTAK_ID` og personløpenummer. Modellen dekker:

- sammenhengende stønad og opphør,
- vedtaksperiode, sakstype, resultat og saksreferanse,
- stønadsklassifisering og relasjon til ektefelle/partner/samboer,
- valgt beregningsgrunnlag, årsinntekter og delytelseslinjer,
- endringskoder og beslutning/godkjenning.

Kjente sakstyper (`S`, `R`, `MG`, `MO`, `GO`, `MS`, `MB`, `FL`, `K`), resultater
(`I`, `DI`, `FI`, `IN`, `Ø`, `R`, `O`, `U`, `A`, `AN`),
stønadsklasser (`EN`, `EO`, `EU`, `EV`) og dokumenterte opphørskoder tolkes. Råkoden beholdes alltid. En ukjent kode
gir et projeksjonsavvik, men fører ikke til tap av raden.

Projeksjonen skal tilby et eget historisk utgangspunkt til opprettelse av revurdering. Den skal ikke konstruere et
kunstig moderne `VedtakSomKanRevurderes`, fordi dagens UUID-er, vilkår og grunnlag ikke finnes én-til-én i Infotrygd.

Modellen og rådatakonverteringen er implementert. Persistering av projeksjonen, utledning av gjeldende tidslinje og
oppslagsflater er ikke implementert ennå; `lagreBatch` i `SupstonadHistoriskService` logger foreløpig bare antallet
projiserte stønader.

## Bekreftede antagelser (verifisert august/september 2026)

Følgende er bekreftet mot kildekoden i historisk-exodus-supstonad og presys PR #2937:

1. **Kildedatabasen er SU-scopet.** Uttrekket kommer fra `infotrygd_suq` — en dedikert SU-subbase. `KODE_RUTINE`
   finnes ikke i tabellene vi leser, og all data er allerede scopet til SU alder. Ingen ytelsestypefiltrering er
   nødvendig.
2. **Datoformat er ISO.** Oracle DATE → `rs.getDate().toLocalDate().toString()` = `yyyy-MM-dd`. Oracle TIMESTAMP →
   `rs.getTimestamp().toLocalDateTime().toString()` = `yyyy-MM-ddTHH:mm:ss[.nnnnnnnnn]`. Vår `take(10)` +
   `LocalDate.parse` er korrekt. Caveat: Oracle DATE kan inneholde klokkeslett som forkastes — akseptert risiko.
3. **KODE_KLASSE er komplett for SU alder.** `EN`, `EO`, `EU`, `EV` bekreftet. `KODE_NIVAA` = `OR` (Ordinær).
4. **KODE_RESULTAT observert i reelle SU-data.** `I`, `DI`, `FI`, `IN`, `Ø`, `R`, `O`, `U`, `A`, `AN`.
   `IN` betyr innvilget ny situasjon, `Ø` betyr økning og `R` betyr redusert. Den tidligere antagelsen om at
   `I`, `DI`, `FI`, `O`, `U`, `A` og `AN` var komplett, ble avkreftet av et reelt uttrekk i september 2026.
5. **KODE_OPPHOR er komplett.** `AN`, `AP`, `AÅ`, `FL`, `HI`, `IN`, `LU`, `SF`, `UT`, `DØ`, `UA` bekreftet.
6. **T_BEREGN_GRL joines på VEDTAK_ID.** PK er `(VEDTAK_ID, TYPE_BELOP, TIDSPUNKT_REG)`.
   `T_SU.VALGT_BEREGN_GRL` peker på `BEREGN_GRL_ID` og angir det valgte grunnlaget blant flere mulige.
7. **T_BESLUT støtter enstegs-godkjenning.** `SAKSBEHANDLER2` er nullable. Null kan bety enten enstegs-godkjenning
   eller uferdig behandling — kontekst må avgjøre.
8. **T_MAP_DELYTELSE er et kodeverk.** Tabellen mapper `TYPE_DELYTELSE` og rutine til fagområde/fagområdereferanse.
   Den inneholder ikke `VEDTAK_ID`, `LINJE_ID` eller `OPPDRAG_LINJE_ID`, og kan derfor ikke koble en historisk
   delytelseslinje direkte til OS/UR. Reelle SU-data viser også at flere delytelser kan ha samme `LINJE_ID` innenfor
   samme vedtak.
9. **T_ROLLE.TYPE for SU alder.** `EP` (ektefelle/partner) bekreftet i testdata. Andre rolletyper er ukjent
   for SU alder spesifikt.
10. **Beløpsfeltene svarer til SU UB.** Brukerhåndboken beskriver `Mnd. sats` som månedsbeløpet før fradrag,
    `Fradrag mnd. sats` som reduksjonen per måned og `Sum ytelse` som månedsbeløpet etter fradrag.
    `Valgt beregningsgrunnlag` er full stønad etter satsen som gjelder for tilfellet. Dette bekrefter at
    vedtatt månedsbeløp er `MS - FM`, men ikke faktisk utbetalt beløp.
11. **Resultat `U` viderefører eksisterende stønad.** For revurdering med uendret resultat dannes det ikke ny
    vedtakslinje i SU VP eller ny linje til Oppdrag. En slik sak må derfor ikke tolkes som en ny beløpsperiode
    eller som opphør av den tidligere ytelsen.

## Dokumenterte endringskoder (T_ENDRING.KODE)

Kun `AN` (annullert) og `UA` (uaktuell) indikerer at et vedtak ikke er reelt/gyldig. Øvrige koder er
informative historikkmarkører:

| Kode | Betydning                  | Gyldighetspåvirkning |
|------|----------------------------|----------------------|
| AN   | Annullert                  | Ugyldig vedtak       |
| UA   | Uaktuell                   | Ugyldig vedtak       |
| F    | Førstegangsvedtak          | Ingen                |
| O    | Opphørt                    | Ingen (opphør er i T_STONAD) |
| E    | Endring beregningsgrunnlag | Ingen                |
| G    | G-regulering               | Ingen                |
| NY   | Ny                         | Ingen                |
| OO   | Overført ny løsning        | Ingen                |
| S    | Satsendring                | Ingen                |
| IN   | Nytt inntektsgrunnlag      | Ingen                |
| EB   | Ukjent (i SU-testdata)     | Ukjent               |

Andre koder (AS, B, BB, H, I, KB, NB, TS, U, P, AV) er dokumentert for andre ytelser og kan forekomme
i SU-data — vi bevarer dem i `endringskoder` uten å tolke/validere dem i projeksjonen per nå.

## Dokumentert utledning av gyldig vedtak

Det finnes ingen `GYLDIG`/`SLETTET`/`ERSTATTET`-kolonne. Gjeldende vedtak utledes fra:

- **Endringskoder:** AN/UA i T_ENDRING → vedtaket er ugyldig.
- **Perioder:** `DATO_INNV_FOM` < `DATO_INNV_TOM` (eller opphørsdato). Tomme/baklengs perioder er ugyldige.
- **Opphørsdato:** Hvis `DATO_OPPHOR` er satt og er ≤ `DATO_INNV_FOM`, var stønaden allerede opphørt.
- **OPPDRAG_ID:** NULL betyr at det aldri ble opprettet en oppdragslinje (aldri utbetalt). Årsaken (aldri sendt,
  eldre enn integrasjonen, avbrutt) er ukjent.
- **Sekvens:** Ved overlappende perioder gjelder vedtaket med senest `TIDSPUNKT_REG`; numerisk `VEDTAK_ID` brukes
  som tie-breaker. Brukerhåndboken bekrefter at et nytt omregningsvedtak erstatter det forrige aktive vedtaket,
  selv om virkningsperioden starter tilbake i tid.

Denne logikken er ikke implementert i projeksjonen ennå — vi lagrer alle rader tapsfritt og skal bygge
utledning som et eget steg.

### Tidslinjeregel

Det er tilstrekkelig informasjon i uttrekket til å lage en månedlig tidslinje:

1. Finn alle `T_STONAD` for personen via `T_LOPENR_FNR`.
2. Finn vedtakene for stønadene og forkast vedtak med endringskode `AN` eller `UA` og resultat `AN`.
3. Resultat `U` danner ikke en ny beløpsperiode, men viderefører den allerede gjeldende ytelsen.
4. Avgrens vedtaksperioden med stønadens `DATO_OPPHOR`, og forkast manglende eller baklengs perioder.
5. For hver måned velges vedtaket som dekker måneden og har senest `TIDSPUNKT_REG`. Numerisk `VEDTAK_ID`
   er tie-breaker.
6. Beløpet hentes fra den valgte delytelsesperioden som dekker måneden.
7. En måned med valgt ytelsesvedtak og gyldig månedsbeløp er `Ytelse`; en måned uten dette er `IngenYtelse`.
   Tilstanden skal ikke utledes fra om beløpet er større enn null.

Tidslinjen kan eksponeres som månedspunkter eller komprimeres til sammenhengende perioder med samme vedtak, sats
og fradrag. Den komprimerte formen er best egnet for visning og periodeoppslag, mens månedspunktene er enklest som
intern, entydig utledning.

## Oppslag som den persisterte projeksjonen skal støtte

Følgende oppslag kan bygges uten data fra Oppdrag eller UR:

| Oppslag | Datagrunnlag | Semantikk |
|---------|--------------|-----------|
| `harSak(personident)` | `T_LOPENR_FNR` og `T_STONAD` | Personen har minst én historisk SU-stønad, uavhengig av om alle måneder ga ytelse |
| `hentVedtaksperioder(personident)` | `T_STONAD` og `T_VEDTAK` | Alle historiske vedtak med råkode, tolket kode, virkningsperiode, registreringstidspunkt og gyldighetsstatus |
| `hentTidslinje(personident, periode)` | Gyldige vedtak og utledede månedsbeløp | Månedlig eller komprimert tidslinje med `Ytelse`/`IngenYtelse`, kildevedtak, sats, fradrag og utledet beløp |
| `harYtelsePåDato(personident, dato)` | Utledet tidslinje | Datoens måned er `Ytelse` |
| `harYtelseIMinstÉnMåned(personident, periode)` | Utledet tidslinje | Minst én måned i perioden er `Ytelse` |
| `harYtelseIHelePerioden(personident, periode)` | Utledet tidslinje | Alle måneder i perioden er `Ytelse` |

Navnet `harYtelse(personident, periode)` bør unngås fordi det ikke sier om delvis overlapp er nok. Oppslagene over
gjør denne forskjellen eksplisitt.

For effektiv bruk må projeksjonen persisteres i egne tabeller med minst personident/personløpenummer, stønad-ID,
vedtak-ID, virkningsperiode, registreringstidspunkt, gyldighetsstatus, sats og fradrag. Det må legges indekser for
personoppslag og periodeoverlapp. Rådataindeksene er tilstrekkelige for batchkonverteringen, men en tjeneste bør ikke
bygge hele tidslinjen på nytt fra JSONB for hvert oppslag.

## Låst beløpsmodell

Reelle SU-data inneholder bare delytelsestypene `MS` (månedsats) og `FM` (fradrag månedsats).
`FRADRAG_TILLEGG` er stabilt `T` for `MS` og `F` for `FM`, `TYPE_SATS` er `M` og `TYPE_UTBETALING` er `L`.
Hver delytelsesperiode har nøyaktig én `MS` og null eller én `FM`. Manglende `FM` normaliseres til null i den
typede modellen, mens rålinjene beholdes. Hele importen er kontrollert uten manglende sats, duplikate
sats-/fradragslinjer eller utledede beløp som er null eller negative.

Modellen lagrer sats og fradrag. Vedtatt månedsbeløp utledes som `sats - fradrag` og lagres ikke separat.
Dette tilsvarer feltene `Mnd. sats`, `Fradrag mnd. sats` og `Sum ytelse` i SU UB. Oppdrag beregnet blant annet
etterbetaling, og faktiske utbetalingsdata ble oppdatert i UR under utbetalingskjøring. Vedtaksbeløpet skal derfor
ikke omtales som faktisk utbetalt beløp.

## Gjenstående uavklarte forhold

1. Faktiske rader i `T_BELOPSTYPE` må vise hvordan inntekter for bruker og EPS skilles. `BEHANDLING` beholdes rått
   inntil betydningen er bekreftet.
2. `T_SU.BELOP_BER_GRUNNLAG` — utover skjermbildets beskrivelse som full stønad etter valgt sats er den presise
   tekniske semantikken (årlig grunnbeløp, G×faktor, annet) uavklart. Testverdi:
   192125.00. Domenemodellen kaller det `valgtBeregningsgrunnlag` inntil videre.
3. Faktisk utbetalt beløp og «utbetalt t.o.m.» ligger i OS/Utbetalingsreskontroen. Uttrekket gir
   vedtaks- og oppdragslinjer, men det er ikke dokumentert at det gir komplett utbetalingshistorikk.
4. TYPE_SAK `MG` (maskinell omregning) og `MO` (manuell omregning) er bekreftet i SU-brukerhåndboken.
   `GO` er dokumentert som manuell G-regulering i eldre systemdokumentasjon. `MS` (maskinell satsomregning),
   `MB` (maskinell beregning), `FL` (flyttesak) og `K` (klage) er bekreftet i Infotrygds kodehierarki og reelle
   SU-data. Andre kjente Infotrygd-koder er `KO` (konvertering), `A` (anke) og `SØ` (søknad om økning/endring).
5. Kobling `T_SU.VALGT_BEREGN_GRL` → `T_BEREGN_GRL.BEREGN_GRL_ID` er ikke implementert. Projeksjonen henter
   alle grunnlagsrader per vedtak uten å skille ut det valgte.
6. Testverdier i historisk-exodus-supstonad er syntetiske, ikke observerte produksjonsdata. Formater og koder
   bør verifiseres mot reelle uttrekk når tilgjengelig.
