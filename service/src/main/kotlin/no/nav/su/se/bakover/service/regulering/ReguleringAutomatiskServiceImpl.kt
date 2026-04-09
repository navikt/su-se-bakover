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
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.toMåned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeBehandleRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereAutomatisk
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringAutomatiskService
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.hentGjeldendeVedtaksdataForRegulering
import no.nav.su.se.bakover.domain.regulering.opprettReguleringForAutomatiskEllerManuellBehandling
import no.nav.su.se.bakover.domain.regulering.supplement.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.regulering.toReguleringForLogResultat
import no.nav.su.se.bakover.domain.regulering.toResultat
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.filterIsInstance

class ReguleringAutomatiskServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val reguleringKjøringRepo: ReguleringKjøringRepo,
    private val sakService: SakService,
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
        /**
         * Inneholder data for alle sakene
         */
        supplement: Reguleringssupplement,
    ): List<Either<KunneIkkeRegulereAutomatisk, ReguleringOppsummering>> {
        reguleringRepo.lagre(supplement)
        return Either.catch { start(fraOgMedMåned, satsFactory) }
            .mapLeft {
                log.error(
                    "Ukjent feil skjedde ved automatisk regulering for fraOgMedMåned: $fraOgMedMåned. Se sikkerlogg for feilmelding.",
                    RuntimeException("Inkluderer stacktrace"),
                )
                sikkerLogg.error("Ukjent feil skjedde ved automatisk regulering for fraOgMedMåned: $fraOgMedMåned", it)
                KunneIkkeRegulereAutomatisk.UkjentFeil
            }
            .fold(
                ifLeft = { listOf(it.left()) },
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
                testRun = ReguleringTestRun(command.lagreManuelle),
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
        testRun: ReguleringTestRun? = null,
    ): List<Either<KunneIkkeRegulereAutomatisk, ReguleringOppsummering>> {
        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSaker()
        val resultater = alleSaker
            .chunked(EKSTERN_OPPSLAG_BATCH_STORRELSE)
            .flatMapIndexed { batchIndex, sakerPerBatch ->
                log.info(
                    "Automatisk regulering: Starter batch ${batchIndex + 1} av ${(alleSaker.size + EKSTERN_OPPSLAG_BATCH_STORRELSE - 1) / EKSTERN_OPPSLAG_BATCH_STORRELSE}. Antall saker i batch: ${sakerPerBatch.size}",
                )

                val sakerSomSkalReguleresEllerIkke = sakerPerBatch.map { (sakid, saksnummer, _) ->
                    val sak: Sak = Either.catch {
                        sakService.hentSak(sakId = sakid).getOrElse { throw RuntimeException("Inkluderer stacktrace") }
                    }.getOrElse {
                        log.error("Regulering for saksnummer $saksnummer: Klarte ikke hente sak $sakid", it)
                        return@map KunneIkkeRegulereAutomatisk.FantIkkeSak.left()
                    }

                    // TODO AUTO-REG-26 raskere måte å sjekke om ikke løpende uten før hele saksobjektet hentes
                    val vedtaksdata =
                        sak.hentGjeldendeVedtaksdataForRegulering(fraOgMedMåned, clock).getOrElse { feil ->
                            when (feil) {
                                Sak.KanIkkeRegulere.FinnesIngenVedtakSomKanRevurderesForValgtPeriode, Sak.KanIkkeRegulere.FørerIkkeTilEnEndring -> log.info(
                                    "Regulering for saksnummer ${sak.saksnummer} gjennomføres ikke på grunn av $feil",
                                )

                                is Sak.KanIkkeRegulere.MåRevurdere, Sak.KanIkkeRegulere.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig -> log.error(
                                    "Regulering for saksnummer ${sak.saksnummer}: Skippet. Denne feilen må varsles til saksbehandler og håndteres manuelt. Årsak: $feil",
                                )
                            }

                            return@map KunneIkkeRegulereAutomatisk.KunneIkkeHenteEllerOppretteRegulering(feil).left()
                        }

                    sak.reguleringer.filterIsInstance<ReguleringUnderBehandling>().let { r ->
                        when (r.size) {
                            0 -> {}
                            // TODO AUTO-REG-26 - vurder om åpne skal slettes og lages ny
                            1 -> return@map KunneIkkeRegulereAutomatisk.HarÅpenReguleringFraFør.left()
                            else -> throw IllegalStateException("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende grunn: Det finnes fler enn en åpen regulering.")
                        }
                    }

                    Pair(sak, vedtaksdata).right()
                }

                val sakerSomKanReguleres = sakerSomSkalReguleresEllerIkke.filterRights().map { it.first }
                val eksterntRegulerteBeløp = if (sakerSomKanReguleres.isEmpty()) {
                    emptyList()
                } else {
                    Either.catch {
                        val eksterntOppslagsgrunnlag = HentReguleringerPesysParameter.utledGrunnlagFraSaker(
                            reguleringsMåned = fraOgMedMåned.fraOgMed.toMåned(),
                            forSaker = sakerSomKanReguleres,
                            clock = clock,
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
                }

                val feilPåEksterneReguleringer = eksterntRegulerteBeløp.filterLefts()
                val sakerSomSkalReguleresEllerIkkeMedEksterneReguleringer = sakerSomSkalReguleresEllerIkke.map {
                    it.flatMap { (sak, vedtaksdata) ->
                        val feil = feilPåEksterneReguleringer.find { it.fnr == sak.fnr }
                        if (feil != null) {
                            KunneIkkeRegulereAutomatisk.UthentingFradragPesysFeilet(feil).left()
                        } else {
                            Pair(sak, vedtaksdata).right()
                        }
                    }
                }

                sakerSomSkalReguleresEllerIkkeMedEksterneReguleringer.map {
                    it.flatMap { (sak, vedtaksdata) ->
                        log.info("Regulering for saksnummer ${sak.saksnummer}: Starter")
                        sak.kjørForSak(
                            satsFactory = satsFactory,
                            vedtaksdata = vedtaksdata,
                            sakerMedEksterntRegulerteBeløp = eksterntRegulerteBeløp.filterRights(),
                            testRun = testRun,
                        )
                    }
                }
            }
        return resultater.also {
            lagreResultat(fraOgMedMåned, testRun, alleSaker, it)
            logResultat(resultater)
        }
    }

    private fun Sak.kjørForSak(
        satsFactory: SatsFactory,
        vedtaksdata: GjeldendeVedtaksdata,
        sakerMedEksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
        testRun: ReguleringTestRun? = null,
    ): Either<KunneIkkeRegulereAutomatisk, ReguleringOppsummering> {
        val sak = this

        val regulering = sak.opprettReguleringForAutomatiskEllerManuellBehandling(
            clock = clock,
            gjeldendeVedtaksdata = vedtaksdata,
            alleEksterntRegulerteBeløp = sakerMedEksterntRegulerteBeløp,
            satsFactory = satsFactory,
        ).getOrElse { feil ->
            log.error("Kan ikke gjennomføre regulering for saksnummer ${sak.saksnummer}. Saksbehandler må få beskjed om at skal revurderes. Årsak: $feil")
            return KunneIkkeRegulereAutomatisk.KunneIkkeHenteEllerOppretteRegulering(feil).left()
        }

        if (testRun == null || testRun.lagreManuelleUnderDryRun(regulering)) {
            lagreOpprettetEllerOverførtTilManuellRegulering(sak, regulering)
        }

        return if (regulering.reguleringstype is Reguleringstype.AUTOMATISK) {
            forsøkAutomatiskReguleringEllerOverførTilManuell(regulering, sak, isLiveRun = testRun == null)
                .onRight { log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen ble ferdigstilt automatisk") }
                .mapLeft { feil -> KunneIkkeRegulereAutomatisk.KunneIkkeBehandleAutomatisk(feil = feil) }
                .fold(
                    ifLeft = { it.left() },
                    ifRight = { it.toReguleringForLogResultat().right() },
                )
        } else {
            log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen må behandles manuelt. ${(regulering.reguleringstype as Reguleringstype.MANUELL).problemer}")
            regulering.toReguleringForLogResultat().right()
        }
    }

    private fun lagreResultat(
        fraOgMedMåned: Måned,
        testRun: ReguleringTestRun? = null,
        alleSaker: List<SakInfo>,
        resultater: List<Either<KunneIkkeRegulereAutomatisk, ReguleringOppsummering>>,
    ) {
        val (lefts, rights) = resultater.split()
        val startTid = LocalDateTime.now()

        val sakerSkalIkkeRegulere =
            lefts.filterIsInstance<KunneIkkeRegulereAutomatisk.KunneIkkeHenteEllerOppretteRegulering>()

        val sakerIkkeLøpende = sakerSkalIkkeRegulere.filter {
            it.feil is Sak.KanIkkeRegulere.FinnesIngenVedtakSomKanRevurderesForValgtPeriode
        }.map {
            serialize(
                Reguleringsresultat(
                    utfall = Reguleringsresultat.Utfall.IKKE_LOEPENDE,
                    beskrivelse = it.toString(),
                ),
            )
        }

        val sakerAlleredeRegulert = sakerSkalIkkeRegulere.filter {
            it.feil is Sak.KanIkkeRegulere.FørerIkkeTilEnEndring
        }.map {
            serialize(
                Reguleringsresultat(
                    utfall = Reguleringsresultat.Utfall.ALLEREDE_REGULERT,
                    beskrivelse = it.toString(),
                ),
            )
        }

        val sakerMåRevurderes = sakerSkalIkkeRegulere.filter {
            (it.feil is Sak.KanIkkeRegulere.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig || it.feil is Sak.KanIkkeRegulere.MåRevurdere)
        }.map {
            serialize(
                Reguleringsresultat(
                    utfall = Reguleringsresultat.Utfall.MÅ_REVURDERE,
                    beskrivelse = when (it.feil) {
                        is Sak.KanIkkeRegulere.MåRevurdere -> (it.feil as Sak.KanIkkeRegulere.MåRevurdere).årsak.name
                        Sak.KanIkkeRegulere.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig -> it.feil.toString()
                        else -> "Feil feiltype på dette stadiet"
                    },
                ),
            )
        }

        val reguleringerSomFeilet = lefts.filter {
            it is KunneIkkeRegulereAutomatisk.FantIkkeSak ||
                it is KunneIkkeRegulereAutomatisk.KunneIkkeBehandleAutomatisk ||
                it is KunneIkkeRegulereAutomatisk.UthentingFradragPesysFeilet ||
                it is KunneIkkeRegulereAutomatisk.UkjentFeil
        }.map {
            serialize(
                Reguleringsresultat(
                    utfall = Reguleringsresultat.Utfall.FEILET,
                    beskrivelse = it.toString(),
                ),
            )
        }
        val reguleringerAlleredeÅpen = lefts.filterIsInstance<KunneIkkeRegulereAutomatisk.HarÅpenReguleringFraFør>()
            .map {
                serialize(
                    Reguleringsresultat(
                        utfall = Reguleringsresultat.Utfall.AAPEN_REGULERING,
                        beskrivelse = it.toString(),
                    ),
                )
            }

        val reguleringerManuell = rights.filter { it.reguleringstype is Reguleringstype.MANUELL }.map {
            val årsak = (it.reguleringstype as Reguleringstype.MANUELL).problemer.first().kategori
            serialize(it.toResultat(årsak.name))
        }
        val reguleringerAutomatisk = rights.filter { it.reguleringstype is Reguleringstype.AUTOMATISK }.map {
            serialize(it.toResultat("Fullført automatisk"))
        }

        val reguleringKjøring = ReguleringKjøring(
            id = UUID.randomUUID(),
            aar = fraOgMedMåned.årOgMåned.monthValue,
            type = ReguleringKjøring.REGULERINGSTYPE_GRUNNBELØP,
            dryrun = testRun != null,
            startTid = startTid,
            sakerAntall = alleSaker.size,
            sakerIkkeLøpende = sakerIkkeLøpende,
            sakerAlleredeRegulert = sakerAlleredeRegulert,
            sakerMåRevurderes = sakerMåRevurderes,
            reguleringerSomFeilet = reguleringerSomFeilet,
            reguleringerAlleredeÅpen = reguleringerAlleredeÅpen,
            reguleringerManuell = reguleringerManuell,
            reguleringerAutomatisk = reguleringerAutomatisk,
        )
        reguleringKjøringRepo.lagre(reguleringKjøring)
    }

    private fun logResultat(it: List<Either<KunneIkkeRegulereAutomatisk, ReguleringOppsummering>>): String {
        val (lefts, rights) = it.split()

        val årsakerForAtReguleringerIkkeKunneBliOpprettet =
            lefts.map { it }.groupBy { it }.map { "${it.key}: ${it.value.size} " }

        val antallAutomatiskeReguleringer = rights.count { it.reguleringstype == Reguleringstype.AUTOMATISK }
        val antallAutomatiskPgaSupplemement = rights.count {
            it.reguleringstype == Reguleringstype.AUTOMATISK && it.harSupplementData
        }
        val manuelleReguleringer = rights.filter { it.reguleringstype is Reguleringstype.MANUELL }

        val årsakerForManuell = rights.filter { it.reguleringstype is Reguleringstype.MANUELL }.flatMap {
            (it.reguleringstype as Reguleringstype.MANUELL).problemer.map { it::class.simpleName }
        }.groupBy { it }.map { "${it.key}: ${it.value.size} " }

        val result = """
            Antall prosesserte saker: ${it.size}
            Antall reguleringer laget: ${rights.size}
            ------------------------------------------------------------------------------
            Antall reguleringer som ikke kunne bli opprettet: ${lefts.size}
            Årsaker til at reguleringene ikke kunne bli opprettet: ${
            if (årsakerForAtReguleringerIkkeKunneBliOpprettet.isEmpty()) {
                "[]"
            } else {
                årsakerForAtReguleringerIkkeKunneBliOpprettet.joinToString { "\n              - $it" }
            }
        }
            ------------------------------------------------------------------------------
            Antall reguleringer som ble kjørt igjennom automatisk: $antallAutomatiskeReguleringer
            Antall reguleringer som ble kjørt med supplement: $antallAutomatiskPgaSupplemement
            Av $antallAutomatiskeReguleringer automatiske, er $antallAutomatiskPgaSupplemement automatisk pga supplement
            ------------------------------------------------------------------------------
            Antall reguleringer til manuell behandling: ${manuelleReguleringer.size}
            Årsaker til manuell behandling: ${
            if (årsakerForManuell.isEmpty()) {
                "[]"
            } else {
                """${årsakerForManuell.joinToString("") { "\n              - $it" }}
            Mer detaljer om årsakene kan finnes i egne logg meldinger
                """
            }
        }
        """.trimIndent()

        return result.also {
            log.info(it)
            manuelleReguleringer.toCSVLoggableStringFraLoggdata().forEach { (årsak, csv) ->
                log.info("$årsak\n" + csv)
            }
        }
    }

    // TODO AUTO-REG-26 Utgår?
    override fun oppdaterReguleringerMedSupplement(supplement: Reguleringssupplement) {
        val reguleringerSomKanOppdateres = reguleringRepo.hentStatusForÅpneManuelleReguleringer()
        reguleringRepo.lagre(supplement)
        reguleringerSomKanOppdateres.forEach { reguleringssammendrag ->
            log.info("Oppdatering av regulering for sak ${reguleringssammendrag.saksnummer} starter...")

            Either.catch {
                val sak: Sak = Either.catch {
                    sakService.hentSak(reguleringssammendrag.saksnummer)
                        .getOrElse { throw RuntimeException("Inkluderer stacktrace") }
                }.getOrElse {
                    log.error(
                        "Regulering for saksnummer ${reguleringssammendrag.saksnummer}: Klarte ikke hente sak",
                        it,
                    )
                    return@forEach
                }

                val regulering = sak.reguleringer.hent(reguleringssammendrag.reguleringId)
                    ?: throw IllegalStateException("Fant ikke regulering med id ${reguleringssammendrag.reguleringId}")
                if (regulering !is ReguleringUnderBehandling) throw IllegalStateException("Fant ikke regulering med id ${reguleringssammendrag.reguleringId}")

                val søkersSupplement = supplement.getFor(regulering.fnr)
                val epsSupplement = regulering.grunnlagsdata.eps.mapNotNull { supplement.getFor(it) }

                val eksternSupplementRegulering =
                    EksternSupplementRegulering(supplement.id, søkersSupplement, epsSupplement)
                val omregningsfaktor = satsFactory.grunnbeløp(regulering.periode.fraOgMed).omregningsfaktor
                val oppdatertRegulering =
                    regulering.oppdaterMedSupplement(eksternSupplementRegulering, omregningsfaktor)

                if (oppdatertRegulering.reguleringstype is Reguleringstype.AUTOMATISK) {
                    forsøkAutomatiskReguleringEllerOverførTilManuell(oppdatertRegulering, sak, true)
                        .onRight { log.info("Regulering for saksnummer ${sak.saksnummer}: Ferdig. Reguleringen ble ferdigstilt automatisk") }
                        .mapLeft { feil -> KunneIkkeRegulereAutomatisk.KunneIkkeBehandleAutomatisk(feil = feil) }
                } else {
                    log.info("Oppdatering av regulering for saksnummer ${sak.saksnummer}. Reguleringen må behandles manuelt pga ${(regulering.reguleringstype as Reguleringstype.MANUELL).problemer}")
                    oppdatertRegulering.also {
                        reguleringRepo.lagre(it)
                    }
                }
            }.mapLeft {
                log.error("Feil ved oppdatering av regulering for saksnummer ${reguleringssammendrag.saksnummer}", it)
            }
        }
    }

    private fun forsøkAutomatiskReguleringEllerOverførTilManuell(
        regulering: ReguleringUnderBehandling,
        sak: Sak,
        isLiveRun: Boolean,
    ): Either<KunneIkkeBehandleRegulering, IverksattRegulering> {
        return reguleringService.behandleReguleringAutomatisk(
            regulering,
            sak,
            isLiveRun,
        ).onLeft {
            if (isLiveRun) {
                val message = when (it) {
                    is KunneIkkeBehandleRegulering.KunneIkkeBeregne -> "Klarte ikke å beregne reguleringen."
                    is KunneIkkeBehandleRegulering.KunneIkkeSimulere -> "Klarte ikke å simulere utbetalingen."
                    is KunneIkkeBehandleRegulering.KunneIkkeUtbetale -> "Klarte ikke å utbetale. Underliggende feil: ${it.feil}"
                }
                // TODO AUTO-REG-26 - Endre til manuell kun for forventa feil under behandling
                val manuellOpprettet = regulering.endreTilManuell(message)
                lagreOpprettetEllerOverførtTilManuellRegulering(sak, manuellOpprettet)
            }
        }
    }

    private fun lagreOpprettetEllerOverførtTilManuellRegulering(sak: Sak, regulering: ReguleringUnderBehandling) {
        sessionFactory.withTransactionContext { tx ->
            reguleringRepo.lagre(regulering, tx)
            if (regulering.reguleringstype is Reguleringstype.MANUELL) {
                val relatertId = sak.hentSisteInnvilgedeSøknadsbehandling()?.id?.value
                statistikkService.lagre(
                    StatistikkEvent.Behandling.Regulering.Opprettet(regulering, relatertId),
                    tx,
                )
            }
        }
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
