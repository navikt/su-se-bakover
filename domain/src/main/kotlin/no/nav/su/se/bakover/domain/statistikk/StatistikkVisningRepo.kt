package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.tid.Tidspunkt
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class Statistikkoppløsning {
    UKE,
    MÅNED,
    ÅR,
}

data class SakStatistikkVisningsvalg(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val oppløsning: Statistikkoppløsning,
) {
    init {
        require(!fraOgMed.isAfter(tilOgMed)) { "fraOgMed må være før eller lik tilOgMed" }
    }
}

enum class SakStatistikkAggregatstatus {
    VENTER,
    PÅGÅR,
    FERDIG,
    FEILET,
}

data class SakStatistikkAggregat(
    val id: UUID,
    val måned: YearMonth,
    val status: SakStatistikkAggregatstatus,
    val maksSekvensId: Long?,
    val opprettet: Instant,
    val startet: Instant?,
    val ferdig: Instant?,
    val grunnlag: SakStatistikkgrunnlag?,
)

data class SakStatistikkgrunnlag(
    val rader: List<SakStatistikkVisningsrad>,
)

data class SakStatistikkVisningsrad(
    val sekvensId: Long,
    val behandlingId: UUID,
    val sakYtelse: String,
    val behandlingType: String,
    val behandlingAarsak: String?,
    val behandlingStatus: String,
    val behandlingResultat: String?,
    val resultatBegrunnelse: String?,
    val mottattTid: Tidspunkt,
    val registrertTid: Tidspunkt,
    val funksjonellTid: Tidspunkt,
    val tekniskTid: Tidspunkt,
    val revurderingstype: String?,
)

data class StønadStatistikkAggregertRad(
    val måned: YearMonth,
    val stønadstype: StønadstatistikkDto.Stønadstype,
    val vedtakstype: StønadstatistikkDto.Vedtakstype,
    val vedtaksresultat: StønadstatistikkDto.Vedtaksresultat,
    val stønadsklassifisering: StønadsklassifiseringDto?,
    val antall: Int,
)

data class StønadStatistikkBestandsendringRad(
    val måned: YearMonth,
    val stønadstype: StønadstatistikkDto.Stønadstype,
    val nye: Int,
    val videreført: Int,
    val utgått: Int,
    val endretStønadsklassifisering: Int,
)

enum class StønadStatistikkAggregatstatus {
    VENTER,
    PÅGÅR,
    FERDIG,
    FEILET,
}

data class StønadStatistikkAggregat(
    val id: UUID,
    val måned: YearMonth,
    val status: StønadStatistikkAggregatstatus,
    val startet: Instant?,
    val payloadJson: String?,
)

interface StatistikkVisningRepo {
    fun hentEllerOpprettSakstatistikkAggregat(måned: YearMonth): SakStatistikkAggregat

    fun markerSakstatistikkAggregatForRegenerering(id: UUID)

    fun hentNesteSakstatistikkAggregatTilGenerering(
        aggregatId: UUID? = null,
    ): SakStatistikkAggregat?

    fun hentSakstatistikkgrunnlag(
        måned: YearMonth,
        maksSekvensId: Long?,
    ): List<SakStatistikkVisningsrad>

    fun hentMaksSakstatistikkSekvensId(måned: YearMonth): Long?

    fun ferdigstillSakstatistikkAggregat(
        id: UUID,
        startet: Instant,
        grunnlag: SakStatistikkgrunnlag,
        maksSekvensId: Long?,
    )

    fun markerSakstatistikkAggregatFeilet(id: UUID, startet: Instant, feilmelding: String?)

    fun hentStønadstatistikk(måned: YearMonth): List<StønadStatistikkAggregertRad>

    fun hentStønadstatistikkBestandsendringer(måned: YearMonth): List<StønadStatistikkBestandsendringRad>

    fun hentStønadstatistikkAggregater(
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): List<StønadStatistikkAggregat>

    fun hentNesteStønadstatistikkAggregatTilGenerering(
        aggregatId: UUID? = null,
    ): StønadStatistikkAggregat?

    fun ferdigstillStønadstatistikkAggregat(
        id: UUID,
        startet: Instant,
        payloadJson: String,
    )

    fun markerStønadstatistikkAggregatFeilet(id: UUID, startet: Instant, feilmelding: String?)
}
