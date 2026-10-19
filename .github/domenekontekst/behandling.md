# Behandling og vedtak

**Status:** `verified`

Behandlinger modelleres som eksplisitte tilstandsmaskiner. Den konkrete Kotlin-typen
avgjør hvilke operasjoner som er lovlige.

## Behandlingstyper

- søknadsbehandling
- revurdering
- regulering
- klage
- tilbakekrevingsbehandling
- spesialiserte revurderinger for stans og gjenopptak

Alle er knyttet til en sak. `Sak` samler behandlingene og tidligere vedtak og brukes
til å utlede gjeldende data for en periode.

## Søknadsbehandling

En søknadsbehandling starter med en journalført søknad og oppgave. Den går gjennom
vilkår, grunnlag, eventuell beregning og simulering før attestering.

Ved avslag kan behandlingen sendes til attestering uten beregning. Ved innvilgelse
kreves beregning og simulering.

## Revurdering

En revurdering gjelder tidligere vedtaksdata i en angitt periode. Den inneholder:

- revurderingsårsak
- informasjon om hva som revurderes
- vedtakene som revurderes månedsvis
- grunnlag og vilkår
- beregning og simulering
- brevvalg og attesteringer

Før iverksettelse kontrolleres det at vedtaksmånedene ikke er endret siden
revurderingen ble laget.

## Attestering

Saksbehandler sender behandling til attestering. Attestanten kan:

- iverksette
- underkjenne med grunn

Saksbehandleren som sendte behandlingen til attestering, kan også returnere sin egen
søknadsbehandling eller revurdering til forrige behandlingssteg. Dette er en annen
operasjon enn at attestanten underkjenner.

Ved manuell søknadsbehandling håndheves totrinnskontroll slik at samme person normalt
ikke kan være både saksbehandler og attestant.

## Iverksettelse

Iverksettelse er skillet mellom utkast og juridisk/økonomisk resultat:

1. Valider behandlingens tilstand og samtidighet.
2. Kontroller komplett regelspesifisering ved beregning.
3. Generer dokument og simuler utbetaling når utfallet krever det.
4. Opprett vedtak.
5. Lagre behandling, vedtak og nødvendige sideeffekter atomisk.
6. Send eventuell utbetaling etter strategien for behandlingstypen.

Søknadsbehandling, revurdering og stans publiserer normalt utbetalingen sist i
iverksettelsestransaksjonen. Automatisk regulering er et bevisst unntak: den committer
før publisering og bruker markering og retry dersom publiseringen feiler.

Et iverksatt vedtak endres ikke direkte. Ny informasjon håndteres med en ny
behandling.

## Feilmodellering

Ugyldige operasjoner returnerer normalt typed `KunneIkke...`-feil. Ikke erstatt disse
med generell `RuntimeException` i web-laget. Ved en transaksjonsgrense kan en typed
feil måtte konverteres til exception dersom transaksjonen skal rulles tilbake.

## Kilder

- `domain/.../Sak.kt`
- `domain/.../søknadsbehandling/`
- `domain/.../revurdering/`
- `domain/.../regulering/`
- `domain/.../klage/Klage.kt`
- `tilbakekreving/domain/.../Tilbakekrevingsbehandling.kt`
- `vedtak/domain/`
