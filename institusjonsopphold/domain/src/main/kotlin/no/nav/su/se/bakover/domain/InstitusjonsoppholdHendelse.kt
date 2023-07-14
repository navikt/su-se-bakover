package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.time.Clock
import java.util.UUID

data class EksternInstitusjonsoppholdHendelse(
    val hendelseId: Long,
    val oppholdId: Long,
    val norskident: Fnr,
    val type: InstitusjonsoppholdType,
    val kilde: InstitusjonsoppholdKilde,
) {
    fun nyHendelseMedSak(
        sakId: UUID,
        versjon: Hendelsesversjon,
        clock: Clock,
    ): InstitusjonsoppholdHendelse.UtenOppgaveId =
        InstitusjonsoppholdHendelse.UtenOppgaveId(
            hendelseId = HendelseId.generer(),
            sakId = sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            eksterneHendelse = this,
            versjon = versjon,
        )
}

sealed interface InstitusjonsoppholdHendelse : Hendelse {
    val eksterneHendelse: EksternInstitusjonsoppholdHendelse
    val oppgaveId: OppgaveId?
    val id: UUID

    fun nyHendelseMedOppgaveId(oppgaveId: OppgaveId, clock: Clock): MedOppgaveId

    data class UtenOppgaveId(
        override val hendelseId: HendelseId,
        override val sakId: UUID,
        override val hendelsestidspunkt: Tidspunkt,
        override val eksterneHendelse: EksternInstitusjonsoppholdHendelse,
        override val versjon: Hendelsesversjon,
    ) : InstitusjonsoppholdHendelse {
        override val id: UUID = hendelseId.value
        override val oppgaveId = null
        override val entitetId: UUID = sakId
        override val tidligereHendelseId: HendelseId? = null
        override val meta: HendelseMetadata = HendelseMetadata.tom()

        override fun nyHendelseMedOppgaveId(oppgaveId: OppgaveId, clock: Clock): MedOppgaveId =
            MedOppgaveId(
                hendelseId = HendelseId.generer(),
                oppgaveId = oppgaveId,
                hendelsestidspunkt = Tidspunkt.now(clock),
                tidligereHendelseId = this.hendelseId,
                sakId = this.sakId,
                versjon = this.versjon.inc(),
                eksterneHendelse = eksterneHendelse,
            )

        override fun compareTo(other: Hendelse): Int {
            require(this.entitetId == other.entitetId && this.sakId == other.sakId) { "EntitetIdene eller sakIdene var ikke lik" }
            return this.versjon.compareTo(other.versjon)
        }
    }

    data class MedOppgaveId(
        override val hendelseId: HendelseId,
        override val oppgaveId: OppgaveId,
        override val hendelsestidspunkt: Tidspunkt,
        override val tidligereHendelseId: HendelseId,
        override val versjon: Hendelsesversjon,
        override val sakId: UUID,
        override val eksterneHendelse: EksternInstitusjonsoppholdHendelse,
    ) : InstitusjonsoppholdHendelse {
        override val id: UUID = hendelseId.value
        override val entitetId: UUID = sakId
        override val meta: HendelseMetadata = HendelseMetadata.tom()

        override fun nyHendelseMedOppgaveId(oppgaveId: OppgaveId, clock: Clock) =
            throw IllegalStateException("Kan ikke knytte til en annen oppgaveId")

        override fun compareTo(other: Hendelse): Int {
            require(this.entitetId == other.entitetId && this.sakId == other.sakId) { "EntitetIdene eller sakIdene var ikke lik" }
            return this.versjon.compareTo(other.versjon)
        }
    }
}
