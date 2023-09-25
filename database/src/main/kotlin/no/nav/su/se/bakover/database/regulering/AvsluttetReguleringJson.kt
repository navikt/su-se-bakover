package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.common.tid.Tidspunkt

data class AvsluttetReguleringJson(
    val tidspunkt: Tidspunkt,
    val avsluttetAv: String?,
)
