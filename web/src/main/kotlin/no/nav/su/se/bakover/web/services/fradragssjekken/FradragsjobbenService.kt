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
import java.util.UUID

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

        log.info(
            "Fradragssjekk: Starter bygging av sjekkplaner for måned {}. Antall saker: {}, interne batcher: {}",
            måned,
            alleSaker.size,
            totaltAntallInterneBatcher,
        )

        val sjekkgrunnlag = alleSaker
            .chunked(INTERN_SAK_BATCH_STORRELSE)
            .flatMap { sakerPerBatch ->
                internBatchNummer++
                vurderteSaker += sakerPerBatch.size
                val løpendeSaker = hentSakerMedLøpendeUtbetalingForMåned(sakerPerBatch, måned)
                lagSjekkgrunnlagForLøpendeSaker(løpendeSaker, måned).also { batchSjekkgrunnlag ->
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
            }

        kjørOgLagreKjøring(
            måned = måned,
            dryRun = dryRun,
            sjekkgrunnlag = sjekkgrunnlag,
            startmelding = "Starter fradragssjekk for måned $måned",
        )
    }

    private fun kjørOgLagreKjøring(
        måned: Måned,
        dryRun: Boolean,
        sjekkgrunnlag: List<SjekkgrunnlagForSak>,
        startmelding: String,
    ) {
        val dato = java.time.LocalDate.now(clock)
        val kjoringId = UUID.randomUUID()
        val startet = clock.instant()
        val saksresultater = mutableListOf<FradragssjekkSakResultat>()
        val totaltAntallEksterneBatcher = antallBatcher(sjekkgrunnlag.size, EKSTERN_OPPSLAG_BATCH_STORRELSE)
        var eksternBatchNummer = 0

        log.info(
            "{} med kjøring {}. dryRun={}. Antall sjekkgrunnlag: {}, eksterne batcher: {}",
            startmelding,
            kjoringId,
            dryRun,
            sjekkgrunnlag.size,
            totaltAntallEksterneBatcher,
        )

        try {
            sjekkgrunnlag
                .chunked(EKSTERN_OPPSLAG_BATCH_STORRELSE)
                .forEach { sjekkgrunnlagBatch ->
                    eksternBatchNummer++
                    saksresultater += prosesserSjekkplanBatch(
                        sjekkgrunnlag = sjekkgrunnlagBatch,
                        måned = måned,
                        dryRun = dryRun,
                    )

                    log.info(
                        "Fradragssjekk: Ekstern batch {}/{} ferdig for kjøring {} og måned {}. Batchstørrelse: {}, saksresultater hittil: {}",
                        eksternBatchNummer,
                        totaltAntallEksterneBatcher,
                        kjoringId,
                        måned,
                        sjekkgrunnlagBatch.size,
                        saksresultater.size,
                    )
                }

            val resultat = FradragssjekkResultat(
                saksresultater = saksresultater.map {
                    when (it.status) {
                        FradragssjekkSakStatus.OPPGAVE_OPPRETTET -> it
                        else -> it.copy(sjekkplan = it.sjekkplan.copy(sak = it.sjekkplan.sak, sjekkpunkter = emptyList()))
                    }
                },
            )
            val kjoring = FradragssjekkKjøring(
                id = kjoringId,
                dato = dato,
                dryRun = dryRun,
                status = FradragssjekkKjøringStatus.FULLFØRT,
                opprettet = startet,
                ferdigstilt = clock.instant(),
                resultat = resultat,
            )
            fradragssjekkRunPostgresRepo.lagreKjoring(kjoring)
            loggOppsummering(kjoring, måned)
        } catch (e: Exception) {
            fradragssjekkRunPostgresRepo.lagreKjoring(
                FradragssjekkKjøring(
                    id = kjoringId,
                    dato = dato,
                    dryRun = dryRun,
                    status = FradragssjekkKjøringStatus.FEILET,
                    opprettet = startet,
                    ferdigstilt = clock.instant(),
                    resultat = FradragssjekkResultat(saksresultater = saksresultater),
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
    ) {
        val saksresultater = kjoring.resultat.saksresultater
        val sakerMedObservasjoner = kjoring.resultat.saksresultater.filter { it.observasjoner.isNotEmpty() }
        val mislykkedeOppgaveopprettelser = kjoring.resultat.saksresultater.filter { it.mislykketOppgaveopprettelse != null }
        val dryRunOppgaver = saksresultater.count { it.status == FradragssjekkSakStatus.OPPGAVE_IKKE_OPPRETTET_DRY_RUN }

        log.info(
            "Fradragssjekk fullført for kjøring {} og måned {}. Vurderte saker: {}, saker med avvik: {}, opprettede oppgaver: {}, dry run-oppgaver: {}, hoppet over pga eksterne feil: {}, observasjoner: {}, invariantbrudd: {}",
            kjoring.id,
            måned,
            saksresultater.size,
            saksresultater.count { it.oppgaveAvvik.isNotEmpty() },
            saksresultater.count { it.opprettetOppgave != null },
            dryRunOppgaver,
            saksresultater.count { it.eksterneFeil.isNotEmpty() },
            sakerMedObservasjoner.size,
            saksresultater.count { it.status == FradragssjekkSakStatus.INVARIANTBRUDD },
        )

        if (sakerMedObservasjoner.isNotEmpty()) {
            loggObservasjoner(sakerMedObservasjoner)
        }

        if (mislykkedeOppgaveopprettelser.isNotEmpty()) {
            loggMislykkedeOppgaveopprettelser(mislykkedeOppgaveopprettelser)
        }
    }

    private fun loggObservasjoner(
        saksresultater: List<FradragssjekkSakResultat>,
    ) {
        log.info(
            "Fradragssjekk: Fant {} observasjoner. {}",
            saksresultater.size,
            saksresultater.joinToString(separator = "; ") { saksresultat ->
                "sakId=${saksresultat.sakId}, observasjoner=${
                    saksresultat.observasjoner.joinToString(",") { it.loggtekst }
                }"
            },
        )
    }

    private fun loggMislykkedeOppgaveopprettelser(
        saksresultater: List<FradragssjekkSakResultat>,
    ) {
        log.error(
            "Fradragssjekk: Mislykket oppgaveopprettelse for {} saker. {}",
            saksresultater.size,
            saksresultater.joinToString(separator = "; ") {
                "sakId=${it.sakId}, avvikskoder=${it.mislykketOppgaveopprettelse?.avvikskoder?.joinToString(",")}"
            },
        )
    }

    private fun hentAlleSaker() = sakService.hentSakIdSaksnummerOgFnrForAlleSaker()

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

        // Kan tenkes at man burde transformert og merged den med sjekkplan direkte kontra å åpne opp på denne måten for feil hits
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
                return FradragssjekkSakResultat(
                    sakId = sjekkplan.sak.sakId,
                    status = FradragssjekkSakStatus.EKSTERN_FEIL,
                    sjekkplan = SjekkPlanData.fraDomain(sjekkplan),
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
                Avviksvurdering.IngenDiff -> FradragssjekkSakResultat(
                    sakId = sjekkplan.sak.sakId,
                    status = FradragssjekkSakStatus.INGEN_AVVIK,
                    sjekkplan = SjekkPlanData.fraDomain(sjekkplan),
                )

                is Avviksvurdering.Diff -> {
                    val (oppgaveAvvik, observasjonsAvvik) = avviksvurdering.avvik.partitionTyped<Fradragsfunn.Oppgaveavvik, Fradragsfunn.Observasjon>()

                    if (oppgaveAvvik.isEmpty()) {
                        FradragssjekkSakResultat(
                            sakId = sjekkplan.sak.sakId,
                            status = FradragssjekkSakStatus.KUN_OBSERVASJON,
                            sjekkplan = SjekkPlanData.fraDomain(sjekkplan),
                            observasjoner = observasjonsAvvik,
                        )
                    } else if (dryRun) {
                        FradragssjekkSakResultat(
                            sakId = sjekkplan.sak.sakId,
                            status = FradragssjekkSakStatus.OPPGAVE_IKKE_OPPRETTET_DRY_RUN,
                            sjekkplan = SjekkPlanData.fraDomain(sjekkplan),
                            oppgaveAvvik = oppgaveAvvik,
                            observasjoner = observasjonsAvvik,
                        )
                    } else {
                        when (val oppgaveResultat = opprettOppgaveForFradrag(sjekkplan.sak, måned, oppgaveAvvik)) {
                            is OppgaveopprettelseResultat.Opprettet -> {
                                FradragssjekkSakResultat(
                                    sakId = sjekkplan.sak.sakId,
                                    status = FradragssjekkSakStatus.OPPGAVE_OPPRETTET,
                                    sjekkplan = SjekkPlanData.fraDomain(sjekkplan),
                                    oppgaveAvvik = oppgaveAvvik,
                                    observasjoner = observasjonsAvvik,
                                    opprettetOppgave = oppgaveResultat,
                                )
                            }

                            is OppgaveopprettelseResultat.Feilet -> {
                                FradragssjekkSakResultat(
                                    sakId = sjekkplan.sak.sakId,
                                    status = FradragssjekkSakStatus.OPPGAVEOPPRETTELSE_FEILET,
                                    sjekkplan = SjekkPlanData.fraDomain(sjekkplan),
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
        return FradragssjekkSakResultat(
            sakId = sjekkplan.sak.sakId,
            status = FradragssjekkSakStatus.INVARIANTBRUDD,
            sjekkplan = SjekkPlanData.fraDomain(sjekkplan),
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
                    sjekkpunkt = SjekkpunktData.fraDomain(sjekkpunkt),
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
