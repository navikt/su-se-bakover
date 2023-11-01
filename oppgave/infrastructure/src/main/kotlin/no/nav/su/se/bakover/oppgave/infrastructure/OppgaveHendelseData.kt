package no.nav.su.se.bakover.oppgave.infrastructure

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype

internal data class OppgaveHendelseData(
    val oppgaveId: String,
    val relaterteHendelser: List<String>,
    val status: String,
    val oppgavetype: String,
    val beskrivelse: String,
    val requestBody: String?,
    val response: String?,
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
            oppgavetype = when (this.oppgavetype) {
                Oppgavetype.BEHANDLE_SAK -> "BEHANDLE_SAK"
                Oppgavetype.ATTESTERING -> "ATTESTERING"
                Oppgavetype.FREMLEGGING -> "FREMLEGGING"
                Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE -> "VURDER_KONSEKVENS_FOR_YTELSE"
            },
            beskrivelse = this.beskrivelse,
            requestBody = this.meta.requestBody,
            response = this.meta.response,
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
        meta = OppgaveHendelseMetadata(
            correlationId = meta.correlationId,
            ident = meta.ident,
            brukerroller = meta.brukerroller,
            requestBody = data.requestBody,
            response = data.response,
        ),
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
        beskrivelse = data.beskrivelse,
        oppgavetype = when (data.oppgavetype) {
            "BEHANDLE_SAK" -> Oppgavetype.BEHANDLE_SAK
            "ATTESTERING" -> Oppgavetype.ATTESTERING
            "FREMLEGGING" -> Oppgavetype.FREMLEGGING
            "VURDER_KONSEKVENS_FOR_YTELSE" -> Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
            else -> throw IllegalStateException("Ukjent oppgavetype - fikk ${data.oppgavetype}")
        },
    )
}
