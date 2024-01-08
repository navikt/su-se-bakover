package tilbakekreving.presentation.api.common

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import tilbakekreving.domain.vurdering.PeriodevurderingMedKrav

data class VurderingMedKravForPeriodeJson(
    val periode: PeriodeJson,
    val vurdering: String,
    val betaltSkattForYtelsesgruppen: Int,
    val bruttoTidligereUtbetalt: Int,
    val bruttoNyUtbetaling: Int,
    val bruttoSkalTilbakekreve: Int,
    val nettoSkalTilbakekreve: Int,
    val bruttoSkalIkkeTilbakekreve: Int,
    val skatteProsent: String,
)

internal fun PeriodevurderingMedKrav.toJson(): VurderingMedKravForPeriodeJson {
    return VurderingMedKravForPeriodeJson(
        periode = PeriodeJson(periode.fraOgMed.toString(), periode.tilOgMed.toString()),
        vurdering = when (this) {
            is PeriodevurderingMedKrav.SkalIkkeTilbakekreve -> "SkalIkkeTilbakekreve"
            is PeriodevurderingMedKrav.SkalTilbakekreve -> "SkalTilbakekreve"
        },
        betaltSkattForYtelsesgruppen = this.betaltSkattForYtelsesgruppen,
        bruttoTidligereUtbetalt = this.bruttoTidligereUtbetalt,
        bruttoNyUtbetaling = this.bruttoNyUtbetaling,
        bruttoSkalTilbakekreve = this.bruttoSkalTilbakekreve,
        nettoSkalTilbakekreve = this.nettoSkalTilbakekreve,
        bruttoSkalIkkeTilbakekreve = this.bruttoSkalIkkeTilbakekreve,
        skatteProsent = this.skatteProsent.toString(),
    )
}
