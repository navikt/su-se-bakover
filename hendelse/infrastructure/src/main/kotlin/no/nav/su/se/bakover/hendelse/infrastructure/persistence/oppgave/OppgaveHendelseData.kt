package no.nav.su.se.bakover.hendelse.infrastructure.persistence.oppgave

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.oppgave.OppgaveHendelse

data class OppgaveHendelseData(
    val oppgaveId: String,
) {
    companion object {
        fun OppgaveHendelse.toJson(): OppgaveHendelseData = OppgaveHendelseData(
            oppgaveId = oppgaveId.toString(),
        )

        fun OppgaveHendelse.toStringifiedJson(): String = serialize(this.toJson())
    }
}
