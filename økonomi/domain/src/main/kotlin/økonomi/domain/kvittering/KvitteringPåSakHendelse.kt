package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * Vi knytter en kvittering mot en sak og en utbetaling.
 *
 *
 */
data class KvitteringPåSakHendelse(
    override val hendelseId: HendelseId,
    override val versjon: Hendelsesversjon,
    override val triggetAv: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val meta: HendelseMetadata,
    val utbetalingsstatus: Kvittering.Utbetalingsstatus,
    val originalKvittering: String,
    val utbetalingId: UUID,
) : Sakshendelse {
    init {
        require(versjon.value > 1L) {
            "Bare en liten guard mot å opprette en hendelse med versjon 1, siden den er reservert for SakOpprettetHendelse"
        }
    }

    override val tidligereHendelseId: HendelseId? = null
    override val entitetId: UUID = sakId

    override fun compareTo(other: Sakshendelse): Int {
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
            utbetalingsstatus: Kvittering.Utbetalingsstatus,
            originalKvittering: String,
            sakId: UUID,
            triggetAv: HendelseId,
            utbetalingId: UUID,
        ): KvitteringPåSakHendelse {
            return KvitteringPåSakHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = hendelseMetadata,
                sakId = sakId,
                utbetalingsstatus = utbetalingsstatus,
                originalKvittering = originalKvittering,
                versjon = forrigeVersjon,
                triggetAv = triggetAv,
                utbetalingId = utbetalingId,
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
