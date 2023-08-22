package no.nav.su.se.bakover.oppgave.infrastructure

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse

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
