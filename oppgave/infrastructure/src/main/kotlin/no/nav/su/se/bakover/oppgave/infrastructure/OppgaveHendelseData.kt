package no.nav.su.se.bakover.oppgave.infrastructure

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse

internal data class OppgaveHendelseData(
    val oppgaveId: String,
    val relaterteHendelser: List<String>,
    val status: String,
) {
    companion object {
        fun OppgaveHendelse.toJson(): OppgaveHendelseData = OppgaveHendelseData(
            oppgaveId = oppgaveId.toString(),
            relaterteHendelser = relaterteHendelser.map { it.toString() },
            status = when (status) {
                OppgaveHendelse.Oppgavestatus.OPPRETTET -> "OPPRETTET"
                OppgaveHendelse.Oppgavestatus.OPPDATERT -> "OPPDATERT"
                OppgaveHendelse.Oppgavestatus.LUKKET -> "LUKKET"
            },
        )

        fun OppgaveHendelse.toStringifiedJson(): String = serialize(this.toJson())
    }
}

internal fun PersistertHendelse.toOppgaveHendelse(
    meta: DefaultHendelseMetadata,
): OppgaveHendelse {
    val data = deserialize<OppgaveHendelseData>(this.data)
    return OppgaveHendelse.createFromPersistence(
        hendelseId = this.hendelseId,
        sakId = this.sakId!!,
        versjon = this.versjon,
        hendelsestidspunkt = this.hendelsestidspunkt,
        meta = meta,
        oppgaveId = OppgaveId(data.oppgaveId),
        relaterteHendelser = data.relaterteHendelser.map { HendelseId.fromString(it) },
        status = when (data.status) {
            "OPPRETTET" -> OppgaveHendelse.Oppgavestatus.OPPRETTET
            "OPPDATERT" -> OppgaveHendelse.Oppgavestatus.OPPDATERT
            "LUKKET" -> OppgaveHendelse.Oppgavestatus.LUKKET
            else -> throw IllegalStateException("Ukjent oppgavestatus ${data.status}")
        },
        entitetId = this.entitetId,
        tidligereHendelseId = this.tidligereHendelseId,
    )
}
