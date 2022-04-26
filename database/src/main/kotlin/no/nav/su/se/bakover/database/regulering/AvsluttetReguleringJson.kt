package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.common.Tidspunkt

data class AvsluttetReguleringJson(
    val begrunnelse: String?,
    val tidspunkt: Tidspunkt
)
