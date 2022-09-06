package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.brev.BrevvalgDatabaseJson

data class AvsluttetRevurderingDatabaseJson(
    val begrunnelse: String,
    val brevvalg: BrevvalgDatabaseJson?,
    val tidspunktAvsluttet: Tidspunkt,
)
