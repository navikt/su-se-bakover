package no.nav.su.se.bakover.domain.oppdrag.simulering

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.su.se.bakover.domain.Fnr
import java.time.LocalDate

data class Simulering(
    val gjelderId: Fnr,
    val gjelderNavn: String,
    val datoBeregnet: LocalDate,
    val nettoBeløp: Int,
    val periodeList: List<SimulertPeriode>
) {
    init {
        listOf(
            SimuleringValidering.SimulerteUtbetalingerHarKunEnDetaljAvTypenYtelse(this),
        ).forEach { require(it.isValid()) { it.message } }
    }

    fun bruttoYtelse() = periodeList
        .sumBy { it.bruttoYtelse() }

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

internal abstract class SimuleringValidering {
    abstract val simulering: Simulering
    abstract val message: String
    abstract fun isValid(): Boolean

    class SimulerteUtbetalingerHarKunEnDetaljAvTypenYtelse(
        override val simulering: Simulering,
        override val message: String = "Simulerte utbetalinger med flere detaljer av typen ${KlasseType.YTEL} indikerer endring av utbetalinger tilbake i tid. Systemet mangler støtte for håndtering av slike tilfeller."
    ) : SimuleringValidering() {
        override fun isValid() = simulering.periodeList
            .flatMap { it.utbetaling }
            .all { it.detaljer.count { it.isYtelse() } == 1 }
    }
}
