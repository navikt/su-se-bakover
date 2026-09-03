# Beregning

**Status:** `verified`

Beregning fastsetter supplerende stønad månedsvis fra periodiserte grunnlag,
vilkår, sats og fradrag.

## Inngang

`BeregningStrategyFactory.beregn` mottar:

- `GrunnlagsdataOgVilkårsvurderinger`
- valgfri begrunnelse
- `Sakstype.ALDER` eller `Sakstype.UFØRE`

Bosituasjon er påkrevd og brukes til å velge beregningsstrategi og sats for
delperioder. Delperiodene skal dekke hele beregningsperioden.

## Alder og uføre

For alder brukes fradragsgrunnlaget fra saksbehandler direkte som inngang til
beregningen.

For uføre kombinerer `Beregningsgrunnlag` uføregrunnlaget med fradragene. Dette
inkluderer regler som er særegne for uføre, blant annet forventet inntekt.

Ikke generaliser beregningslogikk mellom sakstypene uten å kontrollere begge
strategiene og testene.

## Resultat

`Beregning` inneholder:

- månedsberegninger
- regelspesifiserte månedsberegninger
- fradrag
- samlet ytelse og samlet fradrag
- valgfri begrunnelse

Perioder med samme økonomiske innhold kan slås sammen til beregningsperioder for
presentasjon og persistens.

## Regelspesifisering

Automatiske beregninger skal lagre hvilke regler og grunnlag som ble brukt:

- `Regelspesifiseringer` inneholder versjonerte regelidenter.
- `RegelspesifisertGrunnlag` inneholder versjonerte grunnlagsidenter.
- `RegelspesifisertBeregning.benyttetRegel` er roten i regeltreet.
- Avhengige beregninger og grunnlag lagres under roten.

Iverksettelse av søknadsbehandling og revurdering blokkeres dersom en
månedsberegning bruker `BeregnetUtenSpesifisering`.

Nye regler skal registreres i enumene, knyttes til faglig regelspesifisering og
dekkes av testen for komplett regeltre som er angitt i
`Regelspesifisering.kt`.

## Hard regel og faglig kilde

Kode viser hva som er implementert, men avgjør ikke alene om en ny juridisk regel er
faglig godkjent. Confluence-utkast med status «under arbeid» skal ikke behandles som
gjeldende krav uten avklaring.

## Kilder

- `beregning/src/main/kotlin/beregning/domain/Beregning.kt`
- `beregning/src/main/kotlin/beregning/domain/BeregningStrategyFactory.kt`
- `beregning/src/main/kotlin/beregning/domain/BeregningFactory.kt`
- `common/domain/.../regelspesifisering/Regelspesifisering.kt`
- `behandling/regulering/application/src/test/java/beregning/domain/BeregningRegelspesifiseringTest.kt`
