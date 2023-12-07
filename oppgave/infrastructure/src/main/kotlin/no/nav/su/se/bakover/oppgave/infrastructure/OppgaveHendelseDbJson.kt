package no.nav.su.se.bakover.oppgave.infrastructure

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype

private data class OppgaveHendelseDbJson(
    val oppgaveId: String,
    val relaterteHendelser: List<String>,
    val status: String,
    val oppgavetype: String?,
    val beskrivelse: String?,
    val ferdigstiltAv: String?,
    val ferdigstiltTidspunkt: Tidspunkt?,
)

fun OppgaveHendelse.toDbJson(): String {
    return OppgaveHendelseDbJson(
        oppgaveId = oppgaveId.toString(),
        relaterteHendelser = relaterteHendelser.map { it.toString() },
        status = finnStatus(),
        oppgavetype = when (this) {
            is OppgaveHendelse.Lukket.Manuelt,
            is OppgaveHendelse.Lukket.Maskinelt,
            -> null

            is OppgaveHendelse.Oppdatert -> this.oppgavetype.toDb()
            is OppgaveHendelse.Opprettet -> this.oppgavetype.toDb()
        },
        beskrivelse = when (this) {
            is OppgaveHendelse.Lukket.Manuelt -> null
            is OppgaveHendelse.Lukket.Maskinelt -> this.beskrivelse
            is OppgaveHendelse.Oppdatert -> this.beskrivelse
            is OppgaveHendelse.Opprettet -> this.beskrivelse
        },
        ferdigstiltTidspunkt = when (this) {
            is OppgaveHendelse.Lukket.Manuelt -> this.ferdigstiltTidspunkt
            is OppgaveHendelse.Lukket.Maskinelt -> this.ferdigstiltTidspunkt
            is OppgaveHendelse.Oppdatert -> null
            is OppgaveHendelse.Opprettet -> null
        },
        ferdigstiltAv = when (this) {
            is OppgaveHendelse.Lukket.Manuelt -> this.ferdigstiltAv.toString()
            is OppgaveHendelse.Lukket.Maskinelt -> null
            is OppgaveHendelse.Oppdatert -> null
            is OppgaveHendelse.Opprettet -> null
        },
    ).let { serialize(it) }
}

private fun Oppgavetype.toDb(): String {
    return when (this) {
        Oppgavetype.BEHANDLE_SAK -> "BEHANDLE_SAK"
        Oppgavetype.ATTESTERING -> "ATTESTERING"
        Oppgavetype.FREMLEGGING -> "FREMLEGGING"
        Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE -> "VURDER_KONSEKVENS_FOR_YTELSE"
    }
}

private fun OppgaveHendelse.finnStatus() = when (this) {
    is OppgaveHendelse.Lukket.Manuelt -> "LUKKET_MANUELT"
    is OppgaveHendelse.Lukket.Maskinelt -> "LUKKET_MASKINELT"
    is OppgaveHendelse.Oppdatert -> "OPPDATERT"
    is OppgaveHendelse.Opprettet -> "OPPRETTET"
}

internal fun PersistertHendelse.toOppgaveHendelse(): OppgaveHendelse {
    val data = deserialize<OppgaveHendelseDbJson>(this.data)

    return when (data.status) {
        "LUKKET_MANUELT" -> OppgaveHendelse.Lukket.Manuelt(
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            versjon = this.versjon,
            hendelsestidspunkt = this.hendelsestidspunkt,
            oppgaveId = OppgaveId(data.oppgaveId),
            relaterteHendelser = data.relaterteHendelser.map { HendelseId.fromString(it) },
            tidligereHendelseId = this.tidligereHendelseId!!,
            ferdigstiltAv = NavIdentBruker.Saksbehandler(data.ferdigstiltAv!!),
        )

        "LUKKET_MASKINELT" -> OppgaveHendelse.Lukket.Maskinelt(
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            versjon = this.versjon,
            hendelsestidspunkt = this.hendelsestidspunkt,
            oppgaveId = OppgaveId(data.oppgaveId),
            relaterteHendelser = data.relaterteHendelser.map { HendelseId.fromString(it) },
            tidligereHendelseId = this.tidligereHendelseId!!,
            beskrivelse = data.beskrivelse!!,
        )

        "OPPDATERT" -> OppgaveHendelse.Oppdatert(
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            versjon = this.versjon,
            hendelsestidspunkt = this.hendelsestidspunkt,
            oppgaveId = OppgaveId(data.oppgaveId),
            relaterteHendelser = data.relaterteHendelser.map { HendelseId.fromString(it) },
            tidligereHendelseId = this.tidligereHendelseId!!,
            beskrivelse = data.beskrivelse!!,
            oppgavetype = finnOppgaveType(data.oppgavetype),
        )

        "OPPRETTET" -> OppgaveHendelse.Opprettet(
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            versjon = this.versjon,
            hendelsestidspunkt = this.hendelsestidspunkt,
            oppgaveId = OppgaveId(data.oppgaveId),
            relaterteHendelser = data.relaterteHendelser.map { HendelseId.fromString(it) },
            beskrivelse = data.beskrivelse!!,
            oppgavetype = finnOppgaveType(data.oppgavetype),

        )

        else -> throw IllegalStateException("ukjent status for oppgave hendelse - fikk ${data.status}")
    }
}

private fun finnOppgaveType(oppgavetype: String?) =
    when (oppgavetype) {
        "BEHANDLE_SAK" -> Oppgavetype.BEHANDLE_SAK
        "ATTESTERING" -> Oppgavetype.ATTESTERING
        "FREMLEGGING" -> Oppgavetype.FREMLEGGING
        "VURDER_KONSEKVENS_FOR_YTELSE" -> Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        else -> throw IllegalStateException("Ukjent oppgavetype - fikk $oppgavetype")
    }
