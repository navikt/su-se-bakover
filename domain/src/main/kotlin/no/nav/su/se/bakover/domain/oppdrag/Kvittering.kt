package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.now
import java.time.Instant

data class Kvittering(
    val utbetalingsstatus: Utbetalingsstatus,
    val originalKvittering: String,
    val mottattTidspunkt: Instant = now(),

) {
    enum class Utbetalingsstatus {
        OK,
        /** Akseptert og lagret i opprdag, men det følger med en varselmelding **/
        OK_MED_VARSEL,
        /** Ikke lagret */
        FEIL
    }
}
