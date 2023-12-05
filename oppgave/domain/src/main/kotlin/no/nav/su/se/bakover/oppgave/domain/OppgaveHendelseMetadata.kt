package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata

/**
 * @param request - Dette er requesten som [OppgaveHttpKallResponse] gir, når man kaller [OppgaveHttpClient]
 * @param response - Dette er responsen som [OppgaveHttpKallResponse] gir, når man kaller [OppgaveHttpClient]
 */
data class OppgaveHendelseMetadata(
    override val correlationId: CorrelationId?,
    override val ident: NavIdentBruker?,
    override val brukerroller: List<Brukerrolle>,
    val request: String?,
    val response: String,
) : HendelseMetadata
