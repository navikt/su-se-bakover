# Saksgangen i SU-app

Denne siden er et kart over den overordnede saksgangen i `su-se-bakover`. Den beskriver
dagens modell slik den fremgår av kode og tester. Detaljer om hvert område hører hjemme
i egne temafiler.

## Grunnbegreper

**Sak** samler søknader, behandlinger, vedtak, utbetalinger og andre hendelser for én
person og én sakstype. `Sakstype` er enten `ALDER` eller `UFØRE`. `Sak` er aggregatet
som brukes når gjeldende vedtaksdata og utbetalingstidslinje skal utledes.

**Søknad** er brukerens krav om supplerende stønad. En journalført søknad med oppgave
kan danne grunnlag for en søknadsbehandling. Søknadsinnholdet avgjør om saken gjelder
alder eller uføre.

**Behandling** er en tilstandsmaskin, ikke bare en databasepost. Gyldige operasjoner
avhenger av den konkrete domenetypen behandlingen har. Viktige behandlingstyper er:

- søknadsbehandling
- revurdering, inkludert opphør, stans og gjenopptak
- regulering
- klage
- tilbakekrevingsbehandling

**Grunnlagsdata** er opplysningene beregning og vilkårsvurdering bygger på, blant annet
bosituasjon og fradrag. Grunnlag er periodisert og må være konsistent med behandlingens
periode.

**Vilkårsvurdering** avgjør om lovens vilkår er oppfylt for den aktuelle perioden.
Hvilke vilkår som gjelder, avhenger blant annet av sakstypen.

**Beregning** fastsetter ytelsen per måned fra sats, grunnlag og fradrag. Automatiske
beregninger som iverksettes skal ha et komplett regeltre med benyttede regler og
grunnlag. Iverksettelse av søknadsbehandling og revurdering avvises dersom en
månedsberegning mangler regelspesifisering.

**Simulering** viser den økonomiske virkningen før et innvilget vedtak iverksettes.
En simulert utbetaling kan deretter klargjøres og sendes til Oppdrag.

**Attestering** er totrinnskontrollen. Saksbehandler sender behandlingen til
attestering. Attestanten kan iverksette eller underkjenne den. Ved manuell
iverksettelse av søknadsbehandling kan saksbehandler og attestant ikke være samme
person, med eksplisitte unntak i domenet.

**Vedtak** opprettes når en behandling iverksettes. Senere endringer gjøres gjennom en
ny behandling; et eksisterende iverksatt vedtak muteres ikke.

## Hovedflyt

```text
Søknad
  -> journalføring og oppgave
  -> søknadsbehandling
  -> vilkår og grunnlag
  -> beregning ved innvilgelse
  -> simulering ved utbetaling
  -> til attestering
     -> underkjent -> tilbake til saksbehandling
     -> iverksatt
        -> vedtak
        -> utbetaling ved innvilgelse
        -> dokument og videre brevflyt når brev skal sendes

Senere endring
  -> revurdering, regulering, stans eller gjenopptak
  -> ny vurdering/beregning/simulering
  -> attestering
  -> nytt vedtak og eventuell endring i utbetaling
```

De konkrete domenetypene er den autoritative statusmodellen. Ikke innfør en separat,
forenklet statusflyt uten å kontrollere overgangene i domenekoden.

## Flyt 1 – Ny søknad og søknadsbehandling

**Startpunkt:** En journalført søknad med oppgave finnes på saken.

1. Det opprettes en søknadsbehandling for sakstypen `ALDER` eller `UFØRE`.
2. Saksbehandler fastsetter stønadsperiode og legger inn eller oppdaterer grunnlag og
   vilkårsvurderinger.
3. Når vilkårene gir avslag, kan behandlingen sendes til attestering uten beregning.
4. Når vilkårene gir rett til stønad, beregnes ytelsen. Utbetalingen simuleres før
   behandlingen sendes til attestering.
5. Attestanten gjør én av to ting:
   - underkjenner behandlingen, som går tilbake til videre saksbehandling
   - iverksetter behandlingen som innvilgelse eller avslag
6. Ved iverksettelse opprettes og lagres vedtaket.
7. Ved innvilgelse klargjøres og sendes utbetalingen. Ved avslag sendes ingen melding
   til Oppdrag.
8. Dokument lagres når vedtaksflyten har generert et brev. Andre sideeffekter, som
   statistikk, oppgaver og kontrollsamtale, håndteres rundt iverksettelsen.

For søknadsbehandling er de viktigste domenetilstandene:

```text
VilkårsvurdertSøknadsbehandling
  -> BeregnetSøknadsbehandling
  -> SimulertSøknadsbehandling
  -> SøknadsbehandlingTilAttestering
     -> UnderkjentSøknadsbehandling
     -> IverksattSøknadsbehandling

Søknadsbehandling
  -> LukketSøknadsbehandling
```

Dette er et oversiktsbilde, ikke en påstand om at alle varianter må gjennom hvert
steg. Avslag kan for eksempel gå til attestering uten beregning og simulering.

## Flyt 2 – Revurdering

**Startpunkt:** Et tidligere vedtak skal vurderes på nytt for en angitt periode.

1. Revurderingen opprettes med årsak, perioden som revurderes og informasjon om hvilke
   deler av saken som skal vurderes på nytt.
2. Gjeldende vedtaksdata kopieres eller utledes fra tidligere vedtak for den aktuelle
   perioden.
3. Saksbehandler oppdaterer relevante grunnlag og vilkår.
4. Revurderingen beregnes og utbetalingen simuleres.
5. Revurderingen sendes til attestering som innvilget eller opphørt.
6. Attestanten underkjenner eller iverksetter.
7. Før iverksettelse kontrollerer domenet at vedtaksmånedene som revurderes, ikke er
   endret av en annen behandling.
8. Ved iverksettelse opprettes et nytt revurderingsvedtak og eventuell
   utbetalingsendring sendes til Oppdrag.

Viktige domenetilstander:

```text
OpprettetRevurdering
  -> BeregnetRevurdering
  -> SimulertRevurdering
  -> RevurderingTilAttestering
     -> UnderkjentRevurdering
     -> IverksattRevurdering

Revurdering
  -> AvsluttetRevurdering
```

Revurdering kan gi fortsatt innvilgelse, endret ytelse eller opphør. Brevvalget og
grunnlaget for opphør er en del av behandlingen og må ikke utledes kun fra beløpet.

## Flyt 3 – Stans og gjenopptak av ytelse

Stans og gjenopptak er egne revurderingsvarianter med strengere økonomiske
forutsetninger enn en vanlig revurdering.

- Stans er en midlertidig operasjon i nåtid. Utbetalingen skal bestå av nøyaktig én
  stanslinje som endrer den siste løpende utbetalingslinjen.
- Gjenopptak kan bare skje når siste oversendte utbetaling er en stans. En stans og
  tilhørende reaktivering har et én-til-én-forhold.
- Begge flytene kontrollerer gjeldende vedtaks- og utbetalingsdata før iverksettelse.

Ikke modeller stans som opphør. Opphør avslutter retten for perioden, mens stans er en
midlertidig endring i utbetalingen.

## Flyt 4 – Regulering

Regulering oppdaterer løpende saker når regelstyrte satser eller eksternt regulerte
beløp endres. Koden støtter både automatisk og manuell behandling.

```text
OpprettetRegulering
  -> BeregnetRegulering
  -> ReguleringUnderBehandling.TilAttestering
     -> IverksattRegulering

ReguleringUnderBehandling
  -> AvsluttetRegulering
```

En regulering:

1. opprettes fra gjeldende sak og vedtaksdata
2. får oppdaterte eksternt regulerte beløp og eventuelt oppdaterte grunnlag
3. beregnes og simuleres
4. behandles automatisk eller sendes til manuell oppfølging, avhengig av utfallet
5. iverksettes som en egen behandling

Regulering sender ikke vedtaksbrev etter dagens domeneregel
`ReguleringUnderBehandling.skalSendeVedtaksbrev()`.

Detaljene om grunnbeløp, garantipensjon, AAP og inntekt etter uførhet skal beskrives i
`regulering.md`. Ikke anta at et utkast til regelspesifisering er implementert før
kode og tester bekrefter det.

## Flyt 5 – Utbetaling

Utbetaling har sin egen eksplisitte tilstandsmodell:

```text
UtbetalingForSimulering
  -> SimulertUtbetaling
  -> OversendtUtbetaling.UtenKvittering
  -> OversendtUtbetaling.MedKvittering
```

Ved innvilgelse skjer følgende som del av iverksettelsen:

1. Domenet lager en utbetaling for simulering.
2. Simuleringsintegrasjonen returnerer en simulert utbetaling.
3. Utbetalingen klargjøres med en request til Oppdrag.
4. Behandling, vedtak, dokument og utbetaling lagres i en transaksjon.
5. Publisering til Oppdrag skjer sist i transaksjonsblokken.
6. Kvittering fra Oppdrag knyttes senere til den oversendte utbetalingen.

At en utbetaling er oversendt, betyr ikke alene at pengene er utbetalt til mottaker.
Kvitteringsstatus og den videre betalingskjeden må tas med i vurderingen.

## Flyt 6 – Brev, journalføring og distribusjon

Dokumentflyten skiller mellom generering, lagring, journalføring og distribusjon.

```text
IkkeJournalførtEllerDistribuert
  -> Journalført
  -> JournalførtOgDistribuertBrev
```

- Et brev må journalføres før distribusjon kan bestilles.
- Journalføring og distribusjon er idempotensbeskyttet av domenetilstanden.
- Mislykket distribusjon registrerer forsøk og bruker backoff før nytt forsøk.
- Ikke alle behandlinger skal sende brev. Brevvalg og behandlingstype avgjør dette.

## Flyt 7 – Klage

**Startpunkt:** En klage registreres med journalpost, oppgave og mottaksdato på saken.

1. Saksbehandler vurderer klagens formkrav.
2. Bekreftet klage går videre til vurdering eller avvisning.
3. En klage som skal oversendes, sendes til attestering før oversending.
4. En avvist klage sendes til attestering før avvisningen iverksettes.
5. Attestanten kan underkjenne.
6. En klage som behandles i vedtaksinstansen kan ferdigstilles som omgjort.
7. En oversendt klage er en avsluttende domenetilstand. Senere hendelser fra
   klageinstansen håndteres som egne klageinstanshendelser.

`Klage.kt` dokumenterer alle tillatte tilstandsoverganger. Den listen er autoritativ
ved endringer i klageflyten.

## Flyt 8 – Tilbakekreving

En tilbakekrevingsbehandling er knyttet til saken og et kravgrunnlag.

1. Det kan bare finnes én åpen tilbakekrevingsbehandling om gangen.
2. Saksbehandler tar stilling til hvilke måneder og krav som skal tilbakekreves.
3. Bruker forhåndsvarsles når flyten krever det.
4. Vurdering og vedtaksbrev ferdigstilles før behandlingen sendes til attestering.
5. Attestanten iverksetter eller underkjenner.
6. Endringer i kravgrunnlaget kan gjøre eksisterende vurderinger utdaterte og må
   håndteres eksplisitt.

Tilbakekreving er hendelsesbasert i den nye modulen. Ikke omgå
`TilbakekrevingsbehandlingHendelser` ved å oppdatere en løs status direkte.

## Tverrgående regler

### Domenetilstanden styrer

- Bruk konkrete sealed typer og domenemetoder for tilstandsoverganger.
- Ikke sett status direkte i web- eller persistenslaget.
- En ugyldig overgang skal returnere en typed domenefeil eller avvises eksplisitt.

### Iverksettelse er en konsistensgrense

- Gjør domenekontroller før sideeffekter.
- Lagre sammenhengende behandling, vedtak, dokument og utbetaling atomisk der flyten
  krever det.
- En `Left` eller `null` ruller ikke automatisk tilbake en databasetransaksjon. Slike
  feil må håndteres eksplisitt.
- Hold ikke en database-session åpen mens ukjent callback-, service- eller repo-kode
  kjøres. Nestede sessions avvises av `SessionValidator`.

### Tilgang må kontrolleres i hele flyten

- Route-autorisasjon bruker rollene saksbehandler, attestant, veileder og drift.
- Operasjoner på person og sak krever i tillegg tilgangssjekk.
- Ny modulær kode skal bruke `TilgangstyringService` der modulen er migrert. Eldre
  kode kan fortsatt gå gjennom `AccessCheckProxy`.

### Historikk og samtidighet

- Tidligere vedtak og behandlinger brukes til å utlede gjeldende tidslinje.
- En ny behandling skal ikke skrive over historiske behandlinger.
- Ved iverksettelse må data som ble lagt til grunn, fortsatt være gyldige. Følg
  eksisterende samtidighetskontroller i søknadsbehandling, revurdering og økonomi.

### Sporbar beregning

- Nye automatiske beregninger skal bruke `RegelspesifisertBeregning`.
- Benyttede regler og grunnlag skal komme fra de versjonerte enumene i
  `Regelspesifisering.kt`.
- Et nytt eller endret regeltre skal dekkes av regeltretesten som er angitt i
  `Regelspesifisering.kt`.

## Autoritative innganger i kodebasen

| Område | Startpunkt |
|---|---|
| Sak og tidslinjer | `domain/.../Sak.kt` |
| Sakstyper | `common/domain/.../sak/Sakstype.kt` |
| Søknad | `domain/.../søknad/Søknad.kt` |
| Søknadsbehandling | `domain/.../søknadsbehandling/Søknadsbehandling.kt` |
| Iverksettelse av søknad | `domain/.../søknadsbehandling/iverksett/IverksettSøknadsbehandling.kt` |
| Revurdering | `domain/.../revurdering/Revurdering.kt` |
| Iverksettelse av revurdering | `domain/.../revurdering/iverksett/IverksettRevurdering.kt` |
| Regulering | `domain/.../regulering/Regulering.kt` |
| Utbetaling | `økonomi/domain/.../utbetaling/Utbetaling.kt` |
| Brevdistribusjon | `dokument/domain/.../JournalføringOgBrevdistribusjon.kt` |
| Klage | `domain/.../klage/Klage.kt` |
| Tilbakekreving | `tilbakekreving/domain/.../Tilbakekrevingsbehandling.kt` |
| Regelspesifisering | `common/domain/.../regelspesifisering/Regelspesifisering.kt` |
| Tilgang til sak | `tilgangstyring/application/.../TilgangstyringService.kt` |

Når dokumentasjonen og koden er uenige, er nyere kode og tester kilde for faktisk
systemoppførsel. Oppdater denne siden når en hovedflyt eller en sentral domenetilstand
endres.
