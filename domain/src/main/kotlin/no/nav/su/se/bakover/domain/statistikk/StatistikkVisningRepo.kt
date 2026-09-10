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

data class SakStatistikkAggregatnøkkel(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val oppløsning: Statistikkoppløsning,
)

enum class SakStatistikkAggregatstatus {
    VENTER,
    PÅGÅR,
    FERDIG,
    FEILET,
}

data class SakStatistikkAggregat(
    val id: UUID,
    val nøkkel: SakStatistikkAggregatnøkkel,
    val status: SakStatistikkAggregatstatus,
    val versjon: Int,
    val maksSekvensId: Long?,
    val opprettet: Instant,
    val startet: Instant?,
    val ferdig: Instant?,
    val payload: String?,
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

interface StatistikkVisningRepo {
    fun hentEllerOpprettSakstatistikkAggregat(
        nøkkel: SakStatistikkAggregatnøkkel,
        versjon: Int,
    ): SakStatistikkAggregat

    fun markerSakstatistikkAggregatForRegenerering(id: UUID, versjon: Int)

    fun hentNesteSakstatistikkAggregatTilGenerering(
        bareId: UUID? = null,
    ): SakStatistikkAggregat?

    fun hentSakstatistikkgrunnlag(
        nøkkel: SakStatistikkAggregatnøkkel,
        maksSekvensId: Long?,
    ): List<SakStatistikkVisningsrad>

    fun hentMaksSakstatistikkSekvensId(nøkkel: SakStatistikkAggregatnøkkel): Long?

    fun ferdigstillSakstatistikkAggregat(
        id: UUID,
        payload: String,
        maksSekvensId: Long?,
        versjon: Int,
    )

    fun markerSakstatistikkAggregatFeilet(id: UUID, feilmelding: String?)

    fun hentStønadstatistikk(
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): List<StønadStatistikkAggregertRad>

    fun hentStønadstatistikkBestandsendringer(
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): List<StønadStatistikkBestandsendringRad>

    fun hentGenererteStønadstatistikkmåneder(
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): Set<YearMonth>
}
