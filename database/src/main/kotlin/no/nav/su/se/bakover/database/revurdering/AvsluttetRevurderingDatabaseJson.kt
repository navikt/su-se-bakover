package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.BrevvalgDbJson

data class AvsluttetRevurderingDatabaseJson(
    val begrunnelse: String,
    val brevvalg: BrevvalgDbJson?,
    val tidspunktAvsluttet: Tidspunkt,
    val avsluttetAv: String?,
)
