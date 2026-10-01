# Utbetaling

**Status:** `verified`

Økonomiområdet omsetter et innvilget eller endret vedtak til simulerte og
oversendte utbetalingslinjer.

## Tilstandsmodell

```text
UtbetalingForSimulering
  -> SimulertUtbetaling
  -> OversendtUtbetaling.UtenKvittering
  -> OversendtUtbetaling.MedKvittering
```

`Utbetaling` inneholder sak, mottaker, behandler, avstemmingsnøkkel, sakstype og én
eller flere utbetalingslinjer. Domenet validerer sortering, rekkefølge og referanser
mellom linjene.

## Simulering og oversending

1. Behandlingen lager `UtbetalingForSimulering`.
2. `SimuleringClient` returnerer den økonomiske simuleringen.
3. `UtbetalingPublisher` genererer requesten til Oppdrag.
4. Oversendt utbetaling lagres i en transaksjonscontext.
5. Requesten publiseres til Oppdrag etter strategien for behandlingstypen.
6. Kvitteringen lagres på den oversendte utbetalingen når den mottas.

For søknadsbehandling, revurdering og stans skjer publisering sist i
iverksettelsestransaksjonen. Flytt ikke vilkårlige sideeffekter etter publiseringen.

Automatisk regulering bruker en annen strategi fordi det ikke finnes en manuell
iverksettelse som enkelt kan gjentas: databaseendringene committes før publisering.
Hvis MQ-sendingen feiler, markeres reguleringen som ikke sendt og en retry-jobb kan
sende den lagrede utbetalingen på nytt. Begge strategiene er bevisste
konsistensmønstre; bruk mønsteret til den aktuelle flyten.

## Før en ny utbetaling

De fleste utbetalingsoperasjoner krever at alle tidligere utbetalinger har mottatt
OK-kvittering. Ved korrigering av en utbetaling som feilet, kan den aktuelle
strategien eksplisitt tillate en mottatt feilkvittering; ukvitterte utbetalinger er
fortsatt ikke tilstrekkelig.

En lagret reguleringsutbetaling som ikke ble publisert, kan sendes på nytt gjennom
retry-flyten. Dette er et bevisst unntak fra normal førstegangsoversending.

## Kvittering

Samme kvittering kan mottas flere ganger. Dersom en ny kvittering har annet innhold
enn den som allerede er lagret, logges avviket og den lagrede kvitteringen oppdateres.

`OK_MED_VARSEL` og `FEIL` behandles som feil eller varsel i domenet. Oversendt
utbetaling dokumenterer levering til betalingskjeden, ikke alene at mottakeren har
fått pengene.

## Stans og gjenopptak

- Stans skal ha nøyaktig én stanslinje og gjelder en midlertidig operasjon i nåtid.
- Reaktivering er bare gyldig når siste oversendte utbetaling er en stans.
- Stans og reaktivering har et én-til-én-forhold.

## Dødsfall

- Bruker har rett til stønad ut måneden hen dør. Ytelse etter utgangen av
  dødsmåneden er feil og skal følges opp av saksbehandler med opphør.
- Kontroll mot dødsfall skal derfor bruke dødsmåneden, ikke dagens dato.
  `Sak.harYtelseEtterDødsmåned` bruker vedtakstidslinjen, slik at et opphør fra
  måneden etter dødsfallet ikke gir falsk alarm. Stans gir fortsatt treff, fordi
  saken ikke er avsluttet.
- Automatisk opphør ved dødsfall er ikke avklart juridisk eller faglig. Inntil
  videre skal alle automatiske prosesser som oppdager at bruker er død, opprette
  oppgaven `OppgaveConfig.BrukerErDød` (høy prioritet) til saksbehandler, som
  vurderer opphør. Systemet skal ikke opphøre automatisk.
- Disse prosessene oppretter oppgaven i dag:
  - innkalling til kontrollsamtale: når kontrollsamtalen er annullert
  - utløpt frist for kontrollsamtale: når kontrollsamtalen er annullert, også om
    stans feiler
  - påminnelse om ny stønadsperiode: bare ved ytelse etter dødsmåneden, og bare
    i måneden før ytelsen utløper (måneden påminnelsen ellers ville blitt sendt).
    Jobben vurderer alle saker hver måned, så dette gir maks én oppgave per
    stønadsperiode.
- I jobbene for kontrollsamtale opprettes oppgaven til slutt. Feiler
  annulleringen, plukkes kontrollsamtalen opp igjen neste kjøring, slik at
  jobben ikke lager én oppgave per kjøring. Feiler oppretting av oppgaven, logges
  bare en error.
- `Person.erDød()` bygger på `dødsdato`. Et dødsfall i PDL uten dødsdato gir i dag
  `erDød() == false`; se TODO i `Person.kt`.

## Kilder

- `økonomi/domain/.../utbetaling/Utbetaling.kt`
- `økonomi/domain/.../utbetaling/Utbetalingslinje.kt`
- `økonomi/application/.../utbetaling/UtbetalingServiceImpl.kt`
- `domain/.../sak/SakUtbetaling.kt`
- `domain/.../søknadsbehandling/iverksett/`
- `domain/.../revurdering/iverksett/`
