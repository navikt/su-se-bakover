package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.Tidspunkt

data class AvsluttetRevurderingInfo(
    val begrunnelse: String,
    val fritekst: String?,
    val tidspunktAvsluttet: Tidspunkt,
)
