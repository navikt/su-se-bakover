package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata

data class OppgaveHendelseMetadata(
    override val correlationId: CorrelationId?,
    override val ident: NavIdentBruker?,
    override val brukerroller: List<Brukerrolle>,
    val requestBody: String?,
    val response: String?,
) : HendelseMetadata
