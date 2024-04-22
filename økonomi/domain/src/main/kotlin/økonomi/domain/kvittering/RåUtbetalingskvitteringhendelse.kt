package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.time.Clock
import java.util.UUID

/**
 * Uprosessert kvittering fra Oppdrag.
 * Vi gjør ingen knytninger mot sak eller utbetaling her.
 *
 * Dette er en litt spesiell hendelse hvor vi i praksis ikke har noen entitetId og alltid har versjon 1.
 * Dvs. alle kvitteringer er unike og kan ikke sammenlignes med hverandre.
 */
data class RåUtbetalingskvitteringhendelse(
    override val hendelseId: HendelseId,
    override val hendelsestidspunkt: Tidspunkt,
    val originalKvittering: String,
) : Hendelse<RåUtbetalingskvitteringhendelse> {
    override val tidligereHendelseId: HendelseId? = null
    override val versjon: Hendelsesversjon = Hendelsesversjon(1L)
    override val entitetId: UUID = hendelseId.value

    /**
     * Gir ikke så mye mening i dette tilfelle, da den krever at disse hendelsene er like.
     */
    override fun compareTo(other: RåUtbetalingskvitteringhendelse): Int {
        require(this.entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun fraPersistert(
            hendelseId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            forrigeVersjon: Hendelsesversjon,
            entitetId: UUID,
            originalKvittering: String,
        ): RåUtbetalingskvitteringhendelse {
            return RåUtbetalingskvitteringhendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
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

    fun tilKvitteringPåSakHendelse(
        sakId: UUID,
        nesteVersjon: Hendelsesversjon,
        utbetalingId: UUID30,
        clock: Clock,
        utbetalingsstatus: Kvittering.Utbetalingsstatus,
        tidligereHendelseId: HendelseId,
    ): UtbetalingskvitteringPåSakHendelse {
        return UtbetalingskvitteringPåSakHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            sakId = sakId,
            utbetalingsstatus = utbetalingsstatus,
            originalKvittering = this.originalKvittering,
            versjon = nesteVersjon,
            utbetalingId = utbetalingId,
            tidligereHendelseId = tidligereHendelseId,
        )
    }
}
