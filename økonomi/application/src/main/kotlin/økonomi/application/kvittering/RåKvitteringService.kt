package økonomi.application.kvittering

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import økonomi.domain.kvittering.RåUtbetalingskvitteringhendelse
import økonomi.domain.kvittering.UtbetalingskvitteringRepo
import java.time.Clock

class RåKvitteringService(
    private val utbetalingKvitteringRepo: UtbetalingskvitteringRepo,
    private val clock: Clock,
) {
    /**
     * Vi har ikke noe de-dup i dette leddet, så vi kan få "duplikat"-hendelser i databasen.
     * Vi løser de-dup i neste ledd, når vi knytter den mot en utbetaling på en sak.
     */
    fun lagreRåKvitteringshendelse(
        originalKvittering: String,
        meta: JMSHendelseMetadata,
    ) {
        utbetalingKvitteringRepo.lagreRåKvitteringHendelse(
            hendelse = RåUtbetalingskvitteringhendelse(
                hendelseId = HendelseId.generer(),
                hendelsestidspunkt = Tidspunkt.now(clock),
                originalKvittering = originalKvittering,
            ),
            meta = meta,
        )
    }
}
