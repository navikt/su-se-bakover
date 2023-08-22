package økonomi.application.kvittering

import økonomi.domain.kvittering.RåKvitteringHendelse
import økonomi.domain.kvittering.UtbetalingKvitteringRepo

class RåKvitteringService(
    private val utbetalingKvitteringRepo: UtbetalingKvitteringRepo,
) {
    /**
     * Vi har ikke noe de-dup i dette leddet, så vi kan få duplikat-hendelser i databasen.
     * Vi løser de-dup i neste ledd, når vi knytter den mot en utbetaling på en sak.
     */
    fun lagreRåKvitteringshendelse(hendelse: RåKvitteringHendelse) {
        utbetalingKvitteringRepo.lagre(hendelse = hendelse)
    }
}
