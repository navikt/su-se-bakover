package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * TODO jah: Lag en Oppgavehendelser
 * @param relaterteHendelser Den eller de hendelsene som førte til opprettelsen av oppgaven. Kan f.eks. være en institusjonsoppdholdshendelse.
 *
 */
sealed interface OppgaveHendelse : Sakshendelse {
    override val hendelseId: HendelseId
    override val sakId: UUID
    override val versjon: Hendelsesversjon
    override val hendelsestidspunkt: Tidspunkt
    override val meta: OppgaveHendelseMetadata
    override val tidligereHendelseId: HendelseId?
    override val entitetId: UUID get() = sakId
    val relaterteHendelser: List<HendelseId>
    val oppgaveId: OppgaveId

    /**
     * @param beskrivelse - beskrivelsen som blir returnert fra [OppgaveHttpKallResponse]
     * * @param oppgavetype - oppgavetype som blir returnert fra [OppgaveHttpKallResponse]
     */
    data class Opprettet(
        override val hendelseId: HendelseId = HendelseId.generer(),
        override val hendelsestidspunkt: Tidspunkt,
        override val oppgaveId: OppgaveId,
        override val versjon: Hendelsesversjon,
        override val sakId: UUID,
        override val relaterteHendelser: List<HendelseId>,
        override val meta: OppgaveHendelseMetadata,
        val beskrivelse: String,
        val oppgavetype: Oppgavetype,
    ) : OppgaveHendelse {
        override val tidligereHendelseId: HendelseId? = null
    }

    /**
     * @param beskrivelse - beskrivelsen som blir returnert fra [OppgaveHttpKallResponse]
     * @param oppgavetype - oppgavetype som blir returnert fra [OppgaveHttpKallResponse]
     */
    data class Oppdatert(
        override val hendelseId: HendelseId = HendelseId.generer(),
        override val hendelsestidspunkt: Tidspunkt,
        override val oppgaveId: OppgaveId,
        override val versjon: Hendelsesversjon,
        override val sakId: UUID,
        override val relaterteHendelser: List<HendelseId>,
        override val meta: OppgaveHendelseMetadata,
        val beskrivelse: String,
        val oppgavetype: Oppgavetype,
        override val tidligereHendelseId: HendelseId,
    ) : OppgaveHendelse

    sealed interface Lukket : OppgaveHendelse {
        val ferdigstiltTidspunkt: Tidspunkt get() = hendelsestidspunkt

        /**
         * @param beskrivelse - beskrivelsen som blir returnert fra [OppgaveHttpKallResponse]
         */
        data class Maskinelt(
            override val hendelseId: HendelseId = HendelseId.generer(),
            override val hendelsestidspunkt: Tidspunkt,
            override val oppgaveId: OppgaveId,
            override val versjon: Hendelsesversjon,
            override val sakId: UUID,
            override val relaterteHendelser: List<HendelseId>,
            override val meta: OppgaveHendelseMetadata,
            override val tidligereHendelseId: HendelseId,
            val beskrivelse: String,
        ) : Lukket

        data class Manuelt(
            override val hendelseId: HendelseId = HendelseId.generer(),
            override val hendelsestidspunkt: Tidspunkt,
            override val oppgaveId: OppgaveId,
            override val versjon: Hendelsesversjon,
            override val sakId: UUID,
            override val relaterteHendelser: List<HendelseId>,
            override val meta: OppgaveHendelseMetadata,
            override val tidligereHendelseId: HendelseId,
            val ferdigstiltAv: NavIdentBruker.Saksbehandler,
        ) : Lukket
    }

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId) { "EntitetIdene eller sakIdene var ikke lik" }
        return this.versjon.compareTo(other.versjon)
    }
}
