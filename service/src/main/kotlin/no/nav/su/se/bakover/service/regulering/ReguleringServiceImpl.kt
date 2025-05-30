package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.regulering.domain.beregning.KunneIkkeBeregneRegulering
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.split
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.KunneIkkeFerdigstilleOgIverksette
import no.nav.su.se.bakover.domain.regulering.KunneIkkeOppretteRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.LiveRun
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.beregn.blirBeregningEndret
import no.nav.su.se.bakover.domain.regulering.opprettEllerOppdaterRegulering
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import satser.domain.supplerendestønad.grunnbeløpsendringer
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.VilkårsvurderingerHarUlikePeriode
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.math.BigDecimal
import java.time.Clock

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val sakService: SakService,
    private val utbetalingService: UtbetalingService,
    private val vedtakService: VedtakService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
) : ReguleringService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<StatistikkEventObserver> = observers.toList()

    override fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
        /**
         * Inneholder data for alle sakene
         */
        supplement: Reguleringssupplement,
    ): List<Either<KunneIkkeOppretteRegulering, Regulering>> {
        val omregningsfaktor = satsFactory.grunnbeløp(fraOgMedMåned).omregningsfaktor

        reguleringRepo.lagre(supplement)
        return Either.catch { start(fraOgMedMåned, true, satsFactory, supplement, omregningsfaktor) }
            .mapLeft {
                log.error(
                    "Ukjent feil skjedde ved automatisk regulering for fraOgMedMåned: $fraOgMedMåned. Se sikkerlogg for feilmelding.",
                    RuntimeException("Inkluderer stacktrace"),
                )
                sikkerLogg.error("Ukjent feil skjedde ved automatisk regulering for fraOgMedMåned: $fraOgMedMåned", it)
                KunneIkkeOppretteRegulering.UkjentFeil
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
                isLiveRun = false,
                satsFactory = factory,
                supplement = command.supplement,
                omregningsfaktor = factory.grunnbeløp(command.gjeldendeSatsFra).omregningsfaktor,
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
        isLiveRun: Boolean,
        satsFactory: SatsFactory,
        supplement: Reguleringssupplement,
        omregningsfaktor: BigDecimal,
    ): List<Either<KunneIkkeOppretteRegulering, Regulering>> {
        return sakService.hentSakIdSaksnummerOgFnrForAlleSaker().map { (sakid, saksnummer, _) ->
            log.info("Regulering for saksnummer $saksnummer: Starter")

            val sak: Sak = Either.catch {
                sakService.hentSak(sakId = sakid).getOrElse { throw RuntimeException("Inkluderer stacktrace") }
            }.getOrElse {
                log.error("Regulering for saksnummer $saksnummer: Klarte ikke hente sak $sakid", it)
                return@map KunneIkkeOppretteRegulering.FantIkkeSak.left()
            }
            sak.kjørForSak(
                fraOgMedMåned = fraOgMedMåned,
                isLiveRun = isLiveRun,
                satsFactory = satsFactory,
                supplement = supplement,
                omregningsfaktor = omregningsfaktor,
            )
        }
            .also {
                logResultat(it)
            }
    }

    private fun Sak.kjørForSak(
        fraOgMedMåned: Måned,
        isLiveRun: Boolean,
        satsFactory: SatsFactory,
        supplement: Reguleringssupplement,
        omregningsfaktor: BigDecimal,
    ): Either<KunneIkkeOppretteRegulering, Regulering> {
        val sak = this

        val regulering = sak.opprettEllerOppdaterRegulering(
            fraOgMedMåned = fraOgMedMåned,
            clock = clock,
            supplement = supplement,
            omregningsfaktor = omregningsfaktor,
        ).getOrElse { feil ->
            // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
            when (feil) {
                Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode -> log.info(
                    "Regulering for saksnummer ${sak.saksnummer}: Skippet. Fantes ingen vedtak for valgt periode.",
                )

                Sak.KunneIkkeOppretteEllerOppdatereRegulering.BleIkkeLagetReguleringDaDenneUansettMåRevurderes, Sak.KunneIkkeOppretteEllerOppdatereRegulering.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig -> log.error(
                    "Regulering for saksnummer ${sak.saksnummer}: Skippet. Denne feilen må varsles til saksbehandler og håndteres manuelt. Årsak: $feil",
                )
            }

            return KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(feil).left()
        }

        // TODO jah: Flytt inn i sak.opprettEllerOppdaterRegulering(...)
        if (!sak.blirBeregningEndret(regulering, satsFactory, clock)) {
            // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
            log.info("Regulering for saksnummer $saksnummer: Skippet. Lager ikke regulering da den ikke fører til noen endring i utbetaling")
            return KunneIkkeOppretteRegulering.FørerIkkeTilEnEndring.left()
        }

        if (isLiveRun) {
            LiveRun.Opprettet(
                sessionFactory = sessionFactory,
                lagreRegulering = reguleringRepo::lagre,
                lagreVedtak = vedtakService::lagreITransaksjon,
                klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                notifyObservers = { Unit },
            ).kjørSideffekter(regulering)
        }

        return if (regulering.reguleringstype is Reguleringstype.AUTOMATISK) {
            ferdigstillOgIverksettRegulering(regulering, sak, isLiveRun, satsFactory)
                .onRight { log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen ble ferdigstilt automatisk") }
                .mapLeft { feil -> KunneIkkeOppretteRegulering.KunneIkkeRegulereAutomatisk(feil = feil) }
        } else {
            log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen må behandles manuelt. ${(regulering.reguleringstype as Reguleringstype.MANUELL).problemer}")
            regulering.right()
        }
    }

    private fun logResultat(it: List<Either<KunneIkkeOppretteRegulering, Regulering>>): String {
        val (lefts, rights) = it.split()

        val årsakerForAtReguleringerIkkeKunneBliOpprettet =
            lefts.map { it }.groupBy { it }.map { "${it.key}: ${it.value.size} " }

        val antallAutomatiskeReguleringer = rights.count { it.reguleringstype == Reguleringstype.AUTOMATISK }
        val antallAutomatiskPgaSupplemement = rights.count {
            it.reguleringstype == Reguleringstype.AUTOMATISK && (it.eksternSupplementRegulering.bruker != null || it.eksternSupplementRegulering.eps.isNotEmpty())
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
            manuelleReguleringer.toCSVLoggableString().forEach { (årsak, csv) ->
                log.info("$årsak\n" + csv)
            }
        }
    }

    override fun regulerManuelt(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeRegulereManuelt.FantIkkeRegulering.left()
        if (regulering.erFerdigstilt) return KunneIkkeRegulereManuelt.AlleredeFerdigstilt.left()

        val sak = sakService.hentSak(sakId = regulering.sakId)
            .getOrElse { return KunneIkkeRegulereManuelt.FantIkkeSak.left() }
        val fraOgMed = regulering.periode.fraOgMed
        val gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
            fraOgMed = fraOgMed,
            clock = clock,
        )
            .getOrElse { throw RuntimeException("Feil skjedde under manuell regulering for saksnummer ${sak.saksnummer}. $it") }

        if (gjeldendeVedtaksdata.harStans()) {
            return KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres.left()
        }

        try {
            return sak.opprettEllerOppdaterRegulering(
                Måned.fra(fraOgMed),
                clock,
                Reguleringssupplement.empty(clock),
                (grunnbeløpsendringer.last().verdi - grunnbeløpsendringer[grunnbeløpsendringer.size - 1].verdi).toBigDecimal(),
            ).mapLeft {
                throw RuntimeException("Feil skjedde under manuell regulering for saksnummer ${sak.saksnummer}. $it")
            }.map { opprettetRegulering ->
                return opprettetRegulering
                    .copy(reguleringstype = opprettetRegulering.reguleringstype)
                    .leggTilFradrag(fradrag)
                    .leggTilUføre(uføregrunnlag, clock)
                    .leggTilSaksbehandler(saksbehandler)
                    .let {
                        ferdigstillOgIverksettRegulering(it, sak, true, satsFactory)
                            .mapLeft { feil -> KunneIkkeRegulereManuelt.KunneIkkeFerdigstille(feil = feil) }
                    }
            }
        } catch (e: VilkårsvurderingerHarUlikePeriode) {
            // Saksbehandler løser dette manuelt. Hvis det skjer ofte kan det vurderes en automatisk løsning.
            log.error("Manuell regulering for sak=${sak.id} feilet på grunn av VilkårsvurderingerHarUlikePeriode", e)
            return KunneIkkeRegulereManuelt.ReguleringHarUtdatertePeriode.left()
        }
    }

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

                val søkersSupplement = supplement.getFor(regulering.fnr)
                val epsSupplement = regulering.grunnlagsdata.eps.mapNotNull { supplement.getFor(it) }

                val eksternSupplementRegulering =
                    EksternSupplementRegulering(supplement.id, søkersSupplement, epsSupplement)
                val omregningsfaktor = satsFactory.grunnbeløp(regulering.periode.fraOgMed).omregningsfaktor
                val oppdatertRegulering =
                    regulering.oppdaterMedSupplement(eksternSupplementRegulering, omregningsfaktor)

                if (oppdatertRegulering.reguleringstype is Reguleringstype.AUTOMATISK) {
                    ferdigstillOgIverksettRegulering(oppdatertRegulering, sak, true, satsFactory)
                        .onRight { log.info("Regulering for saksnummer ${sak.saksnummer}: Ferdig. Reguleringen ble ferdigstilt automatisk") }
                        .mapLeft { feil -> KunneIkkeOppretteRegulering.KunneIkkeRegulereAutomatisk(feil = feil) }
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

    /**
     * Lagrer reguleringen
     */
    private fun ferdigstillOgIverksettRegulering(
        regulering: OpprettetRegulering,
        sak: Sak,
        isLiveRun: Boolean,
        satsFactory: SatsFactory,
    ): Either<KunneIkkeFerdigstilleOgIverksette, IverksattRegulering> {
        return regulering.beregn(
            satsFactory = satsFactory,
            begrunnelse = null,
            clock = clock,
        ).mapLeft { kunneikkeBeregne ->
            when (kunneikkeBeregne) {
                is KunneIkkeBeregneRegulering.BeregningFeilet -> {
                    log.error(
                        "Ferdigstilling/iverksetting regulering: Beregning feilet for regulering ${regulering.id} for sak ${regulering.saksnummer} og eguleringstype: ${regulering.reguleringstype::class.simpleName}",
                        kunneikkeBeregne.feil,
                    )
                }
            }
            KunneIkkeFerdigstilleOgIverksette.KunneIkkeBeregne
        }
            .flatMap { beregnetRegulering ->
                beregnetRegulering.simuler { beregning, uføregrunnlag ->
                    Either.catch {
                        sak.lagNyUtbetaling(
                            saksbehandler = beregnetRegulering.saksbehandler,
                            beregning = beregning,
                            clock = clock,
                            utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling,
                            uføregrunnlag = uføregrunnlag,
                        ).let {
                            simulerUtbetaling(
                                tidligereUtbetalinger = sak.utbetalinger,
                                utbetalingForSimulering = it,
                                simuler = utbetalingService::simulerUtbetaling,
                            )
                        }
                    }.fold(
                        {
                            log.error(
                                "Ferdigstilling/iverksetting regulering: Fikk exception ved generering av ny utbetaling / simulering av utbetaling for regulering ${regulering.id} for sak ${regulering.saksnummer} og reguleringstype: ${regulering.reguleringstype::class.simpleName}",
                                it,
                            )
                            SimuleringFeilet.TekniskFeil.left()
                        },
                        { it },
                    )
                }.mapLeft {
                    log.error("Ferdigstilling/iverksetting regulering: Simulering feilet for regulering ${regulering.id} for sak ${regulering.saksnummer} og reguleringstype: ${regulering.reguleringstype::class.simpleName}")
                    KunneIkkeFerdigstilleOgIverksette.KunneIkkeSimulere
                }
            }
            .map { (simulertRegulering, simulertUtbetaling) ->
                simulertRegulering.tilIverksatt() to simulertUtbetaling
            }.flatMap { (iverksattRegulering, simulertUtbetaling) ->
                lagVedtakOgUtbetal(iverksattRegulering, simulertUtbetaling, isLiveRun)
            }
            .onLeft {
                val message = when (it) {
                    is KunneIkkeFerdigstilleOgIverksette.KunneIkkeBeregne -> "Klarte ikke å beregne reguleringen."
                    is KunneIkkeFerdigstilleOgIverksette.KunneIkkeSimulere -> "Klarte ikke å simulere utbetalingen."
                    is KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale -> "Klarte ikke å utbetale. Underliggende feil: ${it.feil}"
                }
                if (isLiveRun) {
                    LiveRun.Opprettet(
                        sessionFactory = sessionFactory,
                        lagreRegulering = reguleringRepo::lagre,
                        lagreVedtak = vedtakService::lagreITransaksjon,
                        klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                        notifyObservers = { Unit },
                    ).kjørSideffekter(
                        regulering.copy(
                            reguleringstype = Reguleringstype.MANUELL(
                                setOf(
                                    ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet(begrunnelse = message),
                                ),
                            ),
                        ),
                    )
                }
            }
            .map {
                it
            }
    }

    private fun notifyObservers(vedtak: VedtakInnvilgetRegulering) {
        // TODO jah: Vi har gjort endringer på saken underveis - endret regulering, ny utbetaling og nytt vedtak - uten at selve saken blir oppdatert underveis. Når saken returnerer en oppdatert versjon av seg selv for disse tilfellene kan vi fjerne det ekstra kallet til hentSak.
        observers.forEach { observer ->
            observer.handle(
                StatistikkEvent.Stønadsvedtak(
                    vedtak,
                ) { sakService.hentSak(vedtak.sakId).getOrNull()!! },
            )
        }
    }

    override fun avslutt(
        reguleringId: ReguleringId,
        avsluttetAv: NavIdentBruker,
    ): Either<KunneIkkeAvslutte, AvsluttetRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeAvslutte.FantIkkeRegulering.left()

        return when (regulering) {
            is AvsluttetRegulering, is IverksattRegulering -> KunneIkkeAvslutte.UgyldigTilstand.left()
            is OpprettetRegulering -> {
                val avsluttetRegulering = regulering.avslutt(avsluttetAv, clock)
                reguleringRepo.lagre(avsluttetRegulering)

                avsluttetRegulering.right()
            }
        }
    }

    override fun hentStatusForÅpneManuelleReguleringer(): List<ReguleringSomKreverManuellBehandling> {
        return reguleringRepo.hentStatusForÅpneManuelleReguleringer()
    }

    override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
        return reguleringRepo.hentSakerMedÅpenBehandlingEllerStans()
    }

    private fun lagVedtakOgUtbetal(
        regulering: IverksattRegulering,
        simulertUtbetaling: Utbetaling.SimulertUtbetaling,
        isLiveRun: Boolean,
    ): Either<KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale, IverksattRegulering> {
        return Either.catch {
            if (isLiveRun) {
                LiveRun.Iverksatt(
                    sessionFactory = sessionFactory,
                    lagreRegulering = reguleringRepo::lagre,
                    lagreVedtak = vedtakService::lagreITransaksjon,
                    klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                    notifyObservers = { vedtakInnvilgetRegulering -> notifyObservers(vedtakInnvilgetRegulering) },
                ).kjørSideffekter(regulering, simulertUtbetaling, clock)
            }
        }.mapLeft {
            log.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
            )
            sikkerLogg.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
                it,
            )
            if (it is KunneIkkeFerdigstilleIverksettelsestransaksjon) {
                KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale(it)
            } else {
                KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale(
                    KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil(
                        it,
                    ),
                )
            }
        }.map {
            regulering
        }
    }
}
