package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

/**
 * @param oppgaveId - Kan ha samme oppgave id som en tidligere oppgave-hendelse (hvis ja, må ha tidligere hendelsesId)
 */
data class OppgaveHendelse(
    override val hendelseId: HendelseId,
    override val tidligereHendelseId: HendelseId?,
    override val sakId: UUID,
    override val versjon: Hendelsesversjon,
    override val hendelsestidspunkt: Tidspunkt,
    override val triggetAv: HendelseId?,
    val oppgaveId: OppgaveId,
) : Hendelse {
    override val meta: HendelseMetadata = HendelseMetadata.tom()
    override val entitetId: UUID = sakId

    override fun compareTo(other: Hendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId) { "EntitetIdene eller sakIdene var ikke lik" }
        return this.versjon.compareTo(other.versjon)
    }
}
