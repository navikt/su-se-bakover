package økonomi.application.kvittering

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import økonomi.domain.kvittering.RåKvitteringHendelse
import økonomi.domain.kvittering.UtbetalingKvitteringRepo
import java.time.Clock

class RåKvitteringService(
    private val utbetalingKvitteringRepo: UtbetalingKvitteringRepo,
    private val clock: Clock,
) {
    /**
     * Vi har ikke noe de-dup i dette leddet, så vi kan få "duplikat"-hendelser i databasen.
     * Vi løser de-dup i neste ledd, når vi knytter den mot en utbetaling på en sak.
     */
    fun lagreRåKvitteringshendelse(
        originalKvittering: String,
        correlationId: CorrelationId,
    ) {
        utbetalingKvitteringRepo.lagre(
            hendelse = RåKvitteringHendelse(
                hendelseId = HendelseId.generer(),
                hendelsestidspunkt = Tidspunkt.now(clock),
                meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                originalKvittering = originalKvittering,
            ),
        )
    }
}
