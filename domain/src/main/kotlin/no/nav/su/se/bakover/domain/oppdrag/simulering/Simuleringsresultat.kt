package no.nav.su.se.bakover.domain.oppdrag.simulering

import java.time.LocalDate

data class Simulering(
    val gjelderId: String,
    val gjelderNavn: String,
    val datoBeregnet: LocalDate,
    val totalBelop: Int,
    val periodeList: List<SimulertPeriode>
)

data class SimulertPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetaling: List<SimulertUtbetaling>
)

data class SimulertUtbetaling(
    val fagSystemId: String,
    val utbetalesTilId: String,
    val utbetalesTilNavn: String,
    val forfall: LocalDate,
    val feilkonto: Boolean,
    val detaljer: List<SimulertDetaljer>
)

data class SimulertDetaljer(
    val faktiskFom: LocalDate,
    val faktiskTom: LocalDate,
    val konto: String,
    val belop: Int,
    val tilbakeforing: Boolean,
    val sats: Int,
    val typeSats: String,
    val antallSats: Int,
    val uforegrad: Int,
    val klassekode: String,
    val klassekodeBeskrivelse: String,
    val utbetalingsType: String,
    val refunderesOrgNr: String
)

enum class SimuleringFeilet {
    OPPDRAG_UR_ER_STENGT,
    FUNKSJONELL_FEIL,
    TEKNISK_FEIL
}
