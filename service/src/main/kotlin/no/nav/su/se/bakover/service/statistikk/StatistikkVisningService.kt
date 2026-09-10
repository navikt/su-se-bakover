package no.nav.su.se.bakover.service.statistikk

import behandling.klage.domain.Hjemmel
import behandling.klage.domain.VurderingerTilKlage
import behandling.revurdering.domain.Opphørsgrunn
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatnøkkel
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatstatus
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkVisningsrad
import no.nav.su.se.bakover.domain.statistikk.StatistikkVisningRepo
import no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkAggregertRad
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkBestandsendringRad
import org.slf4j.LoggerFactory
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import vilkår.common.domain.Avslagsgrunn
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.ceil

private const val AGGREGATVERSJON = 8
private const val OMGJØRING_ETTER_AVVIST = "OMGJORING_ETTER_AVVIST"
private const val OMGJØRING_ETTER_AVSLAG = "OMGJORING_ETTER_AVSLAG"
private const val FOR_TIDLIG_SØKNAD = "FOR_TIDLIG_SØKNAD"
private const val FOR_TIDLIG_SØKNAD_RÅVERDI = "Avslag på grunn av for tidlig søknad"
private const val AVSLAG = "AVSLAG"
private const val AVSLÅTT = "AVSLÅTT"
private const val OPPHØRT = "OPPHØRT"
private const val AVVIST = "AVVIST"
private const val OPPRETTHOLDT = "OPPRETTHOLDT"
private const val DELVIS_OMGJØRING = "DELVIS_OMGJØRING"
private const val OMGJORT = "OMGJORT"
private const val BORTFALT = "BORTFALT"
private const val HISTORISK_OPPHØRT_REVURDERING = "OpphørtRevurdering"
private const val HISTORISK_FEILREGISTRERT = "Feilregistrert"
private const val HISTORISK_FEILREGISTRERT_VERSALER = "FEILREGISTRERT"
private val AVGANGSSTATUSER = setOf("IVERKSATT", "AVSLUTTET", "AVBRUTT", "OVERSENDT")
private val TERMINALE_STATUSER = AVGANGSSTATUSER
private val KATEGORIER_MED_TIDSMÅLING = setOf(
    SakStatistikkKategori.SØKNAD,
    SakStatistikkKategori.REVURDERING,
    SakStatistikkKategori.KLAGE,
)
private val KATEGORIER_MED_UNDERKJENNINGSSTATISTIKK = setOf(
    SakStatistikkKategori.SØKNAD,
    SakStatistikkKategori.REVURDERING,
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
                val oppsummering = rader.tilOppsummering(aggregat.nøkkel, maksSekvensId)
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
        val raderPerMåned = repo.hentStønadstatistikk(fraOgMed, tilOgMed).groupBy { it.måned }
        val endringerPerMåned = repo.hentStønadstatistikkBestandsendringer(fraOgMed, tilOgMed).groupBy { it.måned }
        val genererteMåneder = repo.hentGenererteStønadstatistikkmåneder(fraOgMed.minusMonths(1), tilOgMed)
        return StønadStatistikkOppsummering(
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            perioder = fraOgMed.månederTilOgMed(tilOgMed).map { måned ->
                val datagrunnlagTilgjengelig = måned in genererteMåneder
                StønadStatistikkPeriode(
                    måned = måned,
                    datagrunnlag = if (datagrunnlagTilgjengelig) {
                        StønadStatistikkDatagrunnlag.TILGJENGELIG
                    } else {
                        StønadStatistikkDatagrunnlag.MANGLER
                    },
                    rader = if (datagrunnlagTilgjengelig) {
                        raderPerMåned[måned].orEmpty().map(StønadStatistikkAggregertRad::tilJson)
                    } else {
                        emptyList()
                    },
                    bestandsendringerTilgjengelig = datagrunnlagTilgjengelig &&
                        måned.minusMonths(1) in genererteMåneder,
                    bestandsendringer = if (
                        datagrunnlagTilgjengelig && måned.minusMonths(1) in genererteMåneder
                    ) {
                        endringerPerMåned[måned].orEmpty().map(StønadStatistikkBestandsendringRad::tilJson)
                    } else {
                        emptyList()
                    },
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
    TILBAKEKREVING,
}

data class SakStatistikkOppsummering(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val oppløsning: Statistikkoppløsning,
    val metadata: SakStatistikkMetadata,
    val perioder: List<SakStatistikkPeriode>,
    val kohorter: List<SakStatistikkKohort>,
)

data class SakStatistikkMetadata(
    val aggregatversjon: Int,
    val maksSekvensId: Long?,
    val sisteHendelseTidspunkt: Instant?,
    val antallBehandlinger: Int,
    val behandlingerMedFlereUtfall: Int,
)

data class SakStatistikkPeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val antall: List<SakStatistikkAntall>,
    val utfall: List<SakStatistikkUtfall>,
    val beholdning: List<SakStatistikkStatus>,
    val behandlingstid: List<SakStatistikkBehandlingstid>,
    val beholdningsalder: List<SakStatistikkBeholdningsalder>,
    val omarbeid: List<SakStatistikkOmarbeid>,
    val avslagsgrunner: List<SakStatistikkAvslagsfordeling>,
    val opphørsgrunner: List<SakStatistikkOpphørsfordeling>,
    val klageavvisningsgrunner: List<SakStatistikkKlageavvisningsfordeling>,
    val klagehjemler: List<SakStatistikkKlagehjemmelfordeling>,
    val klageomgjøringsgrunner: List<SakStatistikkKlageomgjøringsfordeling>,
)

data class SakStatistikkAntall(
    val behandlingskategori: SakStatistikkKategori,
    val sakYtelse: String,
    val behandlingAarsak: String?,
    val antall: Int,
)

data class SakStatistikkUtfall(
    val behandlingskategori: SakStatistikkKategori,
    val sakYtelse: String,
    val status: String,
    val resultat: String?,
    val antall: Int,
)

data class SakStatistikkStatus(
    val behandlingskategori: SakStatistikkKategori,
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
    val behandlingskategori: SakStatistikkKategori,
    val sakYtelse: String,
    val måling: Behandlingstidsmåling,
    val antall: Int,
    val gjennomsnittMillis: Long,
    val medianMillis: Long,
)

enum class Beholdningsaldersmåling {
    BEHANDLINGENS_ALDER,
    TID_I_NÅVÆRENDE_STATUS,
}

enum class Beholdningsaldersintervall {
    DAGER_0_7,
    DAGER_8_30,
    DAGER_31_60,
    DAGER_61_90,
    OVER_90_DAGER,
}

data class SakStatistikkBeholdningsalder(
    val behandlingskategori: SakStatistikkKategori,
    val sakYtelse: String,
    val status: String,
    val måling: Beholdningsaldersmåling,
    val intervall: Beholdningsaldersintervall,
    val antall: Int,
)

data class SakStatistikkOmarbeid(
    val behandlingskategori: SakStatistikkKategori,
    val sakYtelse: String,
    val behandlingerMedUtfall: Int,
    val utenUnderkjenning: Int,
    val medEnUnderkjenning: Int,
    val medFlereUnderkjenninger: Int,
    val medianTidEtterUnderkjenningMillis: Long?,
)

data class SakStatistikkKohort(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val behandlingskategori: SakStatistikkKategori,
    val sakYtelse: String,
    val antallStartet: Int,
    val ferdigInnen30Dager: SakStatistikkKohortfrist,
    val ferdigInnen60Dager: SakStatistikkKohortfrist,
    val ferdigInnen90Dager: SakStatistikkKohortfrist,
    val åpneVedTilOgMed: Int,
)

data class SakStatistikkKohortfrist(
    val grunnlag: Int,
    val ferdige: Int,
)

enum class SakStatistikkLov {
    SU,
    FVL,
}

data class SakStatistikkParagraf(
    val lov: SakStatistikkLov,
    val paragraf: Int,
)

data class SakStatistikkAvslagsfordeling(
    val sakYtelse: String,
    val antallAvslag: Int,
    val antallUtenBegrunnelse: Int,
    val antallMedUkjentBegrunnelse: Int,
    val grunner: List<SakStatistikkAvslagsgrunn>,
)

data class SakStatistikkAvslagsgrunn(
    val kode: String,
    val paragrafer: List<SakStatistikkParagraf>,
    val antallBehandlinger: Int,
)

data class SakStatistikkOpphørsfordeling(
    val sakYtelse: String,
    val antallOpphør: Int,
    val antallUtenBegrunnelse: Int,
    val antallMedUkjentBegrunnelse: Int,
    val grunner: List<SakStatistikkOpphørsgrunn>,
)

data class SakStatistikkOpphørsgrunn(
    val kode: String,
    val paragrafer: List<SakStatistikkParagraf>,
    val antallBehandlinger: Int,
)

data class SakStatistikkKlageavvisningsfordeling(
    val sakYtelse: String,
    val antallAvvisteKlager: Int,
    val antallUtenBegrunnelse: Int,
    val antallMedUkjentBegrunnelse: Int,
    val grunner: List<SakStatistikkKlageavvisningsgrunn>,
)

data class SakStatistikkKlageavvisningsgrunn(
    val kode: String,
    val antallBehandlinger: Int,
)

data class SakStatistikkKlagehjemmelfordeling(
    val sakYtelse: String,
    val resultat: String,
    val antallKlager: Int,
    val antallUtenHjemmel: Int,
    val antallMedUkjentHjemmel: Int,
    val hjemler: List<SakStatistikkKlagehjemmel>,
)

data class SakStatistikkKlagehjemmel(
    val kode: String,
    val lov: SakStatistikkLov,
    val paragraf: Int,
    val antallBehandlinger: Int,
)

data class SakStatistikkKlageomgjøringsfordeling(
    val sakYtelse: String,
    val resultat: String,
    val antallKlager: Int,
    val antallUtenBegrunnelse: Int,
    val antallMedUkjentBegrunnelse: Int,
    val grunner: List<SakStatistikkKlageomgjøringsgrunn>,
)

data class SakStatistikkKlageomgjøringsgrunn(
    val kode: String,
    val antallBehandlinger: Int,
)

data class StønadStatistikkOppsummering(
    val fraOgMed: YearMonth,
    val tilOgMed: YearMonth,
    val perioder: List<StønadStatistikkPeriode>,
)

data class StønadStatistikkPeriode(
    val måned: YearMonth,
    val datagrunnlag: StønadStatistikkDatagrunnlag,
    val rader: List<StønadStatistikkAntall>,
    val bestandsendringerTilgjengelig: Boolean,
    val bestandsendringer: List<StønadStatistikkBestandsendring>,
)

enum class StønadStatistikkDatagrunnlag {
    TILGJENGELIG,
    MANGLER,
}

data class StønadStatistikkAntall(
    val stønadstype: StønadstatistikkDto.Stønadstype,
    val vedtakstype: StønadstatistikkDto.Vedtakstype,
    val vedtaksresultat: StønadstatistikkDto.Vedtaksresultat,
    val stønadsklassifisering: StønadsklassifiseringDto?,
    val antall: Int,
)

data class StønadStatistikkBestandsendring(
    val stønadstype: StønadstatistikkDto.Stønadstype,
    val nye: Int,
    val videreført: Int,
    val utgått: Int,
    val endretStønadsklassifisering: Int,
)

private data class Behandlingsforløp(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val behandlingAarsak: String?,
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

private data class ÅpenBehandling(
    val forløp: Behandlingsforløp,
    val sisteStatus: SakStatistikkVisningsrad,
)

private data class Beholdningsaldernøkkel(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
    val status: String,
    val måling: Beholdningsaldersmåling,
    val intervall: Beholdningsaldersintervall,
)

private data class Omarbeidsnøkkel(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
)

private data class Omarbeidsobservasjon(
    val nøkkel: Omarbeidsnøkkel,
    val antallUnderkjenninger: Int,
    val tidEtterUnderkjenningMillis: Long?,
)

private data class Kohortnøkkel(
    val kategori: SakStatistikkKategori,
    val sakYtelse: String,
)

private data class AvsluttetBehandling(
    val forløp: Behandlingsforløp,
    val utfall: SakStatistikkVisningsrad,
    val begrunnelser: Set<String>,
)

private data class YtelseOgResultat(
    val sakYtelse: String,
    val resultat: String,
)

internal fun List<SakStatistikkVisningsrad>.tilOppsummering(
    nøkkel: SakStatistikkAggregatnøkkel,
    maksSekvensId: Long?,
): SakStatistikkOppsummering {
    val forløp = groupBy { it.behandlingId }.values.mapNotNull { rader ->
        val sorterteRader = rader.sortedBy { it.sekvensId }
        val hendelser = sorterteRader.kollapsLikeStatuser()
        if (sorterteRader.any { it.behandlingAarsak == "REGULER_GRUNNBELØP" }) return@mapNotNull null
        val siste = hendelser.last()
        Behandlingsforløp(
            kategori = siste.tilKategori(),
            sakYtelse = siste.sakYtelse,
            behandlingAarsak = sorterteRader.first().behandlingAarsak.tilVisningsårsak(),
            hendelser = hendelser,
        )
    }
    val behandlingstider = forløp.flatMap { it.behandlingstider() }
    val perioder = nøkkel.perioder()

    return SakStatistikkOppsummering(
        fraOgMed = nøkkel.fraOgMed,
        tilOgMed = nøkkel.tilOgMed,
        oppløsning = nøkkel.oppløsning,
        metadata = SakStatistikkMetadata(
            aggregatversjon = AGGREGATVERSJON,
            maksSekvensId = maksSekvensId,
            sisteHendelseTidspunkt = forløp.flatMap { it.hendelser }.maxOfOrNull { it.tekniskTid.instant },
            antallBehandlinger = forløp.size,
            behandlingerMedFlereUtfall = forløp.count { behandlingsforløp ->
                behandlingsforløp.hendelser.count { it.behandlingStatus in AVGANGSSTATUSER } > 1
            },
        ),
        perioder = perioder.map { periode ->
            val avsluttedeBehandlinger = forløp.mapNotNull { behandlingsforløp ->
                behandlingsforløp.førsteUtfall()
                    ?.takeIf { it.dato() in periode.fraOgMed..periode.tilOgMed }
                    ?.let {
                        AvsluttetBehandling(
                            forløp = behandlingsforløp,
                            utfall = it,
                            begrunnelser = it.resultatBegrunnelse.tilNormaliserteBegrunnelser(),
                        )
                    }
            }
            val antall = forløp.mapNotNull { behandlingsforløp ->
                val første = behandlingsforløp.hendelser.first()
                if (første.registrertDato() !in periode.fraOgMed..periode.tilOgMed) return@mapNotNull null
                Tilgangsnøkkel(
                    kategori = behandlingsforløp.kategori,
                    sakYtelse = behandlingsforløp.sakYtelse,
                    behandlingAarsak = behandlingsforløp.behandlingAarsak,
                )
            }.groupingBy { it }.eachCount().map { (nøkkel, antall) ->
                SakStatistikkAntall(
                    behandlingskategori = nøkkel.kategori,
                    sakYtelse = nøkkel.sakYtelse,
                    behandlingAarsak = nøkkel.behandlingAarsak,
                    antall = antall,
                )
            }

            val utfall = avsluttedeBehandlinger.map {
                Utfallnøkkel(
                    kategori = it.forløp.kategori,
                    sakYtelse = it.forløp.sakYtelse,
                    status = it.utfall.behandlingStatus,
                    resultat = it.normalisertResultat(),
                )
            }.groupingBy { it }.eachCount().map { (nøkkel, antall) ->
                SakStatistikkUtfall(
                    behandlingskategori = nøkkel.kategori,
                    sakYtelse = nøkkel.sakYtelse,
                    status = nøkkel.status,
                    resultat = nøkkel.resultat,
                    antall = antall,
                )
            }

            val åpneBehandlinger = forløp.mapNotNull { behandlingsforløp ->
                val sisteStatus = behandlingsforløp.hendelser.lastOrNull {
                    !it.dato().isAfter(periode.tilOgMed)
                } ?: return@mapNotNull null
                if (sisteStatus.behandlingStatus in TERMINALE_STATUSER) return@mapNotNull null
                ÅpenBehandling(behandlingsforløp, sisteStatus)
            }

            val beholdning = åpneBehandlinger.map {
                Statusnøkkel(
                    kategori = it.forløp.kategori,
                    sakYtelse = it.forløp.sakYtelse,
                    status = it.sisteStatus.behandlingStatus,
                )
            }.groupingBy { it }.eachCount().map { (nøkkel, antall) ->
                SakStatistikkStatus(
                    behandlingskategori = nøkkel.kategori,
                    sakYtelse = nøkkel.sakYtelse,
                    status = nøkkel.status,
                    antall = antall,
                )
            }

            val beholdningsalder = åpneBehandlinger.flatMap { åpen ->
                val behandlingensAlder = dagerMellom(
                    åpen.forløp.hendelser.first().mottattDato(),
                    periode.tilOgMed,
                )
                val tidINåværendeStatus = dagerMellom(åpen.sisteStatus.dato(), periode.tilOgMed)
                listOf(
                    Beholdningsaldernøkkel(
                        kategori = åpen.forløp.kategori,
                        sakYtelse = åpen.forløp.sakYtelse,
                        status = åpen.sisteStatus.behandlingStatus,
                        måling = Beholdningsaldersmåling.BEHANDLINGENS_ALDER,
                        intervall = behandlingensAlder.tilAldersintervall(),
                    ),
                    Beholdningsaldernøkkel(
                        kategori = åpen.forløp.kategori,
                        sakYtelse = åpen.forløp.sakYtelse,
                        status = åpen.sisteStatus.behandlingStatus,
                        måling = Beholdningsaldersmåling.TID_I_NÅVÆRENDE_STATUS,
                        intervall = tidINåværendeStatus.tilAldersintervall(),
                    ),
                )
            }.groupingBy { it }.eachCount().map { (aldersnøkkel, antall) ->
                SakStatistikkBeholdningsalder(
                    behandlingskategori = aldersnøkkel.kategori,
                    sakYtelse = aldersnøkkel.sakYtelse,
                    status = aldersnøkkel.status,
                    måling = aldersnøkkel.måling,
                    intervall = aldersnøkkel.intervall,
                    antall = antall,
                )
            }

            val omarbeid = avsluttedeBehandlinger.mapNotNull { avsluttet ->
                val behandlingsforløp = avsluttet.forløp
                if (behandlingsforløp.kategori !in KATEGORIER_MED_UNDERKJENNINGSSTATISTIKK) {
                    return@mapNotNull null
                }
                val varigheterEtterUnderkjenning = behandlingsforløp.behandlingstider()
                    .filter { it.nøkkel.måling == Behandlingstidsmåling.TID_ETTER_UNDERKJENNING }
                Omarbeidsobservasjon(
                    nøkkel = Omarbeidsnøkkel(
                        kategori = behandlingsforløp.kategori,
                        sakYtelse = behandlingsforløp.sakYtelse,
                    ),
                    antallUnderkjenninger = behandlingsforløp.hendelser.count {
                        it.behandlingStatus == "UNDERKJENT"
                    },
                    tidEtterUnderkjenningMillis = varigheterEtterUnderkjenning
                        .takeIf { it.isNotEmpty() }
                        ?.sumOf { it.varighetMillis },
                )
            }.groupBy { it.nøkkel }.map { (omarbeidsnøkkel, observasjoner) ->
                SakStatistikkOmarbeid(
                    behandlingskategori = omarbeidsnøkkel.kategori,
                    sakYtelse = omarbeidsnøkkel.sakYtelse,
                    behandlingerMedUtfall = observasjoner.size,
                    utenUnderkjenning = observasjoner.count { it.antallUnderkjenninger == 0 },
                    medEnUnderkjenning = observasjoner.count { it.antallUnderkjenninger == 1 },
                    medFlereUnderkjenninger = observasjoner.count { it.antallUnderkjenninger > 1 },
                    medianTidEtterUnderkjenningMillis = observasjoner.mapNotNull {
                        it.tidEtterUnderkjenningMillis
                    }.sorted().medianEllerNull(),
                )
            }

            SakStatistikkPeriode(
                fraOgMed = periode.fraOgMed,
                tilOgMed = periode.tilOgMed,
                antall = antall.sortedBy {
                    "${it.behandlingskategori}-${it.sakYtelse}-${it.behandlingAarsak}"
                },
                utfall = utfall.sortedBy {
                    "${it.behandlingskategori}-${it.sakYtelse}-${it.status}-${it.resultat}"
                },
                beholdning = beholdning.sortedBy {
                    "${it.behandlingskategori}-${it.sakYtelse}-${it.status}"
                },
                behandlingstid = behandlingstider
                    .filter { it.avsluttetDato in periode.fraOgMed..periode.tilOgMed }
                    .groupBy { it.nøkkel }
                    .map { (tidsnøkkel, målinger) ->
                        målinger.map { it.varighetMillis }.tilBehandlingstid(tidsnøkkel)
                    }
                    .sortedBy { "${it.behandlingskategori}-${it.sakYtelse}-${it.måling}" },
                beholdningsalder = beholdningsalder.sortedBy {
                    "${it.behandlingskategori}-${it.sakYtelse}-${it.status}-${it.måling}-${it.intervall}"
                },
                omarbeid = omarbeid.sortedBy { "${it.behandlingskategori}-${it.sakYtelse}" },
                avslagsgrunner = avsluttedeBehandlinger.tilAvslagsfordelinger(),
                opphørsgrunner = avsluttedeBehandlinger.tilOpphørsfordelinger(),
                klageavvisningsgrunner = avsluttedeBehandlinger.tilKlageavvisningsfordelinger(),
                klagehjemler = avsluttedeBehandlinger.tilKlagehjemmelfordelinger(),
                klageomgjøringsgrunner = avsluttedeBehandlinger.tilKlageomgjøringsfordelinger(),
            )
        },
        kohorter = perioder.flatMap { periode ->
            forløp.filter {
                it.hendelser.first().mottattDato() in periode.fraOgMed..periode.tilOgMed
            }.groupBy {
                Kohortnøkkel(it.kategori, it.sakYtelse)
            }.map { (kohortnøkkel, behandlinger) ->
                SakStatistikkKohort(
                    fraOgMed = periode.fraOgMed,
                    tilOgMed = periode.tilOgMed,
                    behandlingskategori = kohortnøkkel.kategori,
                    sakYtelse = kohortnøkkel.sakYtelse,
                    antallStartet = behandlinger.size,
                    ferdigInnen30Dager = behandlinger.tilKohortfrist(30L, nøkkel.tilOgMed),
                    ferdigInnen60Dager = behandlinger.tilKohortfrist(60L, nøkkel.tilOgMed),
                    ferdigInnen90Dager = behandlinger.tilKohortfrist(90L, nøkkel.tilOgMed),
                    åpneVedTilOgMed = behandlinger.count {
                        it.hendelser.last().behandlingStatus !in TERMINALE_STATUSER
                    },
                )
            }
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

            fra.behandlingStatus in setOf("REGISTRERT", "UNDER_BEHANDLING") &&
                til.behandlingStatus == "TIL_ATTESTERING" ->
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
            varighetMillis = varighetMellom(første.mottattTid, siste.funksjonellTid),
        )
    }
    return resultat
}

private fun varighetMellom(
    fra: SakStatistikkVisningsrad,
    til: SakStatistikkVisningsrad,
): Long = varighetMellom(fra.funksjonellTid, til.funksjonellTid)

private fun varighetMellom(
    fra: Tidspunkt,
    til: Tidspunkt,
): Long = Duration.between(fra.instant, til.instant).toMillis().coerceAtLeast(0)

private fun Behandlingsforløp.førsteUtfall(): SakStatistikkVisningsrad? =
    hendelser.firstOrNull { it.behandlingStatus in AVGANGSSTATUSER }

private fun List<AvsluttetBehandling>.tilAvslagsfordelinger(): List<SakStatistikkAvslagsfordeling> {
    val kjenteGrunner = Avslagsgrunn.entries.associateBy { it.name }
    val kjenteKoder = kjenteGrunner.keys + FOR_TIDLIG_SØKNAD
    return filter {
        it.forløp.kategori == SakStatistikkKategori.SØKNAD &&
            it.normalisertResultat() == AVSLAG
    }.groupBy { it.forløp.sakYtelse }.map { (sakYtelse, behandlinger) ->
        SakStatistikkAvslagsfordeling(
            sakYtelse = sakYtelse,
            antallAvslag = behandlinger.size,
            antallUtenBegrunnelse = behandlinger.count { it.begrunnelser.isEmpty() },
            antallMedUkjentBegrunnelse = behandlinger.count {
                it.begrunnelser.any { kode -> kode !in kjenteKoder }
            },
            grunner = kjenteKoder.mapNotNull { kode ->
                val antall = behandlinger.count { kode in it.begrunnelser }
                antall.takeIf { it > 0 }?.let {
                    SakStatistikkAvslagsgrunn(
                        kode = kode,
                        paragrafer = kjenteGrunner[kode]?.paragrafer.orEmpty().map {
                            SakStatistikkParagraf(SakStatistikkLov.SU, it)
                        },
                        antallBehandlinger = antall,
                    )
                }
            }.sortedBy { it.kode },
        )
    }.sortedBy { it.sakYtelse }
}

private fun List<AvsluttetBehandling>.tilOpphørsfordelinger(): List<SakStatistikkOpphørsfordeling> {
    val kjenteGrunner = Opphørsgrunn.entries.associateBy { it.name }
    return filter {
        it.forløp.kategori == SakStatistikkKategori.REVURDERING &&
            it.normalisertResultat() == OPPHØRT
    }.groupBy { it.forløp.sakYtelse }.map { (sakYtelse, behandlinger) ->
        SakStatistikkOpphørsfordeling(
            sakYtelse = sakYtelse,
            antallOpphør = behandlinger.size,
            antallUtenBegrunnelse = behandlinger.count { it.begrunnelser.isEmpty() },
            antallMedUkjentBegrunnelse = behandlinger.count {
                it.begrunnelser.any { kode -> kode !in kjenteGrunner }
            },
            grunner = kjenteGrunner.mapNotNull { (kode, grunn) ->
                val antall = behandlinger.count { kode in it.begrunnelser }
                antall.takeIf { it > 0 }?.let {
                    SakStatistikkOpphørsgrunn(
                        kode = kode,
                        paragrafer = grunn.getParagrafer().map {
                            SakStatistikkParagraf(SakStatistikkLov.SU, it)
                        },
                        antallBehandlinger = antall,
                    )
                }
            }.sortedBy { it.kode },
        )
    }.sortedBy { it.sakYtelse }
}

private fun List<AvsluttetBehandling>.tilKlageavvisningsfordelinger(): List<SakStatistikkKlageavvisningsfordeling> {
    val kjenteGrunner = setOf(
        "IKKE_INNENFOR_FRISTEN",
        "KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET",
        "IKKE_UNDERSKREVET",
    )
    return filter {
        it.forløp.kategori == SakStatistikkKategori.KLAGE &&
            it.normalisertResultat() == AVVIST
    }.groupBy { it.forløp.sakYtelse }.map { (sakYtelse, behandlinger) ->
        SakStatistikkKlageavvisningsfordeling(
            sakYtelse = sakYtelse,
            antallAvvisteKlager = behandlinger.size,
            antallUtenBegrunnelse = behandlinger.count { it.begrunnelser.isEmpty() },
            antallMedUkjentBegrunnelse = behandlinger.count {
                it.begrunnelser.any { kode -> kode !in kjenteGrunner }
            },
            grunner = kjenteGrunner.mapNotNull { kode ->
                val antall = behandlinger.count { kode in it.begrunnelser }
                antall.takeIf { it > 0 }?.let {
                    SakStatistikkKlageavvisningsgrunn(kode, antall)
                }
            }.sortedBy { it.kode },
        )
    }.sortedBy { it.sakYtelse }
}

private fun List<AvsluttetBehandling>.tilKlagehjemmelfordelinger(): List<SakStatistikkKlagehjemmelfordeling> {
    val kjenteHjemler = Hjemmel.entries.associateBy { it.name }
    return filter {
        val resultat = it.normalisertResultat()
        it.forløp.kategori == SakStatistikkKategori.KLAGE &&
            (
                resultat == OPPRETTHOLDT ||
                    (resultat == DELVIS_OMGJØRING && it.utfall.behandlingStatus == "OVERSENDT")
                )
    }.groupBy {
        YtelseOgResultat(it.forløp.sakYtelse, requireNotNull(it.normalisertResultat()))
    }.map { (nøkkel, behandlinger) ->
        SakStatistikkKlagehjemmelfordeling(
            sakYtelse = nøkkel.sakYtelse,
            resultat = nøkkel.resultat,
            antallKlager = behandlinger.size,
            antallUtenHjemmel = behandlinger.count { it.begrunnelser.isEmpty() },
            antallMedUkjentHjemmel = behandlinger.count {
                it.begrunnelser.any { kode -> kode !in kjenteHjemler }
            },
            hjemler = kjenteHjemler.mapNotNull { (kode, hjemmel) ->
                val antall = behandlinger.count { kode in it.begrunnelser }
                antall.takeIf { it > 0 }?.let {
                    SakStatistikkKlagehjemmel(
                        kode = kode,
                        lov = if (kode.startsWith("SU_")) {
                            SakStatistikkLov.SU
                        } else {
                            SakStatistikkLov.FVL
                        },
                        paragraf = hjemmel.paragrafnummer,
                        antallBehandlinger = antall,
                    )
                }
            }.sortedBy { it.kode },
        )
    }.sortedBy { "${it.sakYtelse}-${it.resultat}" }
}

private fun List<AvsluttetBehandling>.tilKlageomgjøringsfordelinger(): List<SakStatistikkKlageomgjøringsfordeling> {
    val kjenteGrunner = VurderingerTilKlage.Vedtaksvurdering.Årsak.entries.map { it.name }.toSet()
    return filter {
        val resultat = it.normalisertResultat()
        it.forløp.kategori == SakStatistikkKategori.KLAGE &&
            (
                resultat == OMGJORT ||
                    (resultat == DELVIS_OMGJØRING && it.utfall.behandlingStatus == "IVERKSATT")
                )
    }.groupBy {
        YtelseOgResultat(it.forløp.sakYtelse, requireNotNull(it.normalisertResultat()))
    }.map { (nøkkel, behandlinger) ->
        SakStatistikkKlageomgjøringsfordeling(
            sakYtelse = nøkkel.sakYtelse,
            resultat = nøkkel.resultat,
            antallKlager = behandlinger.size,
            antallUtenBegrunnelse = behandlinger.count { it.begrunnelser.isEmpty() },
            antallMedUkjentBegrunnelse = behandlinger.count {
                it.begrunnelser.any { kode -> kode !in kjenteGrunner }
            },
            grunner = kjenteGrunner.mapNotNull { kode ->
                val antall = behandlinger.count { kode in it.begrunnelser }
                antall.takeIf { it > 0 }?.let {
                    SakStatistikkKlageomgjøringsgrunn(kode, antall)
                }
            }.sortedBy { it.kode },
        )
    }.sortedBy { "${it.sakYtelse}-${it.resultat}" }
}

private fun String?.tilNormaliserteBegrunnelser(): Set<String> =
    this?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.map {
            when (it) {
                FOR_TIDLIG_SØKNAD_RÅVERDI -> FOR_TIDLIG_SØKNAD
                else -> it.uppercase()
            }
        }
        ?.toSet()
        .orEmpty()

private fun AvsluttetBehandling.normalisertResultat(): String? = when {
    utfall.behandlingResultat == AVSLÅTT -> AVSLAG
    utfall.behandlingResultat == HISTORISK_OPPHØRT_REVURDERING -> OPPHØRT
    utfall.behandlingResultat == HISTORISK_FEILREGISTRERT ||
        utfall.behandlingResultat == HISTORISK_FEILREGISTRERT_VERSALER -> BORTFALT
    forløp.kategori == SakStatistikkKategori.KLAGE && utfall.behandlingResultat == AVSLAG -> AVVIST
    else -> utfall.behandlingResultat
}

private fun List<Behandlingsforløp>.tilKohortfrist(
    dager: Long,
    datagrunnlagTilOgMed: LocalDate,
): SakStatistikkKohortfrist {
    val modnetGrunnlag = filter {
        !it.hendelser.first().mottattDato().plusDays(dager).isAfter(datagrunnlagTilOgMed)
    }
    return SakStatistikkKohortfrist(
        grunnlag = modnetGrunnlag.size,
        ferdige = modnetGrunnlag.count { behandling ->
            behandling.førsteUtfall()?.dato()?.let {
                !it.isAfter(behandling.hendelser.first().mottattDato().plusDays(dager))
            } == true
        },
    )
}

private fun dagerMellom(fraOgMed: LocalDate, tilOgMed: LocalDate): Long =
    ChronoUnit.DAYS.between(fraOgMed, tilOgMed).coerceAtLeast(0)

private fun Long.tilAldersintervall(): Beholdningsaldersintervall = when (this) {
    in 0L..7L -> Beholdningsaldersintervall.DAGER_0_7
    in 8L..30L -> Beholdningsaldersintervall.DAGER_8_30
    in 31L..60L -> Beholdningsaldersintervall.DAGER_31_60
    in 61L..90L -> Beholdningsaldersintervall.DAGER_61_90
    else -> Beholdningsaldersintervall.OVER_90_DAGER
}

private fun List<Long>.tilBehandlingstid(nøkkel: Behandlingstidsnøkkel): SakStatistikkBehandlingstid {
    val sortert = sorted()
    return SakStatistikkBehandlingstid(
        behandlingskategori = nøkkel.kategori,
        sakYtelse = nøkkel.sakYtelse,
        måling = nøkkel.måling,
        antall = size,
        gjennomsnittMillis = average().toLong(),
        medianMillis = sortert.persentil(0.5),
    )
}

private fun List<Long>.persentil(persentil: Double): Long {
    val indeks = (ceil(size * persentil).toInt() - 1).coerceIn(indices)
    return this[indeks]
}

private fun List<Long>.medianEllerNull(): Long? = takeIf { it.isNotEmpty() }?.persentil(0.5)

private fun SakStatistikkVisningsrad.dato(): LocalDate = funksjonellTid.toLocalDate(zoneIdOslo)

private fun SakStatistikkVisningsrad.mottattDato(): LocalDate = mottattTid.toLocalDate(zoneIdOslo)

private fun SakStatistikkVisningsrad.registrertDato(): LocalDate = registrertTid.toLocalDate(zoneIdOslo)

private fun String?.tilVisningsårsak(): String? = when (this) {
    OMGJØRING_ETTER_AVVIST -> OMGJØRING_ETTER_AVSLAG
    else -> this
}

private fun SakStatistikkVisningsrad.tilKategori(): SakStatistikkKategori {
    return when (behandlingType) {
        "SOKNAD" -> SakStatistikkKategori.SØKNAD
        "KLAGE" -> SakStatistikkKategori.KLAGE
        "TILBAKEKREVING" -> SakStatistikkKategori.TILBAKEKREVING
        "REVURDERING" -> when {
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

private fun StønadStatistikkBestandsendringRad.tilJson() = StønadStatistikkBestandsendring(
    stønadstype = stønadstype,
    nye = nye,
    videreført = videreført,
    utgått = utgått,
    endretStønadsklassifisering = endretStønadsklassifisering,
)

private fun YearMonth.månederTilOgMed(tilOgMed: YearMonth): List<YearMonth> {
    val måneder = mutableListOf<YearMonth>()
    var måned = this
    while (!måned.isAfter(tilOgMed)) {
        måneder += måned
        måned = måned.plusMonths(1)
    }
    return måneder
}
