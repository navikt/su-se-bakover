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

interface FradragsjobbenService {
    fun sjekkLøpendeSakerForFradragIEksterneSystemer()
}

private const val INTERN_SAK_BATCH_STORRELSE = 500
private const val EKSTERN_OPPSLAG_BATCH_STORRELSE = 50

class FradragsjobbenServiceImpl(
    private val aapKlient: AapApiInternClient,
    private val pesysKlient: PesysClient,
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val utbetalingsRepo: UtbetalingRepo,
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
    override fun sjekkLøpendeSakerForFradragIEksterneSystemer() {
        val måned = Måned.now(clock)
        var resultat = FradragssjekkResultat()

        hentAlleSaker()
            .chunked(INTERN_SAK_BATCH_STORRELSE)
            .forEach { sakerPerBatch ->
                resultat += prosesserSakerBatch(sakerPerBatch, måned)
            }

        loggOppsummering(måned, resultat)
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
        måned: Måned,
        resultat: FradragssjekkResultat,
    ) {
        log.info(
            "Fradragssjekk fullført for måned {}. Vurderte saker: {}, saker med avvik: {}, opprettede oppgaver: {}, hoppet over pga eksterne feil: {}",
            måned,
            resultat.vurderteSaker,
            resultat.sakerMedAvvik,
            resultat.opprettedeOppgaver,
            resultat.hoppetOverPåGrunnAvEksternFeil,
        )

        if (resultat.mislykkedeOppgaveopprettelser.isNotEmpty()) {
            loggMislykkedeOppgaveopprettelser(resultat)
        }
    }

    private fun loggMislykkedeOppgaveopprettelser(
        resultat: FradragssjekkResultat,
    ) {
        log.error(
            "Fradragssjekk: Mislykket oppgaveopprettelse for {} saker. {}",
            resultat.mislykkedeOppgaveopprettelser.size,
            resultat.mislykkedeOppgaveopprettelser.joinToString(separator = "; ") {
                "sakId=${it.sakId}, avvikskoder=${it.avvikskoder.joinToString(",")}"
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

        val eksterneOppslag = eksterneOppslagService.hentPerioderForYtelser(sjekkplaner, måned)
        var resultat = FradragssjekkResultat(vurderteSaker = sjekkplaner.size)

        sjekkplaner.forEach { sjekkplan ->
            // TODO: disse skal kanskje ha støtte for å rekjøres
            if (sjekkplan.sjekkpunkter.any { eksterneOppslag.hentLagretResultat(it) is EksterntOppslag.Feil }) {
                resultat = resultat.registrerHoppetOverPåGrunnAvEksternFeil()
                return@forEach
            }

            when (val avviksvurdering = finnAvvikForSak(sjekkplan, eksterneOppslag)) {
                Avviksvurdering.IngenDiff -> Unit
                is Avviksvurdering.Diff -> {
                    resultat = resultat.registrerSakMedAvvik()

                    val (oppgaveAvvik, observasjonsAvvik) = avviksvurdering.avvik.partitionTyped<Fradragsfunn.Oppgaveavvik, Fradragsfunn.Observasjon>()
                    resultat = resultat.copy(sakerInsignifikantDifferanseForOppgave = observasjonsAvvik)
                    resultat = when (val oppgaveResultat = opprettOppgaveForFradrag(sjekkplan.sak, måned, oppgaveAvvik)) {
                        OppgaveopprettelseResultat.Opprettet -> {
                            resultat.registrerOpprettetOppgave()
                        }

                        is OppgaveopprettelseResultat.Feilet -> {
                            resultat.registrerMislykketOppgaveopprettelse(oppgaveResultat.feil)
                        }
                    }
                }
            }
        }

        return resultat
    }

    // Som Iterable.partition men uten type erasure
    inline fun <reified A, reified B> Iterable<*>.partitionTyped(): Pair<List<A>, List<B>> {
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
                OppgaveopprettelseResultat.Opprettet
            },
        )
    }
}
