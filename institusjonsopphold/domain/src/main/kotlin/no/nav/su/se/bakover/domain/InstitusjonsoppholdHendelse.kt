package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.oppgave.OppgaveHendelse
import java.time.Clock
import java.util.UUID

/**
 * https://github.com/navikt/institusjon/blob/main/apps/institusjon-opphold-hendelser/src/main/java/no/nav/opphold/hendelser/producer/domain/KafkaOppholdHendelse.java
 */
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
    ): InstitusjonsoppholdHendelse = InstitusjonsoppholdHendelse(
        hendelseId = HendelseId.generer(),
        sakId = sakId,
        hendelsestidspunkt = Tidspunkt.now(clock),
        eksterneHendelse = this,
        versjon = versjon,
    )
}

data class InstitusjonsoppholdHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val versjon: Hendelsesversjon,
    override val hendelsestidspunkt: Tidspunkt,
    override val tidligereHendelseId: HendelseId? = null,
    val eksterneHendelse: EksternInstitusjonsoppholdHendelse,
) : Hendelse {
    override val meta: HendelseMetadata = HendelseMetadata.tom()
    override val entitetId: UUID = sakId
    override val triggetAv: HendelseId? = null

    override fun compareTo(other: Hendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId) { "EntitetIdene eller sakIdene var ikke lik" }
        return this.versjon.compareTo(other.versjon)
    }

    fun nyOppgaveHendelse(oppgaveId: OppgaveId, versjon: Hendelsesversjon, clock: Clock): OppgaveHendelse =
        OppgaveHendelse(
            hendelseId = HendelseId.generer(),
            tidligereHendelseId = null,
            sakId = this.sakId,
            versjon = versjon,
            hendelsestidspunkt = Tidspunkt.now(clock),
            triggetAv = this.hendelseId,
            oppgaveId = oppgaveId,
        )
}
