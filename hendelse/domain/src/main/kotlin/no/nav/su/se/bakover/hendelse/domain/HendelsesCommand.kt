package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker

interface HendelsesCommand {
    val correlationId: CorrelationId?
    val ident: NavIdentBruker?
    val brukerroller: List<Brukerrolle>

    fun toDefaultHendelsesMetadata(): DefaultHendelseMetadata = DefaultHendelseMetadata(
        correlationId = correlationId,
        ident = ident,
        brukerroller = brukerroller,
    )
}
