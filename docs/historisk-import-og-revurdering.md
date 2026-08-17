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
- Rader og iterator-checkpoint lagres i samme transaksjon. En klientfeil kan derfor fortsette fra siste lagrede side.
- Radbredde, stillestående iterator og avvik fra forhåndstalt antall stopper og markerer importen som feilet.
- Uttrekksdata logges ikke.

## Forutsetning for de historiske vedtakene

Alle vedtakene fra Infotrygd i dette uttrekket gjelder supplerende stønad for alder. Projeksjonen skal derfor ikke
forsøke å utlede alder eller uføre fra de historiske radene. Kodene `EN`, `EO`, `EU` og `EV` beskriver
stønadsklassifisering/bosituasjon innenfor en alderssak.

## Utkast til steg 3

Det er lagt til en separat historisk aldersmodell og en prosjektør som knytter råtabellene sammen via `STONAD_ID`,
`VEDTAK_ID` og personløpenummer. Modellen dekker:

- sammenhengende stønad og opphør,
- vedtaksperiode, sakstype, resultat og saksreferanse,
- stønadsklassifisering og relasjon til ektefelle/partner/samboer,
- valgt beregningsgrunnlag, årsinntekter og delytelseslinjer,
- endringskoder og beslutning/godkjenning,
- en eksplisitt grense som avviser nytt revurderingsgrunnlag før 1. januar 2020.

Kjente sakstyper (`S`, `R`, `MG`, `MO`), resultater (`I`, `DI`, `FI`, `O`, `U`, `A`, `AN`),
stønadsklasser (`EN`, `EO`, `EU`, `EV`) og dokumenterte opphørskoder tolkes. Råkoden beholdes alltid. En ukjent kode
gir et projeksjonsavvik, men fører ikke til tap av raden.

Projeksjonen skal tilby et eget historisk utgangspunkt til opprettelse av revurdering. Den skal ikke konstruere et
kunstig moderne `VedtakSomKanRevurderes`, fordi dagens UUID-er, vilkår og grunnlag ikke finnes én-til-én i Infotrygd.

## Uavklarte forhold før projeksjonen kan brukes i produksjon

1. Faktiske rader i `T_BELOPSTYPE` må vise hvordan inntekter for bruker og EPS skilles. `BEHANDLING` beholdes rått
   inntil betydningen er bekreftet.
2. `T_DELYTELSESTYPE.FRADRAG_TILLEGG`, `TYPE_SATS` og `TYPE_UTBETALING` må bekreftes før delytelseslinjer kan
   summeres til én opprinnelig månedsytelse.
3. Det må bekreftes med eksempelrader at `T_STONADSKLASSE.KODE_KLASSE` inneholder `EN`/`EO`/`EU`/`EV`, og at
   `KODE_NIVAA` inneholder `OR`.
4. Strengformatene for Oracle `DATE`, `TIMESTAMP` og `NUMBER` fra uttrekks-API-et må bekreftes. Både råverdi og
   eventuelt tolket verdi beholdes inntil videre.
5. Faktisk utbetalt beløp og «utbetalt t.o.m.» ser ut til å ha ligget i OS/Utbetalingsreskontroen. Uttrekket gir
   vedtaks- og oppdragslinjer, men det er ikke dokumentert at det gir komplett utbetalingshistorikk.
6. Prosjektøren er foreløpig en ren, testbar transformasjon av et datasett. Før produksjonskjøring må rådata leses
   tabellvis eller partisjonert fra Postgres; alle rader skal ikke lastes i minnet samtidig.
