package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.time.Clock
import java.util.UUID

/**
 * https://github.com/navikt/institusjon/blob/main/apps/institusjon-opphold-hendelser/src/main/java/no/nav/opphold/hendelser/producer/domain/KafkaOppholdHendelse.java
 */
data class EksternInstitusjonsoppholdHendelse(
    val hendelseId: Long,
    val oppholdId: OppholdId,
    val norskident: Fnr,
    val type: InstitusjonsoppholdType,
    val kilde: InstitusjonsoppholdKilde,
) {
    fun nyHendelsePåSak(
        sakId: UUID,
        nesteVersjon: Hendelsesversjon,
        clock: Clock,
    ): InstitusjonsoppholdHendelse = InstitusjonsoppholdHendelse(
        hendelseId = HendelseId.generer(),
        sakId = sakId,
        hendelsestidspunkt = Tidspunkt.now(clock),
        eksterneHendelse = this,
        versjon = nesteVersjon,
    )

    fun nyHendelsePåSakLenketTilEksisterendeHendelse(
        tidligereHendelse: InstitusjonsoppholdHendelse,
        nesteVersjon: Hendelsesversjon,
        clock: Clock,
    ): InstitusjonsoppholdHendelse = InstitusjonsoppholdHendelse(
        hendelseId = HendelseId.generer(),
        sakId = tidligereHendelse.sakId,
        versjon = nesteVersjon,
        hendelsestidspunkt = Tidspunkt.now(clock),
        tidligereHendelseId = tidligereHendelse.hendelseId,
        eksterneHendelse = this,
    )
}

data class OppholdId(val value: Long)

data class InstitusjonsoppholdHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val versjon: Hendelsesversjon,
    override val hendelsestidspunkt: Tidspunkt,
    override val tidligereHendelseId: HendelseId? = null,
    val eksterneHendelse: EksternInstitusjonsoppholdHendelse,
) : Sakshendelse {
    val meta: DefaultHendelseMetadata = DefaultHendelseMetadata.tom()
    override val entitetId: UUID = sakId

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId) { "EntitetIdene eller sakIdene var ikke lik" }
        return this.versjon.compareTo(other.versjon)
    }
}

fun List<InstitusjonsoppholdHendelse>.hentSisteHendelse(): InstitusjonsoppholdHendelse {
    return InstitusjonsoppholdHendelserPåSak(this.toNonEmptyList()).sisteHendelse()
}
