package no.nav.su.se.bakover.database.revurdering

import dokument.database.BrevvalgDbJson
import no.nav.su.se.bakover.common.tid.Tidspunkt

data class AvsluttetRevurderingDatabaseJson(
    val begrunnelse: String,
    val brevvalg: BrevvalgDbJson?,
    val tidspunktAvsluttet: Tidspunkt,
    val avsluttetAv: String?,
)
