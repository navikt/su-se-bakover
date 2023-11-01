package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse.Oppgavestatus.LUKKET
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse.Oppgavestatus.OPPDATERT
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse.Oppgavestatus.OPPRETTET
import java.util.UUID

/**
 * TODO jah: Lag en Oppgavehendelser
 * @param relaterteHendelser Den eller de hendelsene som førte til opprettelsen av oppgaven. Kan f.eks. være en institusjonsoppdholdshendelse.
 * @param oppgavetype Kan være null
 */
data class OppgaveHendelse private constructor(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val versjon: Hendelsesversjon,
    override val hendelsestidspunkt: Tidspunkt,
    override val meta: OppgaveHendelseMetadata,
    val oppgaveId: OppgaveId,
    val relaterteHendelser: List<HendelseId>,
    val status: Oppgavestatus,
    val beskrivelse: String,
    val oppgavetype: Oppgavetype,
    override val tidligereHendelseId: HendelseId?,
) : Sakshendelse {

    init {
        when (status) {
            OPPRETTET -> require(tidligereHendelseId == null) { "Oppgavehendelse kan ikke ha tidligereHendelseId når status er OPPRETTET" }
            OPPDATERT -> require(tidligereHendelseId != null) { "Oppgavehendelse må ha tidligereHendelseId når status er OPPDATERT" }
            LUKKET -> require(tidligereHendelseId != null) { "Oppgavehendelse må ha tidligereHendelseId når status er LUKKET" }
        }
    }

    override val entitetId: UUID = sakId

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId) { "EntitetIdene eller sakIdene var ikke lik" }
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun opprettet(
            hendelseId: HendelseId = HendelseId.generer(),
            hendelsestidspunkt: Tidspunkt,
            oppgaveId: OppgaveId,
            versjon: Hendelsesversjon,
            sakId: UUID,
            relaterteHendelser: List<HendelseId>,
            meta: OppgaveHendelseMetadata,
            beskrivelse: String,
            oppgavetype: Oppgavetype,
        ): OppgaveHendelse {
            return OppgaveHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                versjon = versjon,
                hendelsestidspunkt = hendelsestidspunkt,
                relaterteHendelser = relaterteHendelser,
                oppgaveId = oppgaveId,
                meta = meta,
                status = OPPRETTET,
                tidligereHendelseId = null,
                oppgavetype = oppgavetype,
                beskrivelse = beskrivelse,
            )
        }

        fun oppdatert(
            hendelseId: HendelseId = HendelseId.generer(),
            hendelsestidspunkt: Tidspunkt,
            oppgaveId: OppgaveId,
            versjon: Hendelsesversjon,
            sakId: UUID,
            relaterteHendelser: List<HendelseId>,
            meta: OppgaveHendelseMetadata,
            tidligereHendelseId: HendelseId,
            beskrivelse: String,
            oppgavetype: Oppgavetype,
        ): OppgaveHendelse {
            return OppgaveHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                versjon = versjon,
                hendelsestidspunkt = hendelsestidspunkt,
                relaterteHendelser = relaterteHendelser,
                oppgaveId = oppgaveId,
                meta = meta,
                status = OPPDATERT,
                tidligereHendelseId = tidligereHendelseId,
                beskrivelse = beskrivelse,
                oppgavetype = oppgavetype,
            )
        }

        fun lukket(
            hendelseId: HendelseId = HendelseId.generer(),
            hendelsestidspunkt: Tidspunkt,
            oppgaveId: OppgaveId,
            versjon: Hendelsesversjon,
            sakId: UUID,
            relaterteHendelser: List<HendelseId>,
            meta: OppgaveHendelseMetadata,
            tidligereHendelseId: HendelseId,
            beskrivelse: String,
            oppgavetype: Oppgavetype,
        ): OppgaveHendelse {
            return OppgaveHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                versjon = versjon,
                hendelsestidspunkt = hendelsestidspunkt,
                relaterteHendelser = relaterteHendelser,
                oppgaveId = oppgaveId,
                meta = meta,
                status = LUKKET,
                tidligereHendelseId = tidligereHendelseId,
                beskrivelse = beskrivelse,
                oppgavetype = oppgavetype,
            )
        }

        fun createFromPersistence(
            hendelseId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            oppgaveId: OppgaveId,
            versjon: Hendelsesversjon,
            sakId: UUID,
            relaterteHendelser: List<HendelseId>,
            entitetId: UUID,
            tidligereHendelseId: HendelseId?,
            status: Oppgavestatus,
            meta: OppgaveHendelseMetadata,
            beskrivelse: String,
            oppgavetype: Oppgavetype,
        ): OppgaveHendelse {
            require(entitetId == sakId) { "Forventer at sakId $sakId og entitetId $entitetId er like." }
            return OppgaveHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                versjon = versjon,
                hendelsestidspunkt = hendelsestidspunkt,
                relaterteHendelser = relaterteHendelser,
                oppgaveId = oppgaveId,
                meta = meta,
                status = status,
                tidligereHendelseId = tidligereHendelseId,
                beskrivelse = beskrivelse,
                oppgavetype = oppgavetype,
            )
        }
    }

    enum class Oppgavestatus {
        OPPRETTET,
        OPPDATERT,
        LUKKET,
    }
}
