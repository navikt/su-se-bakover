package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.tid.Tidspunkt

data class Kvittering(
    val utbetalingsstatus: Utbetalingsstatus,
    val originalKvittering: String,
    val mottattTidspunkt: Tidspunkt,
) {
    enum class Utbetalingsstatus {
        OK,

        /** Akseptert og lagret i opprdag, men det følger med en varselmelding **/
        OK_MED_VARSEL,

        /** Ikke lagret */
        FEIL,
    }

    /**
     * Hvis denne er true har OS/oppdrag akseptert/registrert oppdraget/utbetalingen/utbetalingslinjene.
     * Dette betyr ikke at brukeren har fått noen penger på konto eller ikke, men kan ha fått det eller vil sannsynligvis få det en gang i fremtiden.
     */
    fun erKvittertOk() = setOf(Utbetalingsstatus.OK, Utbetalingsstatus.OK_MED_VARSEL)
        .contains(utbetalingsstatus)
}
