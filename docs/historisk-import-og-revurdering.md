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

## Utkast til steg 3

Det er lagt til en separat historisk aldersmodell og en prosjektør som knytter råtabellene sammen via `STONAD_ID`,
`VEDTAK_ID` og personløpenummer. Modellen dekker:

- sammenhengende stønad og opphør,
- vedtaksperiode, sakstype, resultat og saksreferanse,
- stønadsklassifisering og relasjon til ektefelle/partner/samboer,
- valgt beregningsgrunnlag, årsinntekter og delytelseslinjer,
- endringskoder og beslutning/godkjenning.

Kjente sakstyper (`S`, `R`, `MG`, `MO`), resultater (`I`, `DI`, `FI`, `O`, `U`, `A`, `AN`),
stønadsklasser (`EN`, `EO`, `EU`, `EV`) og dokumenterte opphørskoder tolkes. Råkoden beholdes alltid. En ukjent kode
gir et projeksjonsavvik, men fører ikke til tap av raden.

Projeksjonen skal tilby et eget historisk utgangspunkt til opprettelse av revurdering. Den skal ikke konstruere et
kunstig moderne `VedtakSomKanRevurderes`, fordi dagens UUID-er, vilkår og grunnlag ikke finnes én-til-én i Infotrygd.

## Bekreftede antagelser (verifisert august 2026)

Følgende er bekreftet mot kildekoden i historisk-exodus-supstonad og presys PR #2937:

1. **Kildedatabasen er SU-scopet.** Uttrekket kommer fra `infotrygd_suq` — en dedikert SU-subbase. `KODE_RUTINE`
   finnes ikke i tabellene vi leser, og all data er allerede scopet til SU alder. Ingen ytelsestypefiltrering er
   nødvendig.
2. **Datoformat er ISO.** Oracle DATE → `rs.getDate().toLocalDate().toString()` = `yyyy-MM-dd`. Oracle TIMESTAMP →
   `rs.getTimestamp().toLocalDateTime().toString()` = `yyyy-MM-ddTHH:mm:ss[.nnnnnnnnn]`. Vår `take(10)` +
   `LocalDate.parse` er korrekt. Caveat: Oracle DATE kan inneholde klokkeslett som forkastes — akseptert risiko.
3. **KODE_KLASSE er komplett for SU alder.** `EN`, `EO`, `EU`, `EV` bekreftet. `KODE_NIVAA` = `OR` (Ordinær).
4. **KODE_RESULTAT er komplett.** `I`, `DI`, `FI`, `O`, `U`, `A`, `AN` — ingen ytterligere koder funnet for SU.
5. **KODE_OPPHOR er komplett.** `AN`, `AP`, `AÅ`, `FL`, `HI`, `IN`, `LU`, `SF`, `UT`, `DØ`, `UA` bekreftet.
6. **T_BEREGN_GRL joines på VEDTAK_ID.** PK er `(VEDTAK_ID, TYPE_BELOP, TIDSPUNKT_REG)`.
   `T_SU.VALGT_BEREGN_GRL` peker på `BEREGN_GRL_ID` og angir det valgte grunnlaget blant flere mulige.
7. **T_BESLUT støtter enstegs-godkjenning.** `SAKSBEHANDLER2` er nullable. Null kan bety enten enstegs-godkjenning
   eller uferdig behandling — kontekst må avgjøre.
8. **T_MAP_DELYTELSE kobler til OS/UR.** Join-nøkkel er `(VEDTAK_ID, LINJE_ID)` → `OPPDRAG_LINJE_ID`.
   `LINJE_ID` kan være unik kun innenfor `VEDTAK_ID` — join uten vedtak-scope kan koble feil.
9. **T_ROLLE.TYPE for SU alder.** `EP` (ektefelle/partner) bekreftet i testdata. Andre rolletyper er ukjent
   for SU alder spesifikt.

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
i SU-data — converteren logger dem som avvik uten datatap.

## Dokumentert utledning av gyldig vedtak

Det finnes ingen `GYLDIG`/`SLETTET`/`ERSTATTET`-kolonne. Gjeldende vedtak utledes fra:

- **Endringskoder:** AN/UA i T_ENDRING → vedtaket er ugyldig.
- **Perioder:** `DATO_INNV_FOM` < `DATO_INNV_TOM` (eller opphørsdato). Tomme/baklengs perioder er ugyldige.
- **Opphørsdato:** Hvis `DATO_OPPHOR` er satt og er ≤ `DATO_INNV_FOM`, var stønaden allerede opphørt.
- **OPPDRAG_ID:** NULL betyr at det aldri ble opprettet en oppdragslinje (aldri utbetalt). Årsaken (aldri sendt,
  eldre enn integrasjonen, avbrutt) er ukjent.
- **Sekvens:** Ved overlappende perioder gjelder nyere vedtak (høyere VEDTAK_ID).

Denne logikken er ikke implementert i projeksjonen ennå — vi lagrer alle rader tapsfritt og skal bygge
utledning som et eget steg.

## Gjenstående uavklarte forhold

1. Faktiske rader i `T_BELOPSTYPE` må vise hvordan inntekter for bruker og EPS skilles. `BEHANDLING` beholdes rått
   inntil betydningen er bekreftet.
2. `T_DELYTELSESTYPE.FRADRAG_TILLEGG`, `TYPE_SATS` og `TYPE_UTBETALING` må bekreftes før delytelseslinjer kan
   summeres til én opprinnelig månedsytelse.
3. `T_SU.BELOP_BER_GRUNNLAG` — presis semantikk (årlig grunnbeløp, G×faktor, annet) er uavklart. Testverdi:
   192125.00. Domenemodellen kaller det `valgtBeregningsgrunnlag` inntil videre.
4. Faktisk utbetalt beløp og «utbetalt t.o.m.» ser ut til å ha ligget i OS/Utbetalingsreskontroen. Uttrekket gir
   vedtaks- og oppdragslinjer, men det er ikke dokumentert at det gir komplett utbetalingshistorikk.
5. TYPE_SAK kan ha verdier utover `S`, `R`, `MG`, `MO` — kjente fra andre ytelser: `K` (klage), `GO`
   (grunnbeløpomregning), `KO` (konvertering), `MS` (maskinell satsomregning), `A` (anke), `SØ` (søknad om
   økning/endring). Ukjente tolkes som null + avvik.
6. Kobling `T_SU.VALGT_BEREGN_GRL` → `T_BEREGN_GRL.BEREGN_GRL_ID` er ikke implementert. Projeksjonen henter
   alle grunnlagsrader per vedtak uten å skille ut det valgte.
7. Testverdier i historisk-exodus-supstonad er syntetiske, ikke observerte produksjonsdata. Formater og koder
   bør verifiseres mot reelle uttrekk når tilgjengelig.
