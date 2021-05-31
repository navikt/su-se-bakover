package no.nav.su.se.bakover.web.routes.grunnlag

internal data class BosituasjonJson(
    val type: String,
    val fnr: String?,
    val delerBolig: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
    val begrunnelse: String?
)
