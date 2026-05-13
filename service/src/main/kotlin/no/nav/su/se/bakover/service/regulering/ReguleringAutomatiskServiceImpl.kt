package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.domain.extensions.split
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.toMåned
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringAutomatiskService
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.beregnerUtenforToleransegrenser
import no.nav.su.se.bakover.domain.regulering.erRegulertMedNyttGrunnbeløp
import no.nav.su.se.bakover.domain.regulering.hentGjeldendeVedtaksdataForRegulering
import no.nav.su.se.bakover.domain.regulering.logg
import no.nav.su.se.bakover.domain.regulering.opprettReguleringForAutomatiskEllerManuellBehandling
import no.nav.su.se.bakover.domain.regulering.toReguleringForLogResultat
import no.nav.su.se.bakover.domain.regulering.toResultat
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.harGrunnbeløpSomKanReguleresAutomatisk
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.filterIsInstance
import kotlin.collections.joinToString

class ReguleringAutomatiskServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val reguleringKjøringRepo: ReguleringKjøringRepo,
    private val sakService: SakService,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
    private val reguleringService: ReguleringServiceImpl,
    private val statistikkService: SakStatistikkService,
    private val sessionFactory: SessionFactory,
    private val reguleringerFraPesysService: ReguleringerFraPesysService,
    private val aapReguleringerService: AapReguleringerService,
) : ReguleringAutomatiskService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val EKSTERN_OPPSLAG_BATCH_STORRELSE = 50
    }

    override fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
        grunnbeløpRegulering: Boolean,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>> {
        return Either.catch { start(fraOgMedMåned, satsFactory, grunnbeløpRegulering) }
            .mapLeft {
                log.error(
                    "Ukjent feil skjedde ved automatisk regulering for fraOgMedMåned: $fraOgMedMåned. Se sikkerlogg for feilmelding.",
                    RuntimeException("Inkluderer stacktrace"),
                )
                sikkerLogg.error("Ukjent feil skjedde ved automatisk regulering for fraOgMedMåned: $fraOgMedMåned", it)

                throw it
            }
            .fold(
                ifLeft = { it },
                ifRight = { it },
            )
    }

    override fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    ) {
        val factory = command.satsFactory.gjeldende(command.gjeldendeSatsFra)

        Either.catch {
            start(
                fraOgMedMåned = command.startDatoRegulering,
                satsFactory = factory,
                testRun = ReguleringTestRun(
                    lagreManuelle = command.lagreManuelle,
                    maksAntallSaker = command.maksAntallSaker,
                    kunSakstype = command.kunSakstype,
                ),
                grunnbeløpRegulering = command.grunnbeløpRegulering,
            )
        }.onLeft {
            log.error(
                "Ukjent feil skjedde ved automatisk regulering for innsyn for kommando: $command. Se sikkerlogg for flere detaljer.",
                RuntimeException("Inkluderer stacktrace"),
            )
            sikkerLogg.error("Ukjent feil skjedde ved automatisk regulering for innsyn for kommando: $command", it)
        }
    }

    /**
     * Henter saksinformasjon for alle saker og løper igjennom alle sakene et etter en.
     * Dette kan ta lang tid, så denne bør ikke kjøres synkront.
     */
    private fun start(
        fraOgMedMåned: Måned,
        satsFactory: SatsFactory,
        grunnbeløpRegulering: Boolean,
        testRun: ReguleringTestRun? = null,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>> {
        val startTid = LocalDateTime.now()
        log.info("Automatisk regulering: Starter for måned=$fraOgMedMåned, dryrun=${testRun != null} ${testRun?.let { ", maksAntall=${it.maksAntallSaker}, kunSakstype=${it.kunSakstype}" }}")
        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
            .let { saker -> testRun?.kunSakstype?.let { saker.filter { it.type == testRun.kunSakstype } } ?: saker }
            .let { saker -> testRun?.maksAntallSaker?.let { saker.take(it) } ?: saker }
        val resultater = alleSaker
            .chunked(EKSTERN_OPPSLAG_BATCH_STORRELSE)
            .flatMapIndexed { batchIndex, sakerPerBatch ->
                log.info(
                    "Automatisk regulering: Starter batch ${batchIndex + 1} av ${(alleSaker.size + EKSTERN_OPPSLAG_BATCH_STORRELSE - 1) / EKSTERN_OPPSLAG_BATCH_STORRELSE}. Antall saker i batch: ${sakerPerBatch.size}",
                )

                val tidSakVedtaksdata = LocalDateTime.now()
                log.info("Automatisk regulering: Henter sak og vedtaksinfo for batch.")
                val sakerSomSkalReguleresEllerIkke = sakerPerBatch.map { sakInfo ->
                    hentSakerMedVedtaksdataSomSkalReguleres(fraOgMedMåned, sakInfo, grunnbeløpRegulering, satsFactory)
                }
                Duration.between(startTid, LocalDateTime.now()).seconds
                log.info(
                    "Automatisk regulering: Henter sak og vedtaksinfo fullført for batch, tidsbrukSekunder=${
                        Duration.between(tidSakVedtaksdata, LocalDateTime.now()).seconds
                    }",
                )

                val tidEksterneBeløper = LocalDateTime.now()
                log.info("Automatisk regulering: Henter eksterne beløp for batch.")
                val sakerSomKanReguleres = sakerSomSkalReguleresEllerIkke.filterRights()
                val eksterntRegulerteBeløp = if (sakerSomKanReguleres.isEmpty()) {
                    emptyList()
                } else {
                    hentEksterntRegulerteBeløpEllerKastFeil(fraOgMedMåned, sakerSomKanReguleres)
                }
                log.info(
                    "Automatisk regulering: Henter eksterne beløp for batch, tidsbrukSekunder=${
                        Duration.between(tidEksterneBeløper, LocalDateTime.now()).seconds
                    }",
                )

                val feilPåEksterneReguleringer = eksterntRegulerteBeløp.filterLefts()
                val sakerSomSkalReguleresEllerIkkeMedEksterneReguleringer = sakerSomSkalReguleresEllerIkke.map {
                    it.flatMap { sakTilRegulering ->
                        val feil = feilPåEksterneReguleringer.find { it.fnr == sakTilRegulering.sakInfo.fnr }
                        if (feil != null) {
                            BleIkkeRegulert.UthentingFradragPesysFeilet(feil, sakTilRegulering.sakInfo.saksnummer)
                                .left()
                        } else {
                            sakTilRegulering.right()
                        }
                    }
                }

                val tidKjørReguøeringForSaker = LocalDateTime.now()
                log.info("Automatisk regulering: kjører regulering for saker fra batch.")
                sakerSomSkalReguleresEllerIkkeMedEksterneReguleringer.map {
                    it.flatMap { sakTilRegulering ->
                        log.info("Regulering for saksnummer ${sakTilRegulering.sakInfo.saksnummer}: Starter")
                        Either.catch {
                            sakTilRegulering.kjørForSak(
                                satsFactory = satsFactory,
                                sakerMedEksterntRegulerteBeløp = eksterntRegulerteBeløp.filterRights(),
                                testRun = testRun,
                            )
                        }.getOrElse {
                            BleIkkeRegulert.UkjentFeil(
                                feil = it,
                                saksnummer = sakTilRegulering.sakInfo.saksnummer,
                            ).left()
                        }
                    }
                }.also {
                    log.info(
                        "Automatisk regulering: kjører regulering for saker fra batch, tidsbrukSekunder=${
                            Duration.between(tidKjørReguøeringForSaker, LocalDateTime.now()).seconds
                        }",
                    )
                }
            }
        return resultater.also {
            lagreResultat(fraOgMedMåned, startTid, testRun, alleSaker, it)
        }
    }

    private fun hentSakerMedVedtaksdataSomSkalReguleres(
        fraOgMedMåned: Måned,
        sakInfo: SakInfo,
        grunnbeløpRegulering: Boolean,
        satsFactory: SatsFactory,
    ): Either<BleIkkeRegulert, SakTilRegulering> {
        val (sakid, saksnummer, _, type) = sakInfo
        return Either.catch {
            val reguleringer = reguleringRepo.hentForSakId(sakid)
            reguleringer.filterIsInstance<ReguleringUnderBehandling>().let { r ->
                when (r.size) {
                    0 -> {}
                    1 -> return BleIkkeRegulert.FinnesÅpenRegulering(saksnummer)
                        .left()

                    else -> throw IllegalStateException("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende grunn: Det finnes fler enn en åpen regulering.")
                }
            }
            if (grunnbeløpRegulering) {
                val alleredeRegulert = reguleringer.filterIsInstance<IverksattRegulering>()
                    .any { it.periode.fraOgMed == fraOgMedMåned.fraOgMed }
                if (alleredeRegulert) {
                    return BleIkkeRegulert.AlleredeRegulert(saksnummer).left()
                }
            }

            val vedtakSomKanRevurderes = vedtakRepo.hentVedtakSomKanRevurderesForSak(sakInfo.sakId)
            val vedtaksdata =
                hentGjeldendeVedtaksdataForRegulering(
                    fraOgMedMåned,
                    sakInfo,
                    vedtakSomKanRevurderes,
                    clock,
                ).getOrElse {
                    return it.left()
                }

            if (grunnbeløpRegulering && vedtaksdata.erRegulertMedNyttGrunnbeløp(fraOgMedMåned, type, satsFactory)) {
                return BleIkkeRegulert.AlleredeRegulert(saksnummer).left()
            }

            SakTilRegulering(sakInfo, vedtaksdata).right()
        }.getOrElse { feil ->
            BleIkkeRegulert.UkjentFeil(feil, saksnummer).left()
        }
    }

    private fun hentEksterntRegulerteBeløpEllerKastFeil(
        fraOgMedMåned: Måned,
        sakerSomKanReguleres: List<SakTilRegulering>,
    ) =
        Either.catch {
            val eksterntOppslagsgrunnlag = HentReguleringerPesysParameter.utledGrunnlagFraSaker(
                reguleringsMåned = fraOgMedMåned.fraOgMed.toMåned(),
                forSaker = sakerSomKanReguleres,
            )
            val fraPesys = reguleringerFraPesysService.hentReguleringer(eksterntOppslagsgrunnlag)
            val fraAap = aapReguleringerService.hentReguleringer(eksterntOppslagsgrunnlag)
            slåSammenEksterneReguleringer(
                brukereMedEps = eksterntOppslagsgrunnlag.brukereMedEps,
                fraPesys = fraPesys,
                fraAap = fraAap,
            )
        }.getOrElse {
            // TODO AUTO-REG-26 Feile enkelt batch?
            throw it
        }

    private fun SakTilRegulering.kjørForSak(
        satsFactory: SatsFactory,
        sakerMedEksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
        testRun: ReguleringTestRun? = null,
    ): Either<BleIkkeRegulert, ReguleringOppsummering> {
        val startTid = LocalDateTime.now()
        val (sakId, saksnummer, _, _) = sakInfo

        val regulering = opprettReguleringForAutomatiskEllerManuellBehandling(
            clock = clock,
            alleEksterntRegulerteBeløp = sakerMedEksterntRegulerteBeløp,
        ).getOrElse { feil ->
            log.error("Kan ikke gjennomføre regulering for saksnummer $saksnummer. Saksbehandler må få beskjed om at skal revurderes. Årsak: $feil")
            return BleIkkeRegulert.MåRegulereMedRevurdering(saksnummer, feil).left()
        }

        if (regulering.reguleringstype is Reguleringstype.AUTOMATISK) {
            val utbetalinger = reguleringService.hentUtbetalinger(sakId)

            val skalGjøreToleransesjekk =
                regulering.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.any { it.fradragstype.harGrunnbeløpSomKanReguleresAutomatisk() }
            if (skalGjøreToleransesjekk) {
                val utenforToleransegrenser =
                    beregnerUtenforToleransegrenser(regulering, utbetalinger, satsFactory, clock)
                if (utenforToleransegrenser != null) {
                    log.error("Kan ikke gjennomføre regulering for saksnummer ${sakInfo.saksnummer}. Saksbehandler må få beskjed om at skal revurderes. Årsak: $utenforToleransegrenser")
                    return BleIkkeRegulert.MåRegulereMedRevurdering(sakInfo.saksnummer, utenforToleransegrenser).left()
                }
            }

            return reguleringService.behandleReguleringAutomatisk(
                regulering,
                sakInfo,
                utbetalinger,
                isLiveRun = testRun == null,
            )
                .onRight { log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen ble ferdigstilt automatisk") }
                .mapLeft { feil ->
                    BleIkkeRegulert.KunneIkkeBehandleAutomatisk(
                        feil = feil,
                        saksnummer = saksnummer,
                        tidsbrukSekunder = Duration.between(startTid, LocalDateTime.now()).seconds.toInt(),
                    )
                }
                .fold(
                    ifLeft = { it.left() },
                    ifRight = { it.toReguleringForLogResultat(startTid).right() },
                )
        } else {
            log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen må behandles manuelt. ${(regulering.reguleringstype as Reguleringstype.MANUELL).problemer}")
            if (testRun == null || testRun.lagreManuelleUnderDryRun(regulering)) {
                lagreReguleringManuell(sakId, regulering)
            }
            return regulering.toReguleringForLogResultat(startTid).right()
        }
    }

    private fun lagreReguleringManuell(sakId: UUID, regulering: ReguleringUnderBehandling) {
        if (regulering.reguleringstype is Reguleringstype.AUTOMATISK) {
            throw IllegalStateException("Skal ikke lagre for automatisk regulering før den er ferdigstilt")
        }
        sessionFactory.withTransactionContext { tx ->
            reguleringRepo.lagre(regulering, tx)
            val relatertId = reguleringService.hentRelatertId(sakId, tx)
            statistikkService.lagre(
                StatistikkEvent.Behandling.Regulering.Opprettet(regulering, relatertId),
                tx,
            )
        }
    }

    private fun lagreResultat(
        fraOgMedMåned: Måned,
        startTid: LocalDateTime,
        testRun: ReguleringTestRun? = null,
        alleSaker: List<SakInfo>,
        resultater: List<Either<BleIkkeRegulert, ReguleringOppsummering>>,
    ) {
        val (lefts, rights) = resultater.split()

        val sakerIkkeLøpende = lefts.filterIsInstance<BleIkkeRegulert.IkkeLøpendeSak>().map {
            it.toResultat(Reguleringsresultat.Utfall.IKKE_LOEPENDE)
        }

        val sakerAlleredeRegulert =
            lefts.filterIsInstance<BleIkkeRegulert.AlleredeRegulert>().map {
                it.toResultat(Reguleringsresultat.Utfall.ALLEREDE_REGULERT)
            }

        val måRegulereVedRevurdering =
            lefts.filterIsInstance<BleIkkeRegulert.MåRegulereMedRevurdering>().map {
                it.toResultat(Reguleringsresultat.Utfall.MÅ_REVURDERE, it.årsak.toString())
            }

        val reguleringerSomFeilet = lefts.filter {
            it is BleIkkeRegulert.FantIkkeSak ||
                it is BleIkkeRegulert.KunneIkkeBehandleAutomatisk ||
                it is BleIkkeRegulert.UthentingFradragPesysFeilet ||
                it is BleIkkeRegulert.UkjentFeil
        }.map {
            if (it is BleIkkeRegulert.KunneIkkeBehandleAutomatisk) {
                it.toResultat(Reguleringsresultat.Utfall.FEILET, it.toString(), it.tidsbrukSekunder)
            } else {
                it.toResultat(Reguleringsresultat.Utfall.FEILET, it.toString())
            }
        }

        val reguleringerAlleredeÅpen = lefts.filterIsInstance<BleIkkeRegulert.FinnesÅpenRegulering>().map {
            it.toResultat(Reguleringsresultat.Utfall.AAPEN_REGULERING, it.toString())
        }

        val reguleringerManuell = rights.filter { it.reguleringstype is Reguleringstype.MANUELL }.map {
            val årsaker = (it.reguleringstype as Reguleringstype.MANUELL).problemer.map { it.kategori.name }
            it.toResultat(utfall = Reguleringsresultat.Utfall.MANUELL, beskrivelse = årsaker.joinToString(", "))
        }
        val reguleringerAutomatisk = rights.filter { it.reguleringstype is Reguleringstype.AUTOMATISK }.map {
            it.toResultat(utfall = Reguleringsresultat.Utfall.AUTOMATISK, beskrivelse = "Fullført automatisk")
        }

        val reguleringKjøring = ReguleringKjøring(
            id = UUID.randomUUID(),
            aar = fraOgMedMåned.årOgMåned.year,
            type = ReguleringKjøring.REGULERINGSTYPE_GRUNNBELØP,
            dryrun = testRun != null,
            startTid = startTid,
            sakerAntall = alleSaker.size,
            sakerIkkeLøpende = sakerIkkeLøpende,
            sakerAlleredeRegulert = sakerAlleredeRegulert,
            sakerMåRevurderes = måRegulereVedRevurdering,
            reguleringerSomFeilet = reguleringerSomFeilet,
            reguleringerAlleredeÅpen = reguleringerAlleredeÅpen,
            reguleringerManuell = reguleringerManuell,
            reguleringerAutomatisk = reguleringerAutomatisk,
        )
        reguleringKjøringRepo.lagre(reguleringKjøring)
        log.info(reguleringKjøring.logg())
    }
}

internal fun slåSammenEksterneReguleringer(
    brukereMedEps: List<HentReguleringerPesysParameter.BrukerMedEps>,
    fraPesys: List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>>,
    fraAap: List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>>,
): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>> {
    val forventedeFnr = brukereMedEps.map { it.fnr }
    val forventedeFnrSet = forventedeFnr.toSet()
    val fraPesysPerBruker = fraPesys.associateBy { it.fold(ifLeft = { it.fnr }, ifRight = { it.brukerFnr }) }
    val fraAapPerBruker = fraAap.associateBy { it.fold(ifLeft = { it.fnr }, ifRight = { it.brukerFnr }) }

    require(fraPesysPerBruker.keys == forventedeFnrSet) {
        "Forventet Pesys-resultater for $forventedeFnrSet, men fikk ${fraPesysPerBruker.keys}"
    }
    require(fraAapPerBruker.keys == forventedeFnrSet) {
        "Forventet AAP-resultater for $forventedeFnrSet, men fikk ${fraAapPerBruker.keys}"
    }

    return forventedeFnr.map { fnr ->
        val pesysResultat = fraPesysPerBruker.getValue(fnr)
        val aapResultat = fraAapPerBruker.getValue(fnr)
        when {
            pesysResultat is Either.Left && aapResultat is Either.Left -> HentingAvEksterneReguleringerFeiletForBruker(
                fnr = fnr,
                alleFeil = pesysResultat.value.alleFeil + aapResultat.value.alleFeil,
            ).left()

            pesysResultat is Either.Left -> pesysResultat
            aapResultat is Either.Left -> aapResultat
            pesysResultat is Either.Right && aapResultat is Either.Right -> (pesysResultat.value + aapResultat.value).right()
            else -> throw IllegalStateException("Ukjent kombinasjon ved sammenslåing av eksterne reguleringer for $fnr")
        }
    }
}

private operator fun EksterntRegulerteBeløp.plus(other: EksterntRegulerteBeløp): EksterntRegulerteBeløp {
    return EksterntRegulerteBeløp(
        brukerFnr = this.brukerFnr,
        beløpBruker = this.beløpBruker + other.beløpBruker,
        beløpEps = this.beløpEps + other.beløpEps,
        inntektEtterUføre = this.inntektEtterUføre ?: other.inntektEtterUføre,
    )
}

/*
* Konfigurasjon av automatisk regulering for å kunne teste på ulike måter.
*/
private data class ReguleringTestRun(
    val lagreManuelle: Boolean = false,
    val maksAntallSaker: Int? = null,
    val kunSakstype: Sakstype? = null,
) {
    /*
     * Det kan være ønskelig å få opprettet manuelle reguleringer uten å faktisk innføre et nytt grunnbeløp i systemet.
     * Da kan dry run med kunstig grunnbeløp benyttes med valget om å lagre manuelle reguleringer.
     * Selve reguleringen vil benytte eksisterende beløp etter den er opprettet men behovet er først og fremst å få
     * den manuelle reguleringen opprettet for å teste flyt ikke beregning.
     */
    fun lagreManuelleUnderDryRun(regulering: Regulering) =
        ApplicationConfig.isNotProd() && lagreManuelle && regulering.reguleringstype is Reguleringstype.MANUELL
}
