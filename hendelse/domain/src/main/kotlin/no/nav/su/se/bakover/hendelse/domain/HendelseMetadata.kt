package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker

interface HendelseMetadata {
    val correlationId: CorrelationId?
    val ident: NavIdentBruker?
    val brukerroller: List<Brukerrolle>
}
