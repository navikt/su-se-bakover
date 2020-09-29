package no.nav.su.se.bakover.domain.oppdrag.simulering

import no.nav.su.se.bakover.domain.Fnr
import java.time.LocalDate

data class Simulering(
    val gjelderId: Fnr,
    val gjelderNavn: String,
    val datoBeregnet: LocalDate,
    val nettoBeløp: Int,
    val periodeList: List<SimulertPeriode>
) {
    fun bruttoYtelse() = periodeList
        .sumBy { it.bruttoYtelse() }
}

data class SimulertPeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val utbetaling: List<SimulertUtbetaling>
) {
    fun bruttoYtelse() = utbetaling
        .sumBy { it.bruttoYtelse() }
}

data class SimulertUtbetaling(
    val fagSystemId: String,
    val utbetalesTilId: Fnr,
    val utbetalesTilNavn: String,
    val forfall: LocalDate,
    val feilkonto: Boolean,
    val detaljer: List<SimulertDetaljer>
) {
    fun bruttoYtelse() = detaljer
        .filter { it.isYtelse() }
        .sumBy { it.belop }
}

data class SimulertDetaljer(
    val faktiskFraOgMed: LocalDate,
    val faktiskTilOgMed: LocalDate,
    val konto: String,
    val belop: Int,
    val tilbakeforing: Boolean,
    val sats: Int,
    val typeSats: String,
    val antallSats: Int,
    val uforegrad: Int,
    val klassekode: String,
    val klassekodeBeskrivelse: String,
    val klasseType: KlasseType
) {
    fun isYtelse() = KlasseType.YTEL == klasseType
}

enum class SimuleringFeilet {
    OPPDRAG_UR_ER_STENGT,
    FUNKSJONELL_FEIL,
    TEKNISK_FEIL
}

enum class KlasseType {
    YTEL,
    SKAT
}
