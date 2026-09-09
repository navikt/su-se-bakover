package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatnøkkel
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatstatus
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkVisningsrad
import no.nav.su.se.bakover.domain.statistikk.StatistikkVisningRepo
import no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkAggregertRad
import org.slf4j.LoggerFactory
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.ceil

private const val AGGREGATVERSJON = 1
private val AVGANGSSTATUSER = setOf("IVERKSATT", "AVSLUTTET", "AVBRUTT", "OVERSENDT")
private val TERMINALE_STATUSER = setOf("IVERKSATT", "AVSLUTTET", "AVBRUTT")
private val KATEGORIER_MED_TIDSMÅLING = setOf(
    SakStatistikkKategori.SØKNAD,
    SakStatistikkKategori.REVURDERING,
    SakStatistikkKategori.KLAGE,
)

interface StatistikkVisningService {
    fun hentSakstatistikk(nøkkel: SakStatistikkAggregatnøkkel): SakstatistikkSvar
    fun genererVentendeSakstatistikk(bareId: UUID? = null, maksAntall: Int = 10)
    fun hentStønadstatistikk(fraOgMed: YearMonth, tilOgMed: YearMonth): StønadStatistikkOppsummering
}

sealed interface SakstatistikkSvar {
    data class Ferdig(val payload: String) : SakstatistikkSvar
    data class Genererer(val aggregatId: UUID) : SakstatistikkSvar
}

class StatistikkVisningServiceImpl(
    private val repo: StatistikkVisningRepo,
) : StatistikkVisningService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentSakstatistikk(nøkkel: SakStatistikkAggregatnøkkel): SakstatistikkSvar {
        val aggregat = repo.hentEllerOpprettSakstatistikkAggregat(nøkkel, AGGREGATVERSJON)
        val maksSekvensId = repo.hentMaksSakstatistikkSekvensId(nøkkel)
        val payload = aggregat.payload
        if (
            aggregat.status == SakStatistikkAggregatstatus.FERDIG &&
            aggregat.versjon == AGGREGATVERSJON &&
            aggregat.maksSekvensId == maksSekvensId &&
            payload != null
        ) {
            return SakstatistikkSvar.Ferdig(payload)
        }

        if (aggregat.status != SakStatistikkAggregatstatus.PÅGÅR) {
            repo.markerSakstatistikkAggregatForRegenerering(aggregat.id, AGGREGATVERSJON)
        }
        return SakstatistikkSvar.Genererer(aggregat.id)
    }

    override fun genererVentendeSakstatistikk(bareId: UUID?, maksAntall: Int) {
        repeat(maksAntall) {
            val aggregat = repo.hentNesteSakstatistikkAggregatTilGenerering(bareId) ?: return
            runCatching {
                val maksSekvensId = repo.hentMaksSakstatistikkSekvensId(aggregat.nøkkel)
                val rader = repo.hentSakstatistikkgrunnlag(aggregat.nøkkel, maksSekvensId)
                val oppsummering = rader.tilOppsummering(aggregat.nøkkel)
                repo.ferdigstillSakstatistikkAggregat(
                    id = aggregat.id,
                    payload = serialize(oppsummering),
                    maksSekvensId = maksSekvensId,
                    versjon = AGGREGATVERSJON,
                )
            }.onFailure {
                log.error("Generering av sakstatistikkaggregat feilet. AggregatId=${aggregat.id}", it)
                repo.markerSakstatistikkAggregatFeilet(aggregat.id, it.message)
            }
            if (bareId != null) return
        }
    }

    override fun hentStønadstatistikk(
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): StønadStatistikkOppsummering {
        return StønadStatistikkOppsummering(
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            perioder = repo.hentStønadstatistikk(fraOgMed, tilOgMed)
                .groupBy { it.måned }
                .map { (måned, rader) ->
                    StønadStatistikkPeriode(
                        måned = måned,
                        rader = rader.map(StønadStatistikkAggregertRad::tilJson),
                    )
                },
        )
    }
}

enum class SakStatistikkKategori {
    SØKNAD,
    REVURDERING,
    KLAGE,
    STANS,
    GJENOPPTAK,
    REGULERING,
    TILBAKEKREVING,
}

data class SakStatistikkOppsummering(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val oppløsning: Statistikkoppløsning,
    val perioder: List<SakStatistikkPeriode>,
)

data class SakStatistikkPeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val antall: List<SakStatistikkAntall>,
    val utfall: List<SakStatistikkUtfall>,
    val beholdning: List<SakStatistikkStatus>,
    val behandlingstid: List<SakStatistikkBehandlingstid>,
)

data class SakStatistikkAntall(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val behandlingAarsak: String?,
    val behandlingMetode: String,
    val antall: Int,
)

data class SakStatistikkUtfall(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val status: String,
    val resultat: String?,
    val antall: Int,
)

data class SakStatistikkStatus(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val status: String,
    val antall: Int,
)

enum class Behandlingstidsmåling {
    TOTAL_BEHANDLINGSTID,
    SAKSBEHANDLING_FØR_ATTESTERING,
    TID_HOS_ATTESTANT,
    TID_ETTER_UNDERKJENNING,
}

data class SakStatistikkBehandlingstid(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val måling: Behandlingstidsmåling,
    val antall: Int,
    val gjennomsnittMillis: Long,
    val medianMillis: Long,
    val nittiendePersentilMillis: Long,
)

data class StønadStatistikkOppsummering(
    val fraOgMed: YearMonth,
    val tilOgMed: YearMonth,
    val perioder: List<StønadStatistikkPeriode>,
)

data class StønadStatistikkPeriode(
    val måned: YearMonth,
    val rader: List<StønadStatistikkAntall>,
)

data class StønadStatistikkAntall(
    val stønadstype: StønadstatistikkDto.Stønadstype,
    val vedtakstype: StønadstatistikkDto.Vedtakstype,
    val vedtaksresultat: StønadstatistikkDto.Vedtaksresultat,
    val stønadsklassifisering: StønadsklassifiseringDto?,
    val antall: Int,
)

private data class Behandlingsforløp(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val behandlingAarsak: String?,
    val behandlingMetode: String,
    val hendelser: List<SakStatistikkVisningsrad>,
)

private data class Periodespenn(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
)

private data class Tilgangsnøkkel(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val behandlingAarsak: String?,
    val behandlingMetode: String,
)

private data class Utfallnøkkel(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val status: String,
    val resultat: String?,
)

private data class Statusnøkkel(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val status: String,
)

private data class Behandlingstidsnøkkel(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val måling: Behandlingstidsmåling,
)

private data class Behandlingstidspunkt(
    val nøkkel: Behandlingstidsnøkkel,
    val avsluttetDato: LocalDate,
    val varighetMillis: Long,
)

private fun List<SakStatistikkVisningsrad>.tilOppsummering(
    nøkkel: SakStatistikkAggregatnøkkel,
): SakStatistikkOppsummering {
    val forløp = groupBy { it.behandlingId }.values.map { rader ->
        val hendelser = rader.sortedBy { it.sekvensId }.kollapsLikeStatuser()
        val siste = hendelser.last()
        Behandlingsforløp(
            kategori = siste.tilKategori(),
            sakYtelse = siste.sakYtelse,
            behandlingAarsak = hendelser.mapNotNull { it.behandlingAarsak }.lastOrNull(),
            behandlingMetode = siste.behandlingMetode,
            hendelser = hendelser,
        )
    }
    val behandlingstider = forløp.flatMap { it.behandlingstider() }

    return SakStatistikkOppsummering(
        fraOgMed = nøkkel.fraOgMed,
        tilOgMed = nøkkel.tilOgMed,
        oppløsning = nøkkel.oppløsning,
        perioder = nøkkel.perioder().map { periode ->
            val antall = forløp.mapNotNull { behandlingsforløp ->
                val første = behandlingsforløp.hendelser.first()
                if (første.dato() !in periode.fraOgMed..periode.tilOgMed) return@mapNotNull null
                Tilgangsnøkkel(
                    kategori = behandlingsforløp.kategori,
                    sakYtelse = behandlingsforløp.sakYtelse,
                    behandlingAarsak = behandlingsforløp.behandlingAarsak,
                    behandlingMetode = behandlingsforløp.behandlingMetode,
                )
            }.groupingBy { it }.eachCount().map { (nøkkel, antall) ->
                SakStatistikkAntall(
                    kategori = nøkkel.kategori,
                    sakYtelse = nøkkel.sakYtelse,
                    behandlingAarsak = nøkkel.behandlingAarsak,
                    behandlingMetode = nøkkel.behandlingMetode,
                    antall = antall,
                )
            }

            val utfall = forløp.flatMap { behandlingsforløp ->
                behandlingsforløp.hendelser.filter {
                    it.behandlingStatus in AVGANGSSTATUSER &&
                        it.dato() in periode.fraOgMed..periode.tilOgMed
                }.map {
                    Utfallnøkkel(
                        kategori = behandlingsforløp.kategori,
                        sakYtelse = behandlingsforløp.sakYtelse,
                        status = it.behandlingStatus,
                        resultat = it.behandlingResultat,
                    )
                }
            }.groupingBy { it }.eachCount().map { (nøkkel, antall) ->
                SakStatistikkUtfall(
                    kategori = nøkkel.kategori,
                    sakYtelse = nøkkel.sakYtelse,
                    status = nøkkel.status,
                    resultat = nøkkel.resultat,
                    antall = antall,
                )
            }

            val beholdning = forløp.mapNotNull { behandlingsforløp ->
                val sisteStatus = behandlingsforløp.hendelser.lastOrNull {
                    !it.dato().isAfter(periode.tilOgMed)
                } ?: return@mapNotNull null
                if (sisteStatus.behandlingStatus in TERMINALE_STATUSER) return@mapNotNull null
                Statusnøkkel(
                    kategori = behandlingsforløp.kategori,
                    sakYtelse = behandlingsforløp.sakYtelse,
                    status = sisteStatus.behandlingStatus,
                )
            }.groupingBy { it }.eachCount().map { (nøkkel, antall) ->
                SakStatistikkStatus(
                    kategori = nøkkel.kategori,
                    sakYtelse = nøkkel.sakYtelse,
                    status = nøkkel.status,
                    antall = antall,
                )
            }

            SakStatistikkPeriode(
                fraOgMed = periode.fraOgMed,
                tilOgMed = periode.tilOgMed,
                antall = antall.sortedBy { "${it.kategori}-${it.sakYtelse}-${it.behandlingAarsak}" },
                utfall = utfall.sortedBy { "${it.kategori}-${it.sakYtelse}-${it.status}-${it.resultat}" },
                beholdning = beholdning.sortedBy { "${it.kategori}-${it.sakYtelse}-${it.status}" },
                behandlingstid = behandlingstider
                    .filter { it.avsluttetDato in periode.fraOgMed..periode.tilOgMed }
                    .groupBy { it.nøkkel }
                    .map { (tidsnøkkel, målinger) ->
                        målinger.map { it.varighetMillis }.tilBehandlingstid(tidsnøkkel)
                    }
                    .sortedBy { "${it.kategori}-${it.sakYtelse}-${it.måling}" },
            )
        },
    )
}

private fun List<SakStatistikkVisningsrad>.kollapsLikeStatuser(): List<SakStatistikkVisningsrad> =
    fold(emptyList()) { resultat, rad ->
        if (resultat.lastOrNull()?.behandlingStatus == rad.behandlingStatus) {
            resultat
        } else {
            resultat + rad
        }
    }

private fun Behandlingsforløp.behandlingstider(): List<Behandlingstidspunkt> {
    if (kategori !in KATEGORIER_MED_TIDSMÅLING) return emptyList()
    val resultat = mutableListOf<Behandlingstidspunkt>()

    hendelser.zipWithNext().forEach { (fra, til) ->
        val måling = when {
            fra.behandlingStatus == "UNDERKJENT" && til.behandlingStatus == "TIL_ATTESTERING" ->
                Behandlingstidsmåling.TID_ETTER_UNDERKJENNING

            fra.behandlingStatus == "REGISTRERT" && til.behandlingStatus == "TIL_ATTESTERING" ->
                Behandlingstidsmåling.SAKSBEHANDLING_FØR_ATTESTERING

            fra.behandlingStatus == "TIL_ATTESTERING" &&
                til.behandlingStatus in setOf("IVERKSATT", "UNDERKJENT") ->
                Behandlingstidsmåling.TID_HOS_ATTESTANT

            else -> null
        }
        if (måling != null) {
            resultat += Behandlingstidspunkt(
                nøkkel = Behandlingstidsnøkkel(kategori, sakYtelse, måling),
                avsluttetDato = til.dato(),
                varighetMillis = varighetMellom(fra, til),
            )
        }
    }

    val første = hendelser.first()
    val siste = hendelser.firstOrNull { it.behandlingStatus in AVGANGSSTATUSER }
    if (siste != null) {
        resultat += Behandlingstidspunkt(
            nøkkel = Behandlingstidsnøkkel(
                kategori,
                sakYtelse,
                Behandlingstidsmåling.TOTAL_BEHANDLINGSTID,
            ),
            avsluttetDato = siste.dato(),
            varighetMillis = varighetMellom(første, siste),
        )
    }
    return resultat
}

private fun varighetMellom(
    fra: SakStatistikkVisningsrad,
    til: SakStatistikkVisningsrad,
): Long = Duration.between(fra.funksjonellTid.instant, til.funksjonellTid.instant).toMillis().coerceAtLeast(0)

private fun List<Long>.tilBehandlingstid(nøkkel: Behandlingstidsnøkkel): SakStatistikkBehandlingstid {
    val sortert = sorted()
    return SakStatistikkBehandlingstid(
        kategori = nøkkel.kategori,
        sakYtelse = nøkkel.sakYtelse,
        måling = nøkkel.måling,
        antall = size,
        gjennomsnittMillis = average().toLong(),
        medianMillis = sortert.persentil(0.5),
        nittiendePersentilMillis = sortert.persentil(0.9),
    )
}

private fun List<Long>.persentil(persentil: Double): Long {
    val indeks = (ceil(size * persentil).toInt() - 1).coerceIn(indices)
    return this[indeks]
}

private fun SakStatistikkVisningsrad.dato(): LocalDate = funksjonellTid.toLocalDate(zoneIdOslo)

private fun SakStatistikkVisningsrad.tilKategori(): SakStatistikkKategori {
    return when (behandlingType) {
        "SOKNAD" -> SakStatistikkKategori.SØKNAD
        "KLAGE" -> SakStatistikkKategori.KLAGE
        "TILBAKEKREVING" -> SakStatistikkKategori.TILBAKEKREVING
        "REVURDERING" -> when {
            behandlingAarsak == "REGULER_GRUNNBELØP" -> SakStatistikkKategori.REGULERING
            revurderingstype?.endsWith("_STANS") == true -> SakStatistikkKategori.STANS
            revurderingstype?.endsWith("_GJENOPPTAK") == true -> SakStatistikkKategori.GJENOPPTAK
            else -> SakStatistikkKategori.REVURDERING
        }

        else -> throw IllegalStateException("Ukjent behandlingstype i sakstatistikk: $behandlingType")
    }
}

private fun SakStatistikkAggregatnøkkel.perioder(): List<Periodespenn> {
    val perioder = mutableListOf<Periodespenn>()
    var start = fraOgMed
    while (!start.isAfter(tilOgMed)) {
        val naturligSlutt = when (oppløsning) {
            Statistikkoppløsning.UKE -> start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            Statistikkoppløsning.MÅNED -> start.with(TemporalAdjusters.lastDayOfMonth())
            Statistikkoppløsning.ÅR -> start.with(TemporalAdjusters.lastDayOfYear())
        }
        val slutt = minOf(naturligSlutt, tilOgMed)
        perioder += Periodespenn(start, slutt)
        start = slutt.plusDays(1)
    }
    return perioder
}

private fun StønadStatistikkAggregertRad.tilJson() = StønadStatistikkAntall(
    stønadstype = stønadstype,
    vedtakstype = vedtakstype,
    vedtaksresultat = vedtaksresultat,
    stønadsklassifisering = stønadsklassifisering,
    antall = antall,
)
