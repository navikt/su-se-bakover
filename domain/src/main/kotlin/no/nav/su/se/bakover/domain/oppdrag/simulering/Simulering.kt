package no.nav.su.se.bakover.domain.oppdrag.simulering

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.domain.Fnr
import java.time.LocalDate

data class Simulering(
    val gjelderId: Fnr,
    val gjelderNavn: String,
    val datoBeregnet: LocalDate,
    val nettoBeløp: Int,
    val periodeList: List<SimulertPeriode>,
) {
    fun bruttoYtelse() = periodeList
        .sumOf { it.bruttoYtelse() }

    fun harFeilutbetalinger() = TolketSimulering(this).simulertePerioder.any { it.harFeilutbetalinger() }

    override fun equals(other: Any?) = other is Simulering &&
        other.gjelderId == this.gjelderId &&
        other.gjelderNavn == this.gjelderNavn &&
        other.nettoBeløp == this.nettoBeløp &&
        other.periodeList == this.periodeList &&
        other.bruttoYtelse() == this.bruttoYtelse()
}

data class SimulertPeriode(
    @JsonAlias("fraOgMed", "fom")
    val fraOgMed: LocalDate,
    @JsonAlias("tilOgMed", "tom")
    val tilOgMed: LocalDate,
    val utbetaling: List<SimulertUtbetaling>,
) {

    fun bruttoYtelse() = utbetaling
        .sumOf { it.bruttoYtelse() }
}

data class SimulertUtbetaling(
    val fagSystemId: String,
    val utbetalesTilId: Fnr,
    val utbetalesTilNavn: String,
    val forfall: LocalDate,
    val feilkonto: Boolean,
    val detaljer: List<SimulertDetaljer>,
) {
    fun bruttoYtelse() = detaljer
        .filter { it.isYtelse() }
        .sumOf { it.belop }

    override fun equals(other: Any?) = other is SimulertUtbetaling &&
        other.fagSystemId == this.fagSystemId &&
        other.utbetalesTilId == this.utbetalesTilId &&
        other.utbetalesTilNavn == this.utbetalesTilNavn &&
        other.feilkonto == this.feilkonto &&
        other.detaljer == this.detaljer
}

data class SimulertDetaljer(
    @JsonAlias("faktiskFraOgMed", "faktiskFom")
    val faktiskFraOgMed: LocalDate,
    @JsonAlias("faktiskTilOgMed", "faktiskTom")
    val faktiskTilOgMed: LocalDate,
    val konto: String,
    val belop: Int,
    val tilbakeforing: Boolean,
    val sats: Int,
    val typeSats: String,
    val antallSats: Int,
    val uforegrad: Int,
    val klassekode: KlasseKode,
    val klassekodeBeskrivelse: String,
    val klasseType: KlasseType,
) {
    @JsonIgnore
    fun isYtelse() = KlasseType.YTEL == klasseType
}

enum class SimuleringFeilet {
    OPPDRAG_UR_ER_STENGT,
    FUNKSJONELL_FEIL,
    TEKNISK_FEIL
}

enum class KlasseType {
    YTEL,
    SKAT,
    FEIL,
    @Deprecated("Filtreres ut av klient") // TODO flytt dette lenger ut
    MOTP,
}

enum class KlasseKode {
    SUUFORE,
    KL_KODE_FEIL_INNT,

    @Deprecated("Filtreres ut av klient") // TODO flytt dette lenger ut
    TBMOTOBS,

    @Deprecated("Filtreres ut av klient, bakoverkompatabilitet")
    FSKTSKAT,

    @Deprecated("Filtreres ut av klient, bakoverkompatabilitet")
    UFOREUT
}
