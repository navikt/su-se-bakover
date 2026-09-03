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
- vedtaksperiode, sakstype, resultat og saksreferanse,
- stønadsklassifisering og relasjon til ektefelle/partner/samboer,
- valgt beregningsgrunnlag, årsinntekter og delytelseslinjer,
- endringskoder og beslutning/godkjenning.

Dette er den transiente konverteringsmodellen. Oppslagsprojeksjonen persisterer bare feltene som trengs for
personkobling, vedtaksoversikt og ytelsestidslinje. Klassifiseringer, roller, inntekter, SU-detaljer, beslutninger,
endringskoder og rå delytelseslinjer er fortsatt tilgjengelige i det tapsfrie JSONB-snapshotet, men persisteres
ikke i de normaliserte projeksjonstabellene.

Kjente sakstyper (`S`, `R`, `MG`, `MO`, `GO`, `MS`, `MB`, `FL`, `K`), resultater
(`I`, `DI`, `FI`, `IN`, `Ø`, `R`, `O`, `U`, `A`, `AN`),
stønadsklasser (`EN`, `EO`, `EU`, `EV`) og dokumenterte opphørskoder tolkes. Råkoden beholdes alltid. En ukjent kode
gir et projeksjonsavvik, men fører ikke til tap av rådata. Avvik og forbehold returneres internt fra
konverteringstjenesten, men persisteres ikke sammen med projeksjonen.

Projeksjonen skal tilby et eget historisk utgangspunkt til opprettelse av revurdering. Den skal ikke konstruere et
kunstig moderne `VedtakSomKanRevurderes`, fordi dagens UUID-er, vilkår og grunnlag ikke finnes én-til-én i Infotrygd.
Oppslagene er foreløpig serviceoperasjoner og er ikke koblet inn i revurderingsflyten.

Modellen, rådatakonverteringen, persisteringen og oppslagsflatene er implementert. Konverteringen oppretter en
importversjonert projeksjon, lagrer normaliserte stønader, vedtak og månedsbeløp batchvis og bygger deretter en
komprimert tidslinje før projeksjonen merkes `FULLFØRT`. En projeksjon med status `PÅGÅR` eller `FEILET` er ikke
synlig for oppslag. Driftsruten starter konverteringen asynkront på `Dispatchers.IO` og svarer HTTP 202; sluttstatus
må derfor leses fra projeksjonen og logger, ikke fra HTTP-responsen.

### Rolle per importert tabell

Alle de 16 avtalte tabellene lagres tapsfritt. Konvertereren bruker 13 av dem:

Kolonner og datatyper i kildeskjemaet er dokumentert separat i
[`infotrygd-suq-datamodell.sql`](infotrygd-suq-datamodell.sql). Filen er referansedokumentasjon, ikke en
database-migrering.

| Tabell | Bruk i konverteringen |
|--------|------------------------|
| `T_STONAD` | Rot for batching; stønad, personløpenummer, startdato og opphør |
| `T_VEDTAK` | Vedtak, periode, sakstype, resultat og saksreferanse |
| `T_LOPENR_FNR` | Kobler personløpenummer til personident for stønad, rolle og delytelsesmottaker |
| `T_BELOPSTYPE` | Kodeverk for inntektsrader |
| `T_DELYTELSESTYPE` | Kodeverk og fortegn for delytelser |
| `T_KLASSENIVAA` | Tekst for klassifiseringsnivå |
| `T_STONADSKLASSE` | Historisk stønadsklasse/bosituasjon |
| `T_ROLLE` | Rolle og relatert person |
| `T_SU` | Årlig ytelsesbeløp i `BELOP_BER_GRUNNLAG` og revurderingsdato |
| `T_BEREGN_GRL` | Inntekts-/beregningsgrunnlagsrader per vedtak |
| `T_DELYTELSE` | Rå delytelser og utledning av månedsbeløp |
| `T_ENDRING` | Endringskoder; `AN` og `UA` påvirker vedtakets gyldighet |
| `T_BESLUT` | Beslutning og godkjenning |
| `T_BEREGN_FAKTOR` | Historiske beregningsfaktorer og satser; råimporteres, men brukes ikke av konvertereren |
| `T_KJOREPLAN_AVST` | Råimporteres, men brukes ikke av konvertereren |
| `T_MAP_DELYTELSE` | Råimporteres som kodeverk, men brukes ikke av konvertereren |

### Begrepsliste

| Begrep | Betydning | Hva det brukes til | Hvor det forekommer |
|--------|-----------|---------------------|----------------------|
| Råimport / snapshot | Tapsfri kopi av kildeuttrekket for én import | Sporbarhet og grunnlag for ny projeksjon uten nytt uttrekk | `historisk_import`, `historisk_import_tabell`, `historisk_import_rad.data` |
| Projeksjon | Versjonert, normalisert lesemodell av én fullført råimport | Personoppslag, vedtaksoversikt og ytelsestidslinje | `historisk_alder_projeksjon` og de øvrige `historisk_alder_*`-tabellene |
| Stønad | En historisk SU-alderssak knyttet til personløpenummer | Grupperer vedtak og avgrenser dem med start/opphør | `T_STONAD`, normalisert i `historisk_alder_stonad` |
| Vedtak | Historisk avgjørelse innenfor en stønad | Kilde til resultat, virkningsperiode og rekkefølge | `T_VEDTAK`, normalisert i `historisk_alder_vedtak` |
| Resultat | Utfallet registrert på vedtaket, for eksempel `FI` | Avgjør om vedtaket kan danne en ny ytelsesperiode | `T_VEDTAK.KODE_RESULTAT` |
| Vedtaksstatus | Finnes ikke som eget felt i kilden | Må utledes fra resultat, endringskoder, periode og eventuelt beslutnings-/opphørsdata | Utledet; ikke en kildekolonne |
| Gyldig vedtak | Vedtak med komplett, ikke-baklengs periode som ikke er annullert/uaktuelt | Visning av historikk og første filter før tidslinjeutledning | Utledet til `historisk_alder_vedtak.gyldig` |
| Baklengs vedtak / tom periode | Vedtak der FOM er etter TOM; flere har TOM dagen før FOM | Bevares som historikk, men kan ikke danne ytelsesmåneder | Utledes fra `T_VEDTAK.DATO_INNV_FOM > DATO_INNV_TOM` |
| Vedtaksperiode | Vedtakets registrerte virkningsperiode | Ytre periodegrense for vedtaket | `T_VEDTAK.DATO_INNV_FOM` og `DATO_INNV_TOM` |
| Delytelse | Beløpslinje som tilhører et vedtak | Grunnlag for å utlede sats, fradrag og vedtatt månedsbeløp | `T_DELYTELSE` |
| MS | Månedsats før fradrag | Sats i det utledede månedsbeløpet | `T_DELYTELSE.TYPE_DELYTELSE = 'MS'` |
| FM | Fradrag i månedsatsen | Trekkes fra MS; manglende FM betyr null kroner i fradrag | `T_DELYTELSE.TYPE_DELYTELSE = 'FM'` |
| Inntektseier | Om en grunnlagsrad gjelder stønadsmottakeren eller ektefellen | Skiller hvilke inntekter som skal påvirke beregningen | Kodet i `T_BEREGN_GRL.TYPE_BELOP`: brukte koder ender på `M` for stønadsmottaker og `E` for ektefelle |
| Årlig ytelsesbeløp | Årsbeløpet som ble registrert for vedtaket | Historisk satsinformasjon; tilsvarer normalt den avrundede månedsatsen multiplisert med tolv | `T_SU.BELOP_BER_GRUNNLAG`; tilsvarer normalt `MS × 12` |
| Delytelsesperiode | Perioden en MS/FM-gruppe gjelder | Snevrer inn vedtaksperioden; null TOM betyr åpen periode | `T_DELYTELSE.FOM` og `T_DELYTELSE.TOM` |
| Månedsbeløp | Vedtatt beløp beregnet som MS minus FM | Beløp i ytelseskandidaten; er ikke nødvendigvis faktisk utbetalt | Utledet i konvertereren, lagret i `historisk_alder_manedsbelop` |
| Ytelseskandidat | Gyldig vedtak og månedsbeløp som dekker en måned | Kandidat før siste vedtak for personen velges | Utledet når projeksjonen fullføres |
| Ytelsesperiode | Sammenhengende måneder med samme valgte vedtak, sats og fradrag | Komprimert lagring og periodeoppslag | `historisk_alder_ytelsesperiode` |
| Ytelsestidslinje | Én eksplisitt tilstand per måned: `Ytelse` eller `IngenYtelse` | Historisk oppslag for revurderingsperioder | Bygges av `HistoriskAlderOppslag` fra lagrede ytelsesperioder |
| Opphør | Avslutning registrert på stønaden | Avgrenser ytelseskandidater, men inngår ikke direkte i `gyldig` på vedtaket | `T_STONAD.KODE_OPPHOR`, `DATO_OPPHOR` og `TIDSPUNKT_OPPHORT` |
| Oppdragssystemet (OS) | Systemet Infotrygd sendte vedtaks- og oppdragsdata til for simulering og utbetaling | Beregnet utbetalings-/konteringslinjer og dannet utbetalingstransaksjoner | `T_STONAD.OPPDRAG_ID` og oversendingsfeltene i `T_BESLUT` |
| Utbetalingsreskontro (UR) | Utbetalingsdelen av den historiske betalingskjeden | Pengene ble utbetalt gjennom UR; SU-rutinen kunne hente «utbetalt t.o.m.» derfra | Ikke med i uttrekket; omtalt av servicerutinen `HENT-UTBET-TOM-FRA-UR` |
| Oversendt til OS | Metadata om utvekslingen mellom Infotrygd og Oppdragssystemet | Kan dokumentere at et vedtak ble sendt og at svar ble mottatt, men er ikke alene bevis på gjennomført utbetaling | `T_BESLUT.SENDT_TIL_OS`, `MOTTATT_FRA_OS` og `GODKJENT_AV_OS` |
| Endringskode | Historikkmarkør på et vedtak | `AN` og `UA` gjør vedtaket ugyldig; øvrige koder bevares som informasjon | `T_ENDRING.KODE` |
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
     bekreftet. En PoC fra Bisys bruker `OB` om oppfostringsbidrag, men det er et annet kodeverk og dokumenterer
     ikke betydningen av `T_STONADSKLASSE.KODE_KLASSE` i SU.

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
12. **Observerte sakstyper er dekket.** `MG` (maskinell omregning), `MO` (manuell omregning), `GO` (manuell
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
    - Sakstypene er `MB` (maskinell beregning) eller `R` (revurdering).
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
      - De tre `E`-vedtakene følger umiddelbart etter et gyldig `MB`-vedtak på samme stønad. Det gyldige vedtaket
        har samme FOM og en reell TOM, mens det baklengse vedtaket har TOM dagen før FOM.
      - `S`-vedtaket starter måneden etter at en foregående `MO`-periode slutter, men har TOM dagen før sin egen
        FOM.
    - Alle 13 har `OPPDRAG_ID`, mens ingen har en rad i `T_BESLUT`.

    De 13 markeres ugyldige fordi perioden ikke kan brukes og det ikke finnes en entydig dato å korrigere dem til.
    Baklengs periode kan være knyttet til opphør i enkelte tilfeller, men er ikke i seg selv en opphørsstatus.
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

Blant endringskodene er det kun `AN` (annullert) og `UA` (uaktuell) som gjør et vedtak ugyldig. Øvrige koder er
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

## Gyldig vedtak og kandidat til ytelsestidslinjen

Det finnes ingen `GYLDIG`/`SLETTET`/`ERSTATTET`-kolonne i kilden. Den persisterte `gyldig`-verdien betyr at:

- **Endringskoder:** AN/UA i T_ENDRING → vedtaket er ugyldig.
- **Perioder:** Både `DATO_INNV_FOM` og `DATO_INNV_TOM` finnes, og FOM er før eller lik TOM.
- **Resultat:** Resultatet er ikke `AN` (annullert). Andre resultater kan være gyldige historiske vedtak uten å
  representere en ny ytelsesperiode.

Et gyldig vedtak blir bare kandidat til ytelsestidslinjen når resultatet er `I`, `DI`, `FI`, `IN`, `Ø` eller `R`,
det finnes et gyldig utledet månedsbeløp, og stønaden kan kobles til personident. Kandidatperioden avgrenses av
stønadens `DATO_START`, vedtaksperioden, delytelsesperioden og eventuell `DATO_OPPHOR`. En avgrensning som gir en
baklengs periode produserer ingen ytelsesmåneder.

`OPPDRAG_ID` beholdes i den transiente modellen, men brukes ikke i gyldighetsvurderingen eller tidslinjen og
persisteres ikke i oppslagsprojeksjonen. `T_BESLUT.SENDT_TIL_OS`, `MOTTATT_FRA_OS` og `GODKJENT_AV_OS` konverteres
også til den transiente modellen. Systemdokumentasjonen bekrefter at vedtaks- og oppdragsdata ble sendt til
Oppdragssystemet, som returnerte en simulering og dannet utbetalingstransaksjoner, mens selve beløpet ble utbetalt
gjennom UR. Feltene kan derfor brukes som indikasjon på oversending og svar fra betalingskjeden. De dokumenterer
ikke alene at en bestemt utbetaling ble gjennomført.

Gjeldende kandidat per måned utledes deretter fra:

- **Sekvens:** Ved overlappende perioder gjelder vedtaket med senest `TIDSPUNKT_REG`; numerisk `VEDTAK_ID` brukes
  som tie-breaker. Brukerhåndboken bekrefter at et nytt omregningsvedtak erstatter det forrige aktive vedtaket,
  selv om virkningsperioden starter tilbake i tid.

Denne logikken kjøres når projeksjonen fullføres. Det normaliserte delsettet som trengs til oppslag beholdes sammen
med den ferdig utledede tidslinjen, mens det opprinnelige JSONB-snapshotet fortsatt er den tapsfrie kilden.

### Tidslinjeregel

Det er tilstrekkelig informasjon i uttrekket til å lage en månedlig tidslinje:

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

Tidslinjen kan eksponeres som månedspunkter eller komprimeres til sammenhengende perioder med samme vedtak, sats
og fradrag. Den komprimerte formen er best egnet for visning og periodeoppslag, mens månedspunktene er enklest som
intern, entydig utledning.

## Oppslag i den persisterte projeksjonen

Følgende oppslag er implementert uten data fra Oppdrag eller UR:

| Oppslag | Datagrunnlag | Semantikk |
|---------|--------------|-----------|
| `harSak(personident)` | `T_LOPENR_FNR` og `T_STONAD` | Personen har minst én historisk SU-stønad, uavhengig av om alle måneder ga ytelse |
| `hentVedtaksperioder(personident)` | `T_STONAD` og `T_VEDTAK` | Alle historiske vedtak med råkode, tolket kode, virkningsperiode, registreringstidspunkt og gyldighetsstatus |
| `hentTidslinje(personident, periode)` | Gyldige vedtak og utledede månedsbeløp | Månedlig tidslinje med `Ytelse`/`IngenYtelse`, kildevedtak, sats, fradrag og utledet beløp |
| `harYtelsePåDato(personident, dato)` | Utledet tidslinje | Datoens måned er `Ytelse` |
| `harYtelseIMinstÉnMåned(personident, periode)` | Utledet tidslinje | Minst én måned i perioden er `Ytelse` |
| `harYtelseIHelePerioden(personident, periode)` | Utledet tidslinje | Alle måneder i perioden er `Ytelse` |

Navnet `harYtelse(personident, periode)` bør unngås fordi det ikke sier om delvis overlapp er nok. Oppslagene over
gjør denne forskjellen eksplisitt.

Projeksjonen persisteres i:

- `historisk_alder_projeksjon`, som styrer importversjon og status,
- `historisk_alder_stonad`, med stønad-ID, personkobling, startdato og opphørsdato,
- `historisk_alder_vedtak`, med vedtak-ID, rå og tolket sakstype/resultat, virkningsperiode,
  registreringstidspunkt og gyldighetsstatus,
- `historisk_alder_manedsbelop`, med periode, sats, fradrag og eventuell linje-ID fra konverteringen,
- `historisk_alder_ytelsesperiode`, med ferdig valgte og komprimerte ytelsesperioder.

Opphørskode, oppdrag-ID og de øvrige delene av den transiente modellen persisteres ikke her. Ved behov må de leses
fra råimporten eller få egne normaliserte tabeller. Konverteringsavvik og forbehold lagres heller ikke; den
asynkrone driftsruten logger bare antallet avvik.

Oppslag leser alltid siste fullførte projeksjon. Dersom en nyere projeksjon pågår eller feiler, fortsetter tjenesten
å lese forrige fullførte versjon. Indekser dekker personoppslag, vedtakskoblinger og periodeoverlapp. Den månedlige
tidslinjen bygges fra de komprimerte periodene og fyller eksplisitt inn `IngenYtelse` for hull i etterspurt periode.
Oppslagene er foreløpig tilgjengelige som serviceoperasjoner; egne HTTP-ruter er ikke etablert.

## Låst beløpsmodell

Reelle SU-data inneholder bare delytelsestypene `MS` (månedsats) og `FM` (fradrag månedsats).
`FRADRAG_TILLEGG` er stabilt `T` for `MS` og `F` for `FM`, `TYPE_SATS` er `M` og `TYPE_UTBETALING` er `L`.
Hele importen er kontrollert: Alle 199 587 delytelsesgrupper har nøyaktig én `MS`; 162 715 har én `FM`, mens
36 872 ikke har noen `FM`. Manglende `FM` normaliseres til null i den typede modellen, mens en eksisterende
`FM`-rad med manglende eller ugyldig beløp forkastes. Det ble ikke funnet manglende eller ugyldige beløp,
duplikate sats-/fradragslinjer eller utledede beløp som er null eller negative.

Modellen lagrer sats og fradrag. Vedtatt månedsbeløp utledes som `sats - fradrag` og lagres ikke separat.
Dette tilsvarer feltene `Mnd. sats`, `Fradrag mnd. sats` og `Sum ytelse` i SU UB. Oppdrag beregnet blant annet
etterbetaling og dannet utbetalingstransaksjoner, og beløpet ble utbetalt gjennom UR. Det historiske beløpet bør
derfor omtales som vedtatt eller beregnet ytelsesbeløp når vi ikke samtidig har opplysninger fra betalingskjeden.

### Historiske satser og avrunding

`T_BEREGN_FAKTOR` viser faktorene som ble brukt av SU-rutinen. Systemdokumentasjonen viser at beregningsbildet
hentet både grunnbeløp og satser gjennom servicerutinene `HENT-GRUNNBELOP` og `HENT-SATSER`. Observerte
faktorserier er:

| Periode | Enslig | EPS under 67 | EPS over 67 |
|---------|--------|--------------|-------------|
| Fra 2006 | 1,7933 | 2,2933 | 1,6433 |
| Fra mai 2008 | 1,94 | 2,44 | 1,79 |
| Fra mai 2009 | 1,97 | 2,47 | 1,82 |
| Fra mai 2010 | 2,00 | 2,50 | 1,85 |

Faktorene er råimportert, men ikke normalisert eller koblet til hvert vedtak i oppslagsprojeksjonen. Selve satsen
som ble registrert på vedtaket fremgår av MS-linjen og er derfor den mest direkte kilden ved visning av historiske
perioder.

Følgende årsbeløp er transkribert fra satsbildene. Fra 2017 til 2025 samsvarer de med de observerte MS-satsene
etter månedsavrunding: årsbeløpet deles på tolv og avrundes til hele kroner i MS. `MS × 12` kan dermed avvike fra
årsbeløpet i satsbildet med opptil seks kroner. Satsene for 2026 ligger utenfor det importerte datagrunnlaget og
kan ikke kontrolleres mot vedtakene.

| Virkning fra | EN | EU | EO | EV | Kontroll mot import |
|--------------|----:|----:|----:|----:|----------------------|
| 01.05.2026 | 253 787 | 253 787 | 234 765 | 234 765 | Ikke dekket av importen |
| 01.05.2025 | 242 418 | 242 418 | 224 248 | 224 248 | Samsvarer etter månedsavrunding |
| 01.05.2024 | 233 746 | 233 746 | 216 226 | 216 226 | Samsvarer etter månedsavrunding |
| 01.05.2023 | 227 468 | 227 468 | 210 418 | 210 418 | Samsvarer etter månedsavrunding |
| 01.05.2022 | 209 571 | 209 571 | 193 862 | 193 862 | Samsvarer etter månedsavrunding |
| 01.05.2021 | 202 425 | 202 425 | 187 252 | 187 252 | Samsvarer etter månedsavrunding |
| 01.01.2021 | 192 125 | 192 125 | 177 724 | 177 724 | Samsvarer etter månedsavrunding |
| 01.05.2020 | 193 188 | 193 188 | 183 587 | 183 587 | Samsvarer etter månedsavrunding |
| 01.05.2019 | 191 422 | 191 422 | 181 908 | 181 908 | Samsvarer etter månedsavrunding |
| 01.09.2017 | 181 744 | 181 744 | 172 711 | 172 711 | Samsvarer etter månedsavrunding |

De transkriberte diagramdataene for 2012 og 2014 kan ikke knyttes sikkert til kategoriene uten originalbildene.
Beløpene finnes igjen, med små avrundingsforskjeller, blant observerte MS-satser for de aktuelle årene, men
kolonneplasseringen gikk tapt i tekstuttrekket:

| Oppgitt virkning | Oppgitt kategori | Transkribert årsbeløp |
|------------------|-------------------|----------------------:|
| 01.05.2014 | EN | 216 593 |
| 01.05.2014 | EU | 167 963 |
| 01.05.2014 | EO | 209 954 |
| 01.05.2014 | EV | 155 372 |
| 01.05.2012 | EN | 203 269 |
| 01.05.2012 | EU | 150 425 |
| 01.05.2012 | EO | 157 639 |
| 01.05.2012 | EV | 197 049 |

Disse åtte radene skal ikke brukes som en bekreftet kobling mellom kategori og sats før diagrammene kan leses
med visuell kolonneplassering.

### Hva oppslagsprojeksjonen kan vise

Den persisterte projeksjonen lagrer allerede MS som `sats` og FM som `fradrag` både på månedsbeløpet og den
komprimerte ytelsesperioden. Den kan derfor vise sats, fradrag og utledet månedsbeløp uten å bruke
`T_BEREGN_FAKTOR`.

Bosituasjon fra klassifiseringsnivå 02, det årlige ytelsesbeløpet fra `T_SU`, beregningsfaktorene,
`OPPDRAG_ID` og oversendingsfeltene fra `T_BESLUT` finnes bare i råimporten eller den transiente
konverteringsmodellen. De persisteres ikke i oppslagsprojeksjonen i dag. Skal satsen vises sammen med bosituasjon
eller oversendingsstatus, må de relevante feltene normaliseres og persisteres som del av projeksjonen.

## Begrensninger i datagrunnlaget

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
   kildekoder og ikke navngis som satskategori før betydningen er dokumentert. At `OB` betyr oppfostringsbidrag i
   et kodeverk i Bisys er ikke belegg for at samme betydning gjelder i SU-klassifiseringen.
