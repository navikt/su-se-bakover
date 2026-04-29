package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.Instant
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
    private val fradragssjekkOppgaveoppretter: FradragssjekkOppgaveoppretter,
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
        val kjoringsdato = måned.fraOgMed
        val kjoringStartet = clock.instant()

        log.info(
            "Fradragssjekk: Starter bygging av sjekkplaner for måned {}. Antall saker: {}, interne batcher: {} id: $kjoringId",
            måned,
            alleSaker.size,
            totaltAntallInterneBatcher,
        )

        val saksresultater = mutableListOf<FradragssjekkSakResultat>()

        try {
            alleSaker
                .chunked(INTERN_SAK_BATCH_STORRELSE)
                .forEach { sakerPerBatch ->
                    internBatchNummer++
                    vurderteSaker += sakerPerBatch.size

                    val løpendeSaker = hentSakerMedLøpendeUtbetalingForMåned(sakerPerBatch, måned)
                    val sjekkgrunnlagForSaker = lagSjekkgrunnlagForLøpendeSaker(løpendeSaker, måned).also { batchSjekkgrunnlag ->
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
                    val ignorerbareSakder = gracePeriodeEnMåned(saker = sjekkgrunnlagForSaker.map { it.sjekkplan.sak.sakId }.distinct(), dryRun = dryRun)
                    val kjørbareSaker = sjekkgrunnlagForSaker.filter { it.sjekkplan.sak.sakId !in ignorerbareSakder }

                    saksresultater += slåOppFradragssjekkpunkter(
                        sjekkgrunnlagForSaker = kjørbareSaker,
                        måned = måned,
                        dryRun = dryRun,
                        kjøringId = kjoringId,
                        opprettet = kjoringStartet,
                    )
                }
        } catch (e: Exception) {
            lagreFeiletKjoring(
                kjøringId = kjoringId,
                dato = kjoringsdato,
                dryRun = dryRun,
                opprettet = kjoringStartet,
                e = e,
                oppsummering = lagFradragssjekkOppsummering(saksresultater),
            )
            loggOppsummering(
                kjøringId = kjoringId,
                måned = måned,
                saksresultater = saksresultater,
            )
            throw e
        }

        val kjoring = FradragssjekkKjøring(
            id = kjoringId,
            dato = kjoringsdato,
            dryRun = dryRun,
            status = FradragssjekkKjøringStatus.FULLFØRT,
            opprettet = kjoringStartet,
            ferdigstilt = clock.instant(),
        )

        try {
            fradragssjekkRunPostgresRepo.lagreKjoring(
                kjoring = kjoring,
                oppsummering = lagFradragssjekkOppsummering(saksresultater),
            )
        } catch (e: Exception) {
            lagreFeiletKjoring(
                kjøringId = kjoringId,
                dato = kjoringsdato,
                dryRun = dryRun,
                opprettet = kjoringStartet,
                e = e,
                oppsummering = lagFradragssjekkOppsummering(saksresultater),
            )
            loggOppsummering(kjoring.id, måned, saksresultater)
            throw e
        }

        loggOppsummering(kjoring.id, måned, saksresultater)
    }

    /*
        Saker som fikk oppgave i forrige måneds kjøring trenger ikke oppgave i påfølgende måned da
        saksbehandler trenger tid på seg for å rette opp saken
     */
    private fun gracePeriodeEnMåned(saker: List<UUID>, dryRun: Boolean, måned: Måned): List<UUID> {
        if (dryRun || saker.isEmpty()) return saker
        val sakIderMedOppgaveForrigeMåned =
            fradragssjekkRunPostgresRepo.hentSakIderMedOppgaveOpprettetForMåned(
                sakIder = saker,
                måned = måned.minusMonths(1),
            )
            /*
            send alle sakider her til fradragsjobbtabellen for forrige måneds kjøring og
            hvis oppgave ble opprettet så filtrerer vi de bort
             */
    }

    private fun slåOppFradragssjekkpunkter(
        sjekkgrunnlagForSaker: List<SjekkgrunnlagForSak>,
        måned: Måned,
        dryRun: Boolean,
        kjøringId: UUID,
        opprettet: Instant,
    ): List<FradragssjekkSakResultat> {
        val saksresultater = mutableListOf<FradragssjekkSakResultat>()
        val totaltAntallEksterneBatcher = antallBatcher(sjekkgrunnlagForSaker.size, EKSTERN_OPPSLAG_BATCH_STORRELSE)
        var eksternBatchNummer = 0

        log.info(
            "start:{} -> starter for måned {} med kjøring {}. dryRun={}. Antall sjekkgrunnlag: {}, eksterne batcher: {}",
            opprettet,
            måned,
            kjøringId,
            dryRun,
            sjekkgrunnlagForSaker.size,
            totaltAntallEksterneBatcher,
        )

        sjekkgrunnlagForSaker
            .chunked(EKSTERN_OPPSLAG_BATCH_STORRELSE)
            .forEach { sjekkgrunnlagBatch ->
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

        // NB: Denne gjøres per INTERN_SAK_BATCH_STORRELSE MAX ish gang der vet man at minnet holder som regel under
        fradragssjekkRunPostgresRepo.lagreSaksresultater(
            saker = saksresultater,
            måned = måned,
            kjøringId = kjøringId,
            opprettet = opprettet,
        )
        return saksresultater
    }

    private fun lagreFeiletKjoring(
        kjøringId: UUID,
        dato: LocalDate,
        dryRun: Boolean,
        opprettet: Instant,
        e: Exception,
        oppsummering: FradragssjekkOppsummering,
    ) {
        fradragssjekkRunPostgresRepo.lagreKjoring(
            FradragssjekkKjøring(
                id = kjøringId,
                dato = dato,
                dryRun = dryRun,
                status = FradragssjekkKjøringStatus.FEILET,
                opprettet = opprettet,
                ferdigstilt = clock.instant(),
                feilmelding = e.message,
            ),
            oppsummering = oppsummering,
        )
    }

    private fun antallBatcher(
        total: Int,
        batchStørrelse: Int,
    ): Int {
        if (total == 0) return 0
        return (total + batchStørrelse - 1) / batchStørrelse
    }

    private fun loggOppsummering(
        kjøringId: UUID,
        måned: Måned,
        saksresultater: List<FradragssjekkSakResultat>,
    ) {
        val oppsummering = lagOppsummeringPerÅrsak(saksresultater)

        log.info(
            "Fradragssjekk fullført for kjøring {} og måned {}. Vurderte saker: {} oppsummering: {}",
            kjøringId,
            måned,
            saksresultater.size,
            oppsummering,
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
                    val (oppgaveGrunnlag, observasjonsAvvik) = avviksvurdering.avvik.partitionTyped<Fradragsfunn.Oppgavegrunnlag, Fradragsfunn.Observasjon>()

                    if (oppgaveGrunnlag.isEmpty()) {
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
                            oppgaveGrunnlag = oppgaveGrunnlag,
                            observasjoner = observasjonsAvvik,
                        )
                    } else {
                        when (val oppgaveResultat = opprettOppgaveForFradrag(sjekkplan.sak, måned, oppgaveGrunnlag)) {
                            is OppgaveopprettelseResultat.Opprettet -> {
                                FradragssjekkSakResultat.OppgaveOpprettet(
                                    sakId = sjekkplan.sak.sakId,
                                    sakstype = sjekkplan.sak.type,
                                    sjekkPunkter = sjekkplan.sjekkpunkter,
                                    oppgaveGrunnlag = oppgaveGrunnlag,
                                    observasjoner = observasjonsAvvik,
                                    opprettetOppgave = oppgaveResultat,
                                )
                            }

                            is OppgaveopprettelseResultat.Feilet -> {
                                FradragssjekkSakResultat.OppgaveopprettelseFeilet(
                                    sakId = sjekkplan.sak.sakId,
                                    sakstype = sjekkplan.sak.type,
                                    sjekkPunkter = sjekkplan.sjekkpunkter,
                                    oppgaveGrunnlag = oppgaveGrunnlag,
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
            when (val oppslag = oppslagsresultater.finnYtelseForPerson(sjekkplan.sak.sakId, sjekkpunkt)) {
                is EksterntOppslag.Feil -> EksternFeilPåSjekkpunkt(
                    sjekkpunkt = sjekkpunkt,
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
        avvik: List<Fradragsfunn.Oppgavegrunnlag>,
    ): OppgaveopprettelseResultat {
        val config = OppgaveConfig.Fradragssjekk(
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
        )

        val response = fradragssjekkOppgaveoppretter.opprett(config, setOf(NøkkelOrd.FRADRAGSSJEKK))

        return response.fold(
            ifLeft = {
                OppgaveopprettelseResultat.Feilet(
                    MislykketOppgaveopprettelse(
                        sakId = sak.sakId,
                        avvikskoder = avvik.map { it.kode }.distinct(),
                    ),
                )
            },
            ifRight = {
                log.info(
                    "Fradragssjekk: Opprettet oppgave {} for sak {}",
                    it.oppgaveId,
                    sak.sakId,
                )
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
