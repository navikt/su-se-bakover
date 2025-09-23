package no.nav.su.se.bakover.domain.revurdering.opprett

import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent

data class OpprettRevurderingResultatUtenOppgaveId(
    val fnr: Fnr,
    val oppgaveConfig: () -> OppgaveConfig,
    private val sak: (r: OpprettetRevurdering) -> Sak,
    private val opprettRevurdering: (o: OppgaveId) -> OpprettetRevurdering,
    private val statistikkHendelse: (r: OpprettetRevurdering) -> StatistikkEvent,
    val klageId: KlageId? = null,
) {
    fun leggTilOppgaveId(oppgaveId: OppgaveId): OpprettRevurderingResultat {
        val r = opprettRevurdering(oppgaveId)
        return OpprettRevurderingResultat(
            sak = sak(r),
            opprettetRevurdering = r,
            statistikkHendelse = statistikkHendelse(r),
            klageId = klageId,
        )
    }
}

data class OpprettRevurderingResultat(
    val sak: Sak,
    val opprettetRevurdering: OpprettetRevurdering,
    val statistikkHendelse: StatistikkEvent,
    val klageId: KlageId? = null,
)
