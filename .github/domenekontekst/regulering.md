# Regulering

**Status:** `verified` for implementert flyt. Faglige utkast er merket som
`unresolved` i `avklaringer.md`.

Regulering oppdaterer løpende saker ved endring i satser og enkelte eksternt
regulerte ytelser. Koden støtter automatisk og manuell regulering.

## Tilstandsmodell

```text
OpprettetRegulering
  -> BeregnetRegulering
  -> ReguleringUnderBehandling.TilAttestering
  -> IverksattRegulering

ReguleringUnderBehandling
  -> AvsluttetRegulering
```

Regulering er en behandling med eget grunnlag, beregning, simulering,
attesteringshistorikk og reguleringstype.

## Automatisk eller manuell

`opprettReguleringForAutomatiskEllerManuellBehandling` vurderer om saken kan
behandles automatisk.

- Automatisk regulering går gjennom beregning, simulering og iverksettelse maskinelt.
- Når grunnlag eller eksterne data ikke kan behandles trygt automatisk, skilles det
  mellom manuell regulering, sak som må revurderes og behandling som feilet.
- Enkelte grunnbeløpsregulerte fradrag gjennomgår en toleransesjekk før automatisk
  behandling.
- Dry-run/testkjøring utfører ikke normal iverksettelse eller utbetaling, men lagrer
  fortsatt kjøringsmetadata og eksterne perioder for analyse. Konfigurerte
  ikke-produksjonskjøringer kan også bevare manuelle reguleringer.

Regulering sender ikke vedtaksbrev etter dagens domeneregel.

## Eksternt regulerte beløp

Implementasjonen har typed modeller og klienter for blant annet:

- arbeidsavklaringspenger fra AAP-integrasjonen
- pensjonsrelaterte data fra Pesys

Beløp periodiseres og lagres med kilde og eventuelle feilkoder. Data som mangler
tilstrekkelig periode eller ikke kan tolkes, skal føre til manuell oppfølging fremfor
en antatt verdi.

## Regelspesifisering

Implementerte reguleringsberegninger skal bruke identene i
`Regelspesifisering.kt`. Der finnes blant annet:

- `REGEL-BEREGN-SATS-AAP-MÅNED`
- `GRUNNLAG-DAGSATS-AAP`
- `GRUNNLAG-UFØRETRYGD`
- grunnlag for grunnbeløp og satsfaktorer

At en ident finnes i enumen viser at kodebasen kjenner identen, men ikke alene at
hele fagutkastet er ferdig eller tatt i bruk i alle flyter.

## Kilder

- `domain/.../regulering/`
- `service/.../regulering/ReguleringAutomatiskServiceImpl.kt`
- `service/.../regulering/ReguleringManuellServiceImpl.kt`
- `behandling/regulering/`
- `common/domain/.../regelspesifisering/Regelspesifisering.kt`
- `client/.../aap/AapApiInternHttpClient.kt`
- `client/.../pesys/PesysHttpClient.kt`
