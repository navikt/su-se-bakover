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
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.Instant
import java.util.UUID

interface FradragsjobbenService {
    fun sjekkLøpendeSakerForFradragIEksterneSystemer()
}

private const val INTERN_SAK_BATCH_STORRELSE = 500
private const val EKSTERN_OPPSLAG_BATCH_STORRELSE = 50

internal class FradragsjobbenServiceImpl(
    private val aapKlient: AapApiInternClient,
    private val pesysKlient: PesysClient,
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val utbetalingsRepo: UtbetalingRepo,
    private val fradragssjekkRunPostgresRepo: FradragssjekkRunPostgresRepo,
    private val clock: Clock,
) : FradragsjobbenService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val eksterneOppslagService = EksterneFradragsoppslagService(
        aapKlient = aapKlient,
        pesysKlient = pesysKlient,
        log = log,
    )

    private sealed interface Kjøringsutfall {
        val resultat: FradragssjekkResultat

        data class Fullført(
            override val resultat: FradragssjekkResultat,
        ) : Kjøringsutfall

        data class Feilet(
            override val resultat: FradragssjekkResultat,
            val exception: Exception,
        ) : Kjøringsutfall
    }

    /**
     * 1. Finn løpende saker
     * 2. Hent fradrag for sakene basert på type sak
     * SU ufør: Sjekk UFØR OG AAP på bruker og UFØR og AAP på EPS hvis EPS er under 67, men kun AP for EPS dersom EPS er over 67
     *
     * SU alder: sjekk AP på bruker og AP på EPS dersom EPS er over 67, men UFØR og AAP dersom EPS er under 67
     * 3. Lag oppgave hvis fradrag er ulikt eller ikke fantes på brukes
     *
     */
    override fun sjekkLøpendeSakerForFradragIEksterneSystemer() {
        val måned = Måned.now(clock)
        val kjoringId = UUID.randomUUID()
        val startet = clock.instant()

        log.info("Starter fradragssjekk for måned {} med kjøring {}", måned, kjoringId)

        val utfall = kjørFradragssjekk(måned)

        lagreAvsluttetKjøring(
            kjoringId = kjoringId,
            måned = måned,
            startet = startet,
            utfall = utfall,
        )

        if (utfall is Kjøringsutfall.Fullført) {
            loggOppsummering(kjoringId, måned, utfall.resultat)
        }

        if (utfall is Kjøringsutfall.Feilet) {
            throw utfall.exception
        }
    }

    private fun kjørFradragssjekk(
        måned: Måned,
    ): Kjøringsutfall {
        var resultat = FradragssjekkResultat()

        return try {
            hentAlleSaker()
                .chunked(INTERN_SAK_BATCH_STORRELSE)
                .forEach { sakerPerBatch ->
                    resultat += prosesserSakerBatch(sakerPerBatch, måned)
                }

            Kjøringsutfall.Fullført(resultat = resultat)
        } catch (e: Exception) {
            Kjøringsutfall.Feilet(
                resultat = resultat,
                exception = e,
            )
        }
    }

    private fun lagreAvsluttetKjøring(
        kjoringId: UUID,
        måned: Måned,
        startet: Instant,
        utfall: Kjøringsutfall,
    ) {
        val kjoring = when (utfall) {
            is Kjøringsutfall.Fullført -> FradragssjekkKjøring(
                id = kjoringId,
                måned = måned,
                status = FradragssjekkKjøringStatus.FULLFØRT,
                opprettet = startet,
                ferdigstilt = clock.instant(),
                resultat = utfall.resultat,
            )

            is Kjøringsutfall.Feilet -> FradragssjekkKjøring(
                id = kjoringId,
                måned = måned,
                status = FradragssjekkKjøringStatus.FEILET,
                opprettet = startet,
                ferdigstilt = clock.instant(),
                resultat = utfall.resultat,
                feilmelding = utfall.exception.message,
            )
        }

        fradragssjekkRunPostgresRepo.lagreKjoring(kjoring)
    }

    private fun prosesserSakerBatch(
        sakerPerBatch: List<SakInfo>,
        måned: Måned,
    ): FradragssjekkResultat {
        return hentSakerMedLøpendeUtbetalingForMåned(sakerPerBatch, måned)
            .let { lagSjekkplanerForLøpendeSaker(it, måned) }
            .chunked(EKSTERN_OPPSLAG_BATCH_STORRELSE)
            .fold(FradragssjekkResultat()) { acc, sjekkplanBatch ->
                acc + prosesserSjekkplanBatch(sjekkplanBatch, måned)
            }
    }

    private fun loggOppsummering(
        kjoringId: UUID,
        måned: Måned,
        resultat: FradragssjekkResultat,
    ) {
        val sakerMedAvvik = resultat.saksresultater.filter { it.oppgaveAvvik.isNotEmpty() }
        val opprettedeOppgaver = resultat.saksresultater.filter { it.opprettetOppgave != null }
        val sakerMedEksternFeil = resultat.saksresultater.filter { it.eksterneFeil.isNotEmpty() }
        val sakerMedObservasjoner = resultat.saksresultater.filter { it.observasjoner.isNotEmpty() }
        val sakerMedInvariantbrudd = resultat.saksresultater.filter { it.status == FradragssjekkSakStatus.INVARIANTBRUDD }
        val mislykkedeOppgaveopprettelser = resultat.saksresultater.filter { it.mislykketOppgaveopprettelse != null }

        log.info(
            "Fradragssjekk fullført for kjøring {} og måned {}. Vurderte saker: {}, saker med avvik: {}, opprettede oppgaver: {}, hoppet over pga eksterne feil: {}, observasjoner: {}, invariantbrudd: {}",
            kjoringId,
            måned,
            resultat.vurderteSaker,
            sakerMedAvvik.size,
            opprettedeOppgaver.size,
            sakerMedEksternFeil.size,
            sakerMedObservasjoner.size,
            sakerMedInvariantbrudd.size,
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

    private fun hentSakerMedLøpendeUtbetalingForMåned(
        saker: List<SakInfo>,
        måned: Måned,
    ): List<SakInfo> {
        if (saker.isEmpty()) return emptyList()

        val utbetalingerPerSak = utbetalingsRepo.hentOversendteUtbetalingerForSakIder(
            saker.map { it.sakId },
        )

        return saker.filter { sak ->
            utbetalingerPerSak[sak.sakId]
                ?.hentGjeldendeUtbetaling(måned.fraOgMed)
                ?.fold(
                    { false },
                    { true },
                ) == true
        }
    }

    private fun lagSjekkplanerForLøpendeSaker(
        løpendeSaker: List<SakInfo>,
        måned: Måned,
    ): List<SjekkPlan> {
        return løpendeSaker.mapNotNull { sak ->
            hentGjeldendeVedtaksdataForSak(sak, måned)?.let { gjeldendeVedtaksdata ->
                lagSjekkplanForSak(
                    sak = sak,
                    gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                    måned = måned,
                )
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
        sjekkplaner: List<SjekkPlan>,
        måned: Måned,
    ): FradragssjekkResultat {
        if (sjekkplaner.isEmpty()) return FradragssjekkResultat()

        // Kan tenkes at man burde transformert og merged den med sjekkplan direkte kontra å åpne opp på denne måten for feil hits
        val oppslagsresultater = eksterneOppslagService.hentOppslagsresultaterForYtelser(sjekkplaner, måned)
        val saksresultater = sjekkplaner.map { sjekkplan ->
            prosesserSjekkplan(
                sjekkplan = sjekkplan,
                måned = måned,
                oppslagsresultater = oppslagsresultater,
            )
        }

        return FradragssjekkResultat(
            saksresultater = saksresultater,
        )
    }

    private fun prosesserSjekkplan(
        sjekkplan: SjekkPlan,
        måned: Måned,
        oppslagsresultater: EksterneOppslagsresultater,
    ): FradragssjekkSakResultat {
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

            when (val avviksvurdering = finnAvvikForSak(sjekkplan, oppslagsresultater)) {
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
            FradragssjekkSakResultat(
                sakId = sjekkplan.sak.sakId,
                status = FradragssjekkSakStatus.INVARIANTBRUDD,
                sjekkplan = SjekkPlanData.fraDomain(sjekkplan),
                feilmelding = e.message,
            )
        }
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
