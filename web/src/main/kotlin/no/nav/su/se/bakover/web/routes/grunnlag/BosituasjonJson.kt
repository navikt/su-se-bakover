package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson

internal data class BosituasjonJson(
    val type: String,
    val fnr: String?,
    val delerBolig: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
    val begrunnelse: String?,
    val sats: String?,
    val periode: PeriodeJson,
)
