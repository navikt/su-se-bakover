package no.nav.su.se.bakover.domain.oppdrag.simulering

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Månedsbeløp
import no.nav.su.se.bakover.domain.Sakstype
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

    fun harFeilutbetalinger(): Boolean {
        return tolk().simulertePerioder.any { it.harFeilutbetalinger() }
    }

    fun tolk(): TolketSimulering {
        return TolketSimulering(this)
    }

    fun hentUtbetalteBeløp(): Månedsbeløp {
        return tolk().hentUtbetalteBeløp()
    }

    fun hentFeilutbetalteBeløp(): Månedsbeløp {
        return tolk().hentFeilutbetalteBeløp()
    }

    /**
     * Nettobeløpet påvirkes av skatt, så tas ikke med i equals-sjekken.
     * Bruttobeløpet, altså summen av månedsbeløpene, brukes i stedet .
     */
    override fun equals(other: Any?) = other is Simulering &&
        other.gjelderId == this.gjelderId &&
        other.gjelderNavn == this.gjelderNavn &&
        other.periodeList == this.periodeList &&
        other.bruttoYtelse() == this.bruttoYtelse()

    override fun hashCode(): Int {
        var result = gjelderId.hashCode()
        result = 31 * result + gjelderNavn.hashCode()
        result = 31 * result + periodeList.hashCode()
        return result
    }
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

    override fun hashCode(): Int {
        var result = fagSystemId.hashCode()
        result = 31 * result + utbetalesTilId.hashCode()
        result = 31 * result + utbetalesTilNavn.hashCode()
        result = 31 * result + feilkonto.hashCode()
        result = 31 * result + detaljer.hashCode()
        return result
    }
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

enum class KlasseType {
    YTEL,
    SKAT,
    FEIL,

    @Deprecated("Filtreres ut av klient") // TODO flytt dette lenger ut
    MOTP,

    ;

    companion object {
        fun skalIkkeFiltreres() = setOf(YTEL, FEIL)
    }
}

enum class KlasseKode {
    SUUFORE,
    KL_KODE_FEIL_INNT,

    @Deprecated("Filtreres ut av klient") // TODO flytt dette lenger ut
    TBMOTOBS,

    @Deprecated("Filtreres ut av klient, bakoverkompatabilitet")
    FSKTSKAT,

    @Deprecated("Filtreres ut av klient, bakoverkompatabilitet")
    UFOREUT,

    SUALDER,
    KL_KODE_FEIL,
    ;

    companion object {
        fun skalIkkeFiltreres() = setOf(SUUFORE, KL_KODE_FEIL_INNT, SUALDER, KL_KODE_FEIL)
    }
}

fun Sakstype.toYtelsekode(): KlasseKode {
    return when (this) {
        Sakstype.ALDER -> {
            KlasseKode.SUALDER
        }
        Sakstype.UFØRE -> {
            KlasseKode.SUUFORE
        }
    }
}

fun Sakstype.toFeilkode(): KlasseKode {
    return when (this) {
        Sakstype.ALDER -> {
            KlasseKode.KL_KODE_FEIL
        }
        Sakstype.UFØRE -> {
            KlasseKode.KL_KODE_FEIL_INNT
        }
    }
}
