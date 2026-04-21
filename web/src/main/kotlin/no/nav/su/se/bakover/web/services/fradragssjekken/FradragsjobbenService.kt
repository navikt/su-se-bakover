package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.collections.chunked
import kotlin.collections.plusAssign

interface FradragsjobbenService {
    fun sjekkLøpendeSakerForFradragIEksterneSystemer(dryRun: Boolean = false)
    fun kjørFradragssjekkForMånedMedValidering(måned: Måned, dryRun: Boolean = false)
    fun validerKjøringForMåned(måned: Måned): FradragsSjekkFeil?
    fun harOrdinaerKjoringForMåned(måned: Måned): Boolean
}

private const val INTERN_SAK_BATCH_STORRELSE = 500
private const val EKSTERN_OPPSLAG_BATCH_STORRELSE = 50

internal class FradragsjobbenServiceImpl(
    private val aapKlient: AapApiInternClient,
    private val pesysKlient: PesysClient,
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val utbetalingsRepo: UtbetalingRepo,
    private val satsFactory: SatsFactory,
    private val fradragssjekkRunPostgresRepo: FradragssjekkRunPostgresRepo,
    private val clock: Clock,
) : FradragsjobbenService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val eksterneOppslagService = EksterneFradragsoppslagService(
        aapKlient = aapKlient,
        pesysKlient = pesysKlient,
        log = log,
    )

    /**
     * 1. Finn løpende saker
     * 2. Hent fradrag for sakene basert på type sak
     * SU ufør: Sjekk UFØR OG AAP på bruker og UFØR og AAP på EPS hvis EPS er under 67, men kun AP for EPS dersom EPS er over 67
     *
     * SU alder: sjekk AP på bruker og AP på EPS dersom EPS er over 67, men UFØR og AAP dersom EPS er under 67
     * 3. Lag oppgave hvis fradrag er ulikt eller ikke fantes på brukes
     *
     */
    override fun sjekkLøpendeSakerForFradragIEksterneSystemer(dryRun: Boolean) {
        kjørFradragssjekkForMånedMedValidering(måned = Måned.now(clock), dryRun = dryRun)
    }

    override fun kjørFradragssjekkForMånedMedValidering(
        måned: Måned,
        dryRun: Boolean,
    ) {
        requireGyldigKjøringForMåned(måned = måned)
        kjørFradragssjekkForMåned(måned = måned, dryRun = dryRun)
    }

    override fun harOrdinaerKjoringForMåned(måned: Måned): Boolean {
        return fradragssjekkRunPostgresRepo.harOrdinaerKjoringForMåned(måned)
    }

    override fun validerKjøringForMåned(
        måned: Måned,
    ): FradragsSjekkFeil? {
        val inneværendeMåned = Måned.now(clock)
        if (måned < inneværendeMåned) {
            return FradragsSjekkFeil.DatoErTilbakeITid
        }
        if (måned > inneværendeMåned) {
            return FradragsSjekkFeil.DatoErFremITid
        }

        if (harOrdinaerKjoringForMåned(måned)) {
            return FradragsSjekkFeil.AlleredeKjørtForMåned
        }
        return null
    }

    private fun requireGyldigKjøringForMåned(
        måned: Måned,
    ) {
        when (validerKjøringForMåned(måned = måned)) {
            FradragsSjekkFeil.AlleredeKjørtForMåned -> throw IllegalStateException("Fradragssjekk er allerede kjørt for måned $måned")
            FradragsSjekkFeil.DatoErFremITid -> throw IllegalArgumentException("Fradragssjekk kan ikke kjøres for fremtidig måned $måned")
            FradragsSjekkFeil.DatoErTilbakeITid -> throw IllegalArgumentException("Fradragssjekk kan ikke kjøres for tidligere måned $måned")
            null -> Unit
        }
    }

    private fun kjørFradragssjekkForMåned(
        måned: Måned,
        dryRun: Boolean,
    ) {
        val alleSaker = hentAlleSaker()
        val totaltAntallInterneBatcher = antallBatcher(alleSaker.size, INTERN_SAK_BATCH_STORRELSE)
        var internBatchNummer = 0
        var vurderteSaker = 0

        val kjoringId = UUID.randomUUID()
        // TODO:
        val dato = LocalDate.now(clock)

        log.info(
            "Fradragssjekk: Starter bygging av sjekkplaner for måned {}. Antall saker: {}, interne batcher: {} id: $kjoringId",
            måned,
            alleSaker.size,
            totaltAntallInterneBatcher,
        )

        val oppsummering = alleSaker
            .chunked(INTERN_SAK_BATCH_STORRELSE)
            .flatMap { sakerPerBatch ->
                internBatchNummer++
                vurderteSaker += sakerPerBatch.size
                val løpendeSaker = hentSakerMedLøpendeUtbetalingForMåned(sakerPerBatch, måned)
                val sjekkpunktsSakser: List<SjekkgrunnlagForSak> = lagSjekkgrunnlagForLøpendeSaker(løpendeSaker, måned).also { batchSjekkgrunnlag ->
                    log.info(
                        "Fradragssjekk: Intern batch {}/{} ferdig for måned {}. Saker i batch: {}, vurdert hittil: {}, løpende saker i batch: {}, sjekkgrunnlag i batch: {}",
                        internBatchNummer,
                        totaltAntallInterneBatcher,
                        måned,
                        sakerPerBatch.size,
                        vurderteSaker,
                        løpendeSaker.size,
                        batchSjekkgrunnlag.size,
                    )
                }
                slåOppFradagssjekkpunkter(
                    sjekkpunktsSaker = sjekkpunktsSakser,
                    måned = måned,
                    dryRun = dryRun,
                    kjøringId = kjoringId,
                )
            }

        lagreTotalKjøring(
            måned = måned,
            dryRun = dryRun,
            kjøringId = kjoringId,
            saksresultater = oppsummering,
        )
    }

    private fun slåOppFradagssjekkpunkter(sjekkpunktsSaker: List<SjekkgrunnlagForSak>, måned: Måned, dryRun: Boolean, kjøringId: UUID): List<FradragssjekkSakResultat> {
        val startet = clock.instant()
        val saksresultater = mutableListOf<FradragssjekkSakResultat>()
        val totaltAntallEksterneBatcher = antallBatcher(sjekkpunktsSaker.size, EKSTERN_OPPSLAG_BATCH_STORRELSE)
        var eksternBatchNummer = 0

        log.info(
            "start:$startet ->starter for måned $måned med kjøring {}. dryRun={}. Antall sjekkgrunnlag: {}, eksterne batcher: {}",
            kjøringId,
            dryRun,
            sjekkpunktsSaker.size,
            totaltAntallEksterneBatcher,
        )
        sjekkpunktsSaker
            .chunked(EKSTERN_OPPSLAG_BATCH_STORRELSE)
            .map { sjekkgrunnlagBatch ->
                eksternBatchNummer++
                saksresultater += prosesserSjekkplanBatch(
                    sjekkgrunnlag = sjekkgrunnlagBatch,
                    måned = måned,
                    dryRun = dryRun,
                )

                log.debug(
                    "Fradragssjekk: Ekstern batch {}/{} ferdig for kjøring {} og måned {}. Batchstørrelse: {}, saksresultater hittil: {}",
                    eksternBatchNummer,
                    totaltAntallEksterneBatcher,
                    kjøringId,
                    måned,
                    sjekkgrunnlagBatch.size,
                    saksresultater.size,
                )
            }

        fradragssjekkRunPostgresRepo.lagreSaksresultater(saksresultater, måned, kjøringId)
        return saksresultater
    }

    // TODO: må ha en try catch som lagrer ved exceptions også slik at en kjøring alltid får en status
    private fun lagreTotalKjøring(
        måned: Måned,
        dryRun: Boolean,
        kjøringId: UUID,
        saksresultater: List<FradragssjekkSakResultat>,
    ) {
        try {
            val aggregertOppsummering = lagFradragssjekkOppsummering(saksresultater)
            val kjoring = FradragssjekkKjøring(
                id = kjøringId,
                dato = LocalDate.now(clock),
                dryRun = dryRun,
                status = FradragssjekkKjøringStatus.FULLFØRT,
                opprettet = clock.instant(),
                ferdigstilt = clock.instant(), // hva skal vi med denne?
            )
            fradragssjekkRunPostgresRepo.lagreKjoring(kjoring, aggregertOppsummering)
            loggOppsummering(kjoring, måned, saksresultater)
        } catch (e: Exception) {
            fradragssjekkRunPostgresRepo.lagreKjoring(
                FradragssjekkKjøring(
                    id = kjøringId,
                    dato = LocalDate.now(clock),
                    dryRun = dryRun,
                    status = FradragssjekkKjøringStatus.FEILET,
                    opprettet = clock.instant(),
                    ferdigstilt = clock.instant(),
                    feilmelding = e.message,
                ),
            )
            throw e
        }
    }

    private fun antallBatcher(
        total: Int,
        batchStørrelse: Int,
    ): Int {
        if (total == 0) return 0
        return (total + batchStørrelse - 1) / batchStørrelse
    }

    private fun loggOppsummering(
        kjoring: FradragssjekkKjøring,
        måned: Måned,
        saksresultater: List<FradragssjekkSakResultat>,
    ) {
        val sakerMedObservasjoner = saksresultater.filter { resultat ->
            when (resultat) {
                is FradragssjekkSakResultat.KunObservasjon -> resultat.observasjoner.isNotEmpty()
                is FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun -> resultat.observasjoner.isNotEmpty()
                is FradragssjekkSakResultat.OppgaveOpprettet -> resultat.observasjoner.isNotEmpty()
                is FradragssjekkSakResultat.OppgaveopprettelseFeilet -> resultat.observasjoner.isNotEmpty()
                is FradragssjekkSakResultat.IngenAvvik -> false
                is FradragssjekkSakResultat.EksternFeil -> false
                is FradragssjekkSakResultat.Invariantbrudd -> false
            }
        }

        val mislykkedeOppgaveopprettelser =
            saksresultater.filterIsInstance<FradragssjekkSakResultat.OppgaveopprettelseFeilet>()

        val dryRunOppgaver =
            saksresultater.count { it is FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun }

        val sakerMedOppgaveavvik =
            saksresultater.count {
                it is FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun ||
                    it is FradragssjekkSakResultat.OppgaveOpprettet ||
                    it is FradragssjekkSakResultat.OppgaveopprettelseFeilet
            }

        val opprettedeOppgaver =
            saksresultater.count { it is FradragssjekkSakResultat.OppgaveOpprettet }

        val eksterneFeil =
            saksresultater.count { it is FradragssjekkSakResultat.EksternFeil }

        val invariantbrudd =
            saksresultater.count { it is FradragssjekkSakResultat.Invariantbrudd }

        log.info(
            "Fradragssjekk fullført for kjøring {} og måned {}. Vurderte saker: {}, saker med avvik: {}, opprettede oppgaver: {}, dry run-oppgaver: {}, hoppet over pga eksterne feil: {}, observasjoner: {}, invariantbrudd: {}",
            kjoring.id,
            måned,
            saksresultater.size,
            sakerMedOppgaveavvik,
            opprettedeOppgaver,
            dryRunOppgaver,
            eksterneFeil,
            sakerMedObservasjoner.size,
            invariantbrudd,
        )
    }

    private fun hentAlleSaker() = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()

    internal fun hentSakerMedLøpendeUtbetalingForMåned(
        saker: List<SakInfo>,
        måned: Måned,
    ): List<LøpendeSakForMåned> {
        if (saker.isEmpty()) return emptyList()

        val utbetalingerPerSak = utbetalingsRepo.hentOversendteUtbetalingerForSakIder(
            saker.map { it.sakId },
        )

        return saker.mapNotNull { sak ->
            utbetalingerPerSak[sak.sakId]
                ?.hentGjeldendeUtbetaling(måned.fraOgMed)
                ?.fold(
                    ifLeft = { null },
                    ifRight = {
                        when (it) {
                            is UtbetalingslinjePåTidslinje.Ny, is UtbetalingslinjePåTidslinje.Reaktivering -> LøpendeSakForMåned(sak = sak, gjeldendeMånedsutbetaling = it.beløp)
                            is UtbetalingslinjePåTidslinje.Opphør -> null
                            is UtbetalingslinjePåTidslinje.Stans -> null
                        }
                    },
                )
        }
    }

    private fun lagSjekkgrunnlagForLøpendeSaker(
        løpendeSaker: List<LøpendeSakForMåned>,
        måned: Måned,
    ): List<SjekkgrunnlagForSak> {
        return løpendeSaker.mapNotNull { løpendeSak ->
            hentGjeldendeVedtaksdataForSak(løpendeSak.sak, måned)?.let { gjeldendeVedtaksdata ->
                lagSjekkplanForSak(
                    sak = løpendeSak.sak,
                    gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                    måned = måned,
                )?.let { sjekkplan ->
                    SjekkgrunnlagForSak(
                        sjekkplan = sjekkplan,
                        gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                        gjeldendeMånedsutbetaling = løpendeSak.gjeldendeMånedsutbetaling,
                    )
                }
            }
        }
    }

    private fun hentGjeldendeVedtaksdataForSak(
        sak: SakInfo,
        måned: Måned,
    ): GjeldendeVedtaksdata? {
        return sakService.hentGjeldendeVedtaksdata(sak.sakId, måned).fold(
            ifLeft = {
                log.warn("Fradragssjekk: Klarte ikke hente gjeldende vedtaksdata for sak {}", sak.sakId)
                null
            },
            ifRight = { it },
        )
    }

    private fun prosesserSjekkplanBatch(
        sjekkgrunnlag: List<SjekkgrunnlagForSak>,
        måned: Måned,
        dryRun: Boolean,
    ): List<FradragssjekkSakResultat> {
        if (sjekkgrunnlag.isEmpty()) return emptyList()

        val oppslagsresultater = eksterneOppslagService.hentOppslagsresultaterForYtelser(
            sjekkgrunnlag.map { it.sjekkplan },
            måned,
        )
        return sjekkgrunnlag.map { grunnlag ->
            prosesserSjekkplan(
                sjekkgrunnlag = grunnlag,
                måned = måned,
                oppslagsresultater = oppslagsresultater,
                dryRun = dryRun,
            )
        }
    }

    private fun prosesserSjekkplan(
        sjekkgrunnlag: SjekkgrunnlagForSak,
        måned: Måned,
        oppslagsresultater: EksterneOppslagsresultater,
        dryRun: Boolean,
    ): FradragssjekkSakResultat {
        val sjekkplan = sjekkgrunnlag.sjekkplan
        return try {
            val eksterneFeil = finnEksterneFeilForSak(sjekkplan, oppslagsresultater)
            if (eksterneFeil.isNotEmpty()) {
                return FradragssjekkSakResultat.EksternFeil(
                    sakId = sjekkplan.sak.sakId,
                    sakstype = sjekkplan.sak.type,
                    sjekkPunkter = sjekkplan.sjekkpunkter,
                    eksterneFeil = eksterneFeil,
                )
            }

            when (
                val avviksvurdering = finnAvvikForSak(
                    sjekkgrunnlag = sjekkgrunnlag,
                    måned = måned,
                    oppslagsresultater = oppslagsresultater,
                    satsFactory = satsFactory,
                    clock = clock,
                )
            ) {
                Avviksvurdering.IngenDiff -> FradragssjekkSakResultat.IngenAvvik(
                    sakId = sjekkplan.sak.sakId,
                    sakstype = sjekkplan.sak.type,
                    sjekkPunkter = sjekkplan.sjekkpunkter,
                )

                is Avviksvurdering.Diff -> {
                    val (oppgaveAvvik, observasjonsAvvik) = avviksvurdering.avvik.partitionTyped<Fradragsfunn.Oppgaveavvik, Fradragsfunn.Observasjon>()

                    if (oppgaveAvvik.isEmpty()) {
                        FradragssjekkSakResultat.KunObservasjon(
                            sakId = sjekkplan.sak.sakId,
                            sakstype = sjekkplan.sak.type,
                            sjekkPunkter = sjekkplan.sjekkpunkter,
                            observasjoner = observasjonsAvvik,
                        )
                    } else if (dryRun) {
                        FradragssjekkSakResultat.OppgaveIkkeOpprettetDryRun(
                            sakId = sjekkplan.sak.sakId,
                            sakstype = sjekkplan.sak.type,
                            sjekkPunkter = sjekkplan.sjekkpunkter,
                            oppgaveAvvik = oppgaveAvvik,
                            observasjoner = observasjonsAvvik,
                        )
                    } else {
                        when (val oppgaveResultat = opprettOppgaveForFradrag(sjekkplan.sak, måned, oppgaveAvvik)) {
                            is OppgaveopprettelseResultat.Opprettet -> {
                                FradragssjekkSakResultat.OppgaveOpprettet(
                                    sakId = sjekkplan.sak.sakId,
                                    sakstype = sjekkplan.sak.type,
                                    sjekkPunkter = sjekkplan.sjekkpunkter,
                                    oppgaveAvvik = oppgaveAvvik,
                                    observasjoner = observasjonsAvvik,
                                    opprettetOppgave = oppgaveResultat,
                                )
                            }

                            is OppgaveopprettelseResultat.Feilet -> {
                                FradragssjekkSakResultat.OppgaveopprettelseFeilet(
                                    sakId = sjekkplan.sak.sakId,
                                    sakstype = sjekkplan.sak.type,
                                    sjekkPunkter = sjekkplan.sjekkpunkter,
                                    oppgaveAvvik = oppgaveAvvik,
                                    observasjoner = observasjonsAvvik,
                                    mislykketOppgaveopprettelse = oppgaveResultat.feil,
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: ManglerLagretOppslagsresultatException) {
            log.error("Fradragssjekk: feil for sak {}. {}", sjekkplan.sak.sakId, e.message, e)
            lagInvariantbruddResultat(sjekkplan, e.message)
        }
    }

    private fun lagInvariantbruddResultat(
        sjekkplan: SjekkPlan,
        feilmelding: String?,
    ): FradragssjekkSakResultat {
        return FradragssjekkSakResultat.Invariantbrudd(
            sakId = sjekkplan.sak.sakId,
            sakstype = sjekkplan.sak.type,
            sjekkPunkter = sjekkplan.sjekkpunkter,
            feilmelding = feilmelding,
        )
    }

    // Som Iterable.partition men uten type erasure
    private inline fun <reified A, reified B> Iterable<*>.partitionTyped(): Pair<List<A>, List<B>> {
        val a = mutableListOf<A>()
        val b = mutableListOf<B>()

        for (e in this) {
            when (e) {
                is A -> a.add(e)
                is B -> b.add(e)
            }
        }

        return a to b
    }

    private fun finnEksterneFeilForSak(
        sjekkplan: SjekkPlan,
        oppslagsresultater: EksterneOppslagsresultater,
    ): List<EksternFeilPåSjekkpunkt> {
        return sjekkplan.sjekkpunkter.mapNotNull { sjekkpunkt ->
            when (val oppslag = oppslagsresultater.finnYtelseForPerson(sjekkpunkt)) {
                is EksterntOppslag.Feil -> EksternFeilPåSjekkpunkt(
                    sjekkpunkt = sjekkplan.sjekkpunkter,
                    grunn = oppslag.grunn,
                )

                EksterntOppslag.IngenTreff,
                is EksterntOppslag.Funnet,
                -> null
            }
        }
    }

    private fun opprettOppgaveForFradrag(
        sak: SakInfo,
        måned: Måned,
        avvik: List<Fradragsfunn.Oppgaveavvik>,
    ): OppgaveopprettelseResultat {
        return oppgaveService.opprettOppgaveMedSystembruker(
            OppgaveConfig.Fradragssjekk(
                saksnummer = sak.saksnummer,
                måned = måned,
                avvik = avvik.map {
                    OppgaveConfig.Fradragssjekk.Avvik(
                        kode = it.kode,
                        tekst = it.oppgavetekst,
                    )
                },
                sakstype = sak.type,
                fnr = sak.fnr,
                clock = clock,
            ),
        ).fold(
            ifLeft = {
                OppgaveopprettelseResultat.Feilet(
                    MislykketOppgaveopprettelse(
                        sakId = sak.sakId,
                        avvikskoder = avvik.map { it.kode }.distinct(),
                    ),
                )
            },
            ifRight = {
                log.info("Fradragssjekk: Opprettet oppgave {} for sak {}", it.oppgaveId, sak.sakId)
                OppgaveopprettelseResultat.Opprettet(oppgaveId = it.oppgaveId, sakId = sak.sakId)
            },
        )
    }
}

sealed interface FradragsSjekkFeil {
    data object AlleredeKjørtForMåned : FradragsSjekkFeil
    data object DatoErTilbakeITid : FradragsSjekkFeil
    data object DatoErFremITid : FradragsSjekkFeil
}
