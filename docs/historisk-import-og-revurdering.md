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

## Steg 3 som fortsatt må modelleres

Før rådata kan brukes av revurdering må vi verifisere nøkler, kodeverk og gyldighetsperioder og lage en versjonert,
lesbar projeksjon. Den bør minst knytte person/sak, historisk vedtak, beregningsresultat per måned og grunnlag/fradrag
sammen, og samtidig beholde referansen tilbake til rå rad og import-ID.

Projeksjonen skal tilby et eget historisk utgangspunkt til opprettelse av revurdering. Den skal ikke konstruere et
kunstig moderne `VedtakSomKanRevurderes`, fordi dagens UUID-er, vilkår og grunnlag ikke finnes én-til-én i Infotrygd.
Før dette implementeres må kolonnene og kodene i særlig `T_VEDTAK`, `T_BEREGN_GRL`, `T_BEREGN_FAKTOR`, `T_DELYTELSE`,
`T_STONAD` og `T_SU` avklares mot reelle eksempeldata.
