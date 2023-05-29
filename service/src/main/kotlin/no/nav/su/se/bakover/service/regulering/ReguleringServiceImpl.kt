package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.KunneIkkeBeregneRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeFerdigstilleOgIverksette
import no.nav.su.se.bakover.domain.regulering.KunneIkkeOppretteRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.LiveRun
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringMerknad
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.beregn.blirBeregningEndret
import no.nav.su.se.bakover.domain.regulering.opprettEllerOppdaterRegulering
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val sakRepo: SakRepo,
    private val utbetalingService: UtbetalingService,
    private val vedtakService: VedtakService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
    private val tilbakekrevingService: TilbakekrevingService,
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
    ): List<Either<KunneIkkeOppretteRegulering, Regulering>> {
        return Either.catch { start(fraOgMedMåned, true, satsFactory) }
            .mapLeft {
                log.error("Ukjent feil skjedde ved automatisk regulering for fraOgMedMåned: $fraOgMedMåned", it)
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
        Either.catch {
            start(
                fraOgMedMåned = command.fraOgMedMåned,
                isLiveRun = false,
                satsFactory = command.satsFactory,
            )
        }.onLeft {
            log.error("Ukjent feil skjedde ved automatisk regulering for innsyn for kommando: $command", it)
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
    ): List<Either<KunneIkkeOppretteRegulering, Regulering>> {
        return sakRepo.hentSakIdSaksnummerOgFnrForAlleSaker().map { (sakid, saksnummer, _) ->
            log.info("Regulering for saksnummer $saksnummer: Starter")

            val sak = Either.catch {
                sakRepo.hentSak(sakId = sakid) ?: return@map KunneIkkeOppretteRegulering.FantIkkeSak.left()
                    .also {
                        log.error(
                            "Regulering for saksnummer $saksnummer: Klarte ikke hente sak",
                            RuntimeException("Inkluderer stacktrace"),
                        )
                    }
            }.getOrElse {
                log.error("Regulering for saksnummer $saksnummer: Klarte ikke hente sak", it)
                return@map KunneIkkeOppretteRegulering.FantIkkeSak.left()
            }

            val regulering = sak.opprettEllerOppdaterRegulering(
                fraOgMedMåned = fraOgMedMåned,
                clock = clock,
            ).getOrElse { feil ->
                // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
                when (feil) {
                    Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode -> log.info(
                        "Regulering for saksnummer ${sak.saksnummer}: Skippet. Fantes ingen vedtak for valgt periode.",
                    )

                    Sak.KunneIkkeOppretteEllerOppdatereRegulering.BleIkkeLagetReguleringDaDenneUansettMåRevurderes, Sak.KunneIkkeOppretteEllerOppdatereRegulering.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig -> log.error(
                        "Regulering for saksnummer ${sak.saksnummer}: Skippet. Denne feilen må varsles til saksbehandler og håndteres manuelt. Årsak: $feil",
                    )

                    else -> TODO("fjern meg")
                }

                return@map KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(feil).left()
            }

            // TODO jah: Flytt inn i sak.opprettEllerOppdaterRegulering(...)
            if (!sak.blirBeregningEndret(regulering, satsFactory, clock)) {
                // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
                log.info("Regulering for saksnummer $saksnummer: Skippet. Lager ikke regulering da den ikke fører til noen endring i utbetaling")
                return@map KunneIkkeOppretteRegulering.FørerIkkeTilEnEndring.left()
            }

            tilbakekrevingService.hentAvventerKravgrunnlag(sak.id)
                .ifNotEmpty {
                    log.info("Regulering for saksnummer $saksnummer: Kan ikke sende oppdragslinjer mens vi venter på et kravgrunnlag, siden det kan annulere nåværende kravgrunnlag. Setter reguleringen til manuell.")
                    return@map regulering.copy(
                        reguleringstype = Reguleringstype.MANUELL(setOf(ÅrsakTilManuellRegulering.AvventerKravgrunnlag)),
                    ).right().onRight {
                        if (isLiveRun) {
                            LiveRun.Opprettet(
                                sessionFactory = sessionFactory,
                                lagreRegulering = reguleringRepo::lagre,
                                lagreVedtak = vedtakService::lagreITransaksjon,
                                klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                                notifyObservers = { Unit },
                            ).kjørSideffekter(it)
                        }
                    }
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

            if (regulering.reguleringstype is Reguleringstype.AUTOMATISK) {
                ferdigstillOgIverksettRegulering(regulering, sak, isLiveRun, satsFactory)
                    .onRight { log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen ble ferdigstilt automatisk") }
                    .mapLeft { feil -> KunneIkkeOppretteRegulering.KunneIkkeRegulereAutomatisk(feil = feil) }
            } else {
                log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen må behandles manuelt. ${(regulering.reguleringstype as Reguleringstype.MANUELL).problemer}")
                regulering.right()
            }
        }.also {
            logResultat(it)
        }
    }

    private fun logResultat(it: List<Either<KunneIkkeOppretteRegulering, Regulering>>) {
        val regulert = it.mapNotNull { regulering ->
            regulering.fold(ifLeft = { null }, ifRight = { it })
        }

        val årsaker = regulert
            .filter { regulering -> regulering.reguleringstype is Reguleringstype.MANUELL }
            .flatMap { (it.reguleringstype as Reguleringstype.MANUELL).problemer.toList() }
            .groupBy { it }
            .map { it.key to it.value.size }
            .joinToString { "${it.first}: ${it.second}" }

        val antallAutomatiske =
            regulert.filter { regulering -> regulering.reguleringstype is Reguleringstype.AUTOMATISK }.size
        val antallManuelle =
            regulert.filter { regulering -> regulering.reguleringstype is Reguleringstype.MANUELL }.size

        log.info("Totalt antall prosesserte reguleringer: ${regulert.size}, antall automatiske: $antallAutomatiske, antall manuelle: $antallManuelle, årsaker: $årsaker")
    }

    override fun regulerManuelt(
        reguleringId: UUID,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        fradrag: List<Grunnlag.Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeRegulereManuelt.FantIkkeRegulering.left()
        if (regulering.erFerdigstilt) return KunneIkkeRegulereManuelt.AlleredeFerdigstilt.left()

        val sak = sakRepo.hentSak(sakId = regulering.sakId) ?: return KunneIkkeRegulereManuelt.FantIkkeSak.left()
        val fraOgMed = regulering.periode.fraOgMed
        val gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
            fraOgMed = fraOgMed,
            clock = clock,
        )
            .getOrElse { throw RuntimeException("Feil skjedde under manuell regulering for saksnummer ${sak.saksnummer}. $it") }

        if (gjeldendeVedtaksdata.harStans()) {
            return KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres.left()
        }

        if (gjeldendeVedtaksdata.pågåendeAvkortingEllerBehovForFremtidigAvkorting) {
            return KunneIkkeRegulereManuelt.HarPågåendeEllerBehovForAvkorting.left()
        }

        if (tilbakekrevingService.hentAvventerKravgrunnlag(sak.id).isNotEmpty()) {
            return KunneIkkeRegulereManuelt.AvventerKravgrunnlag.left()
        }

        return sak.opprettEllerOppdaterRegulering(Måned.fra(fraOgMed), clock).mapLeft {
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
    }

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
                        "Regulering for saksnummer ${regulering.saksnummer}: Feilet. Beregning feilet.",
                        kunneikkeBeregne.feil,
                    )
                }
            }
            KunneIkkeFerdigstilleOgIverksette.KunneIkkeBeregne
        }
            .flatMap { beregnetRegulering ->
                beregnetRegulering.simuler { beregning, uføregrunnlag ->
                    sak.lagNyUtbetaling(
                        saksbehandler = beregnetRegulering.saksbehandler,
                        beregning = beregning,
                        clock = clock,
                        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling,
                        uføregrunnlag = uføregrunnlag,
                    ).let {
                        sak.simulerUtbetaling(
                            utbetalingForSimulering = it,
                            periode = regulering.periode,
                            simuler = utbetalingService::simulerUtbetaling,
                            kontrollerMotTidligereSimulering = regulering.simulering,
                        )
                    }.map { simulertUtbetaling ->
                        simulertUtbetaling.simulering
                    }
                }.mapLeft {
                    log.error("Regulering for saksnummer ${regulering.saksnummer}. Simulering feilet.")
                    KunneIkkeFerdigstilleOgIverksette.KunneIkkeSimulere
                }.flatMap {
                    if (it.simulering!!.harFeilutbetalinger()) {
                        log.error("Regulering for saksnummer ${regulering.saksnummer}: Simuleringen inneholdt feilutbetalinger.")
                        KunneIkkeFerdigstilleOgIverksette.KanIkkeAutomatiskRegulereSomFørerTilFeilutbetaling.left()
                    } else {
                        it.right()
                    }
                }
            }
            .map { simulertRegulering -> simulertRegulering.tilIverksatt() }
            .flatMap { lagVedtakOgUtbetal(it, sak, isLiveRun) }
            .onLeft {
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
                                    ÅrsakTilManuellRegulering.UtbetalingFeilet,
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
                ) { sakRepo.hentSak(vedtak.sakId)!! },
            )
        }
    }

    override fun avslutt(reguleringId: UUID): Either<KunneIkkeAvslutte, AvsluttetRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeAvslutte.FantIkkeRegulering.left()

        return when (regulering) {
            is AvsluttetRegulering, is IverksattRegulering -> KunneIkkeAvslutte.UgyldigTilstand.left()
            is OpprettetRegulering -> {
                val avsluttetRegulering = regulering.avslutt(clock)
                reguleringRepo.lagre(avsluttetRegulering)

                avsluttetRegulering.right()
            }
        }
    }

    override fun hentStatus(): List<Pair<Regulering, List<ReguleringMerknad>>> {
        val reguleringer = reguleringRepo.hentReguleringerSomIkkeErIverksatt()

        return reguleringer.map {
            val tilhørendeMerknader = listOfNotNull(
                if (it.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.any { it.fradragstype == Fradragstype.Fosterhjemsgodtgjørelse }) ReguleringMerknad.Fosterhjemsgodtgjørelse else null,
            )

            Pair(it, tilhørendeMerknader)
        }
    }

    override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
        return reguleringRepo.hentSakerMedÅpenBehandlingEllerStans()
    }

    private fun lagVedtakOgUtbetal(
        regulering: IverksattRegulering,
        sak: Sak,
        isLiveRun: Boolean,
    ): Either<KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale, IverksattRegulering> {
        return Either.catch {
            val utbetaling = sak.lagNyUtbetaling(
                saksbehandler = regulering.saksbehandler,
                beregning = regulering.beregning,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                uføregrunnlag = when (regulering.sakstype) {
                    Sakstype.ALDER -> {
                        null
                    }

                    Sakstype.UFØRE -> {
                        regulering.vilkårsvurderinger.uføreVilkår()
                            .getOrElse { throw IllegalStateException("Regulering uføre: ${regulering.id} mangler uføregrunnlag") }
                            .grunnlag
                            .toNonEmptyList()
                    }
                },
            ).let {
                sak.simulerUtbetaling(
                    utbetalingForSimulering = it,
                    periode = regulering.periode,
                    simuler = utbetalingService::simulerUtbetaling,
                    kontrollerMotTidligereSimulering = regulering.simulering,
                )
            }.getOrElse { feil ->
                throw KunneIkkeSendeTilUtbetalingException(UtbetalingFeilet.KunneIkkeSimulere(feil))
            }

            if (isLiveRun) {
                LiveRun.Iverksatt(
                    sessionFactory = sessionFactory,
                    lagreRegulering = reguleringRepo::lagre,
                    lagreVedtak = vedtakService::lagreITransaksjon,
                    klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                    notifyObservers = { vedtakInnvilgetRegulering -> notifyObservers(vedtakInnvilgetRegulering) },
                )
                    .kjørSideffekter(regulering, utbetaling, clock)
            }
        }.mapLeft {
            log.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering",
                it,
            )
            KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale
        }.map {
            regulering
        }
    }

    private data class KunneIkkeSendeTilUtbetalingException(val feil: UtbetalingFeilet) : RuntimeException()
}
