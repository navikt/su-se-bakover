package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * Vi knytter en kvittering mot en sak og en utbetaling.
 * @param tidligereHendelseId Hendelsen vi mottok på køen fra Oppdrag. Her vil den originale meldingen ligge. Kan leses som at denne hendelsen erstatter den forrige, selvom entitetId/sakId og versjon ikke vil henge sammen i dette tilfellet. Dette vil gjøre det enklere og debugge sammenhengen mellom disse hendelsene..
 */
data class UtbetalingskvitteringPåSakHendelse(
    override val hendelseId: HendelseId,
    override val versjon: Hendelsesversjon,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val tidligereHendelseId: HendelseId,
    val utbetalingsstatus: Kvittering.Utbetalingsstatus,
    val originalKvittering: String,
    val utbetalingId: UUID30,
) : Sakshendelse {
    init {
        require(versjon.value > 1L) {
            "Bare en liten guard mot å opprette en hendelse med versjon 1, siden den er reservert for SakOpprettetHendelse"
        }
    }

    override val entitetId: UUID = sakId

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun fraPersistert(
            hendelseId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            forrigeVersjon: Hendelsesversjon,
            entitetId: UUID,
            utbetalingsstatus: Kvittering.Utbetalingsstatus,
            originalKvittering: String,
            sakId: UUID,
            utbetalingId: UUID30,
            tidligereHendelseId: HendelseId,
        ): UtbetalingskvitteringPåSakHendelse {
            return UtbetalingskvitteringPåSakHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                sakId = sakId,
                utbetalingsstatus = utbetalingsstatus,
                originalKvittering = originalKvittering,
                versjon = forrigeVersjon,
                utbetalingId = utbetalingId,
                tidligereHendelseId = tidligereHendelseId,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId. "
                }
            }
        }
    }

    val kvittering: Kvittering by lazy {
        Kvittering(
            utbetalingsstatus = utbetalingsstatus,
            originalKvittering = originalKvittering,
            mottattTidspunkt = hendelsestidspunkt,
        )
    }
}
