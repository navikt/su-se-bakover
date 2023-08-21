package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

/**
 * Uprosessert kvittering fra Oppdrag.
 * Vi gjør ingen knytninger mot sak eller utbetaling her.
 *
 * Dette er en litt spesiell hendelse hvor vi i praksis ikke har noen entitetId og alltid har versjon 1.
 * Dvs. alle kvitteringer er unike og kan ikke sammenlignes med hverandre.
 */
data class RåKvitteringHendelse(
    override val hendelseId: HendelseId,
    override val hendelsestidspunkt: Tidspunkt,
    override val meta: HendelseMetadata,
    val originalKvittering: String,
) : Hendelse<RåKvitteringHendelse> {
    override val tidligereHendelseId: HendelseId? = null
    override val versjon: Hendelsesversjon = Hendelsesversjon(1L)
    override val entitetId: UUID = hendelseId.value
    override val triggetAv: HendelseId? = null

    override fun compareTo(other: RåKvitteringHendelse): Int {
        require(this.entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun fraPersistert(
            hendelseId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            hendelseMetadata: HendelseMetadata,
            forrigeVersjon: Hendelsesversjon,
            entitetId: UUID,
            originalKvittering: String,
        ): RåKvitteringHendelse {
            return RåKvitteringHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = hendelseMetadata,
                originalKvittering = originalKvittering,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId. "
                }
                require(forrigeVersjon == Hendelsesversjon(1L)) {
                    "Den persistert versjon var ulik den utleda fra domenet:$forrigeVersjon vs. ${Hendelsesversjon(1L)}. "
                }
            }
        }
    }
}
