package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.hentGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringMerknad
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.inneholderAvslag
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
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

    private fun blirBeregningEndret(sak: Sak, regulering: Regulering.OpprettetRegulering): Boolean {
        if (regulering.inneholderAvslag()) return true

        val reguleringMedBeregning = regulering.beregn(
            satsFactory = satsFactory,
            begrunnelse = null,
            clock = clock,
        ).getOrHandle {
            when (it) {
                is Regulering.KunneIkkeBeregne.BeregningFeilet -> {
                    throw RuntimeException("Regulering for saksnummer ${regulering.saksnummer}: Vi klarte ikke å beregne. Underliggende grunn ${it.feil}")
                }

                is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> {
                    throw RuntimeException("Regulering for saksnummer ${regulering.saksnummer}: Vi klarte ikke å beregne. Feil status")
                }
            }
        }

        return !reguleringMedBeregning.beregning!!.getMånedsberegninger().all { månedsberegning ->
            sak.hentGjeldendeUtbetaling(
                forDato = månedsberegning.periode.fraOgMed,
                clock = clock,
            ).fold(
                { false },
                { månedsberegning.getSumYtelse() == it.beløp },
            )
        }
    }

    override fun startRegulering(startDato: LocalDate): List<Either<KunneIkkeOppretteRegulering, Regulering>> {
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
            }.getOrHandle {
                log.error("Regulering for saksnummer $saksnummer: Klarte ikke hente sak", it)
                return@map KunneIkkeOppretteRegulering.FantIkkeSak.left()
            }

            val regulering = sak.opprettEllerOppdaterRegulering(
                startDato = startDato,
                clock = clock,
            ).getOrHandle { feil ->
                // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
                when (feil) {
                    Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode -> log.info(
                        "Regulering for saksnummer $saksnummer: Skippet. Fantes ingen vedtak for valgt periode.",
                    )

                    Sak.KunneIkkeOppretteEllerOppdatereRegulering.BleIkkeLagetReguleringDaDenneUansettMåRevurderes, Sak.KunneIkkeOppretteEllerOppdatereRegulering.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig -> log.error(
                        "Regulering for saksnummer $saksnummer: Skippet. Denne feilen må varsles til saksbehandler og håndteres manuelt. Årsak: $feil",
                    )

                    Sak.KunneIkkeOppretteEllerOppdatereRegulering.HarÅpenBehandling -> log.info(
                        "Regulering for saksnummer $saksnummer: Skippet. Fantes en åpen behandling",
                    )
                }

                return@map KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(feil).left()
            }

            if (!blirBeregningEndret(sak, regulering)) {
                // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
                log.info("Regulering for saksnummer $saksnummer: Skippet. Lager ikke regulering da den ikke fører til noen endring i utbetaling")
                return@map KunneIkkeOppretteRegulering.FørerIkkeTilEnEndring.left()
            }

            tilbakekrevingService.hentAvventerKravgrunnlag(sak.id)
                .ifNotEmpty {
                    log.info("Regulering for saksnummer $saksnummer: Kan ikke sende oppdragslinjer mens vi venter på et kravgrunnlag, siden det kan annulere nåværende kravgrunnlag. Setter reguleringen til manuell.")
                    return@map regulering.copy(
                        reguleringstype = Reguleringstype.MANUELL(setOf(ÅrsakTilManuellRegulering.AvventerKravgrunnlag)),
                    ).right().tap {
                        reguleringRepo.lagre(it)
                    }
                }

            reguleringRepo.lagre(regulering)

            if (regulering.reguleringstype is Reguleringstype.AUTOMATISK) {
                ferdigstillOgIverksettRegulering(regulering, sak)
                    .tap { log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen ble ferdigstilt automatisk") }
                    .mapLeft { feil -> KunneIkkeOppretteRegulering.KunneIkkeRegulereAutomatisk(feil = feil) }
            } else {
                log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen må behandles manuelt.")
                regulering.right()
            }
        }.also {
            val regulert = it.mapNotNull { regulering ->
                regulering.fold(ifLeft = { null }, ifRight = { it })
            }
            val antallAutomatiske =
                regulert.filter { regulering -> regulering.reguleringstype is Reguleringstype.AUTOMATISK }.size
            val antallManuelle =
                regulert.filter { regulering -> regulering.reguleringstype is Reguleringstype.MANUELL }.size

            log.info("Totalt antall prosesserte reguleringer: ${regulert.size}, antall automatiske: $antallAutomatiske, antall manuelle: $antallManuelle")
        }
    }

    override fun regulerManuelt(
        reguleringId: UUID,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        fradrag: List<Grunnlag.Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, Regulering.IverksattRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeRegulereManuelt.FantIkkeRegulering.left()
        if (regulering.erFerdigstilt) return KunneIkkeRegulereManuelt.AlleredeFerdigstilt.left()

        val sak = sakRepo.hentSak(sakId = regulering.sakId) ?: return KunneIkkeRegulereManuelt.FantIkkeSak.left()
        val gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
            fraOgMed = regulering.periode.fraOgMed,
            clock = clock,
        )
            .getOrHandle { throw RuntimeException("Feil skjedde under manuell regulering for saksnummer ${sak.saksnummer}. $it") }

        if (gjeldendeVedtaksdata.harStans()) {
            return KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres.left()
        }

        if (gjeldendeVedtaksdata.pågåendeAvkortingEllerBehovForFremtidigAvkorting) {
            return KunneIkkeRegulereManuelt.HarPågåendeEllerBehovForAvkorting.left()
        }

        if (tilbakekrevingService.hentAvventerKravgrunnlag(sak.id).isNotEmpty()) {
            return KunneIkkeRegulereManuelt.AvventerKravgrunnlag.left()
        }

        return sak.opprettEllerOppdaterRegulering(regulering.periode.fraOgMed, clock).mapLeft {
            return when (it) {
                Sak.KunneIkkeOppretteEllerOppdatereRegulering.HarÅpenBehandling -> KunneIkkeRegulereManuelt.HarÅpenBehandling.left()
                else -> throw RuntimeException("Feil skjedde under manuell regulering for saksnummer ${sak.saksnummer}. $it")
            }
        }.map { opprettetRegulering ->
            return opprettetRegulering
                .copy(reguleringstype = opprettetRegulering.reguleringstype)
                .leggTilFradrag(fradrag)
                .leggTilUføre(uføregrunnlag, clock)
                .leggTilSaksbehandler(saksbehandler)
                .let {
                    ferdigstillOgIverksettRegulering(it, sak)
                        .mapLeft { feil -> KunneIkkeRegulereManuelt.KunneIkkeFerdigstille(feil = feil) }
                }
        }
    }

    private fun ferdigstillOgIverksettRegulering(regulering: Regulering.OpprettetRegulering, sak: Sak): Either<KunneIkkeFerdigstilleOgIverksette, Regulering.IverksattRegulering> {
        return regulering.beregn(
            satsFactory = satsFactory,
            begrunnelse = null,
            clock = clock,
        ).mapLeft { kunneikkeBeregne ->
            when (kunneikkeBeregne) {
                is Regulering.KunneIkkeBeregne.BeregningFeilet -> {
                    log.error(
                        "Regulering for saksnummer ${regulering.saksnummer}: Feilet. Beregning feilet.",
                        kunneikkeBeregne.feil,
                    )
                }

                is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> {
                    log.error("Regulering for saksnummer ${regulering.saksnummer}: Feilet. Beregning feilet. Ikke lov å beregne i denne statusen")
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
                            simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                                utbetalingService.simulerUtbetaling(
                                    utbetalingForSimulering,
                                    periode,
                                )
                            },
                            kontrollerMotTidligereSimulering = regulering.simulering,
                            clock = clock,
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
            .flatMap { lagVedtakOgUtbetal(it, sak) }
            .tapLeft {
                reguleringRepo.lagre(
                    regulering.copy(
                        reguleringstype = Reguleringstype.MANUELL(
                            setOf(
                                ÅrsakTilManuellRegulering.UtbetalingFeilet,
                            ),
                        ),
                    ),
                )
            }
            .map {
                val (iverksattRegulering, vedtak) = it

                Either.catch {
                    observers.forEach { observer -> observer.handle(StatistikkEvent.Stønadsvedtak(vedtak)) }
                }.tapLeft {
                    log.error(
                        "Regulering for saksnummer ${iverksattRegulering.saksnummer}: Utsending av stønadsstatistikk feilet under automatisk regulering.",
                        it,
                    )
                }

                iverksattRegulering
            }
    }

    override fun avslutt(reguleringId: UUID): Either<KunneIkkeAvslutte, Regulering.AvsluttetRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeAvslutte.FantIkkeRegulering.left()

        return when (regulering) {
            is Regulering.AvsluttetRegulering, is Regulering.IverksattRegulering -> KunneIkkeAvslutte.UgyldigTilstand.left()
            is Regulering.OpprettetRegulering -> {
                val avsluttetRegulering = regulering.avslutt(clock)
                reguleringRepo.lagre(avsluttetRegulering)

                avsluttetRegulering.right()
            }
        }
    }

    override fun hentStatus(): List<Pair<Regulering, List<ReguleringMerknad>>> {
        val reguleringer = reguleringRepo.hentReguleringerSomIkkeErIverksatt()
        val sakerSomAvventerForhåndsvarsel = sakRepo.hentSakerSomVenterPåForhåndsvarsling()

        return reguleringer.map {
            val tilhørendeMerknader = listOfNotNull(
                if (it.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.any { it.fradragstype == Fradragstype.Fosterhjemsgodtgjørelse }) ReguleringMerknad.Fosterhjemsgodtgjørelse else null,
                if (sakerSomAvventerForhåndsvarsel.contains(it.saksnummer)) ReguleringMerknad.VenterPåSvarFraForhåndsvarsel else null,
            )

            Pair(it, tilhørendeMerknader)
        }
    }

    override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
        return reguleringRepo.hentSakerMedÅpenBehandlingEllerStans()
    }

    private fun lagVedtakOgUtbetal(regulering: Regulering.IverksattRegulering, sak: Sak): Either<KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale, Pair<Regulering.IverksattRegulering, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering>> {
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
                            .getOrHandle { throw IllegalStateException("Regulering uføre: ${regulering.id} mangler uføregrunnlag") }
                            .grunnlag
                            .toNonEmptyList()
                    }
                },
            ).let {
                sak.simulerUtbetaling(
                    utbetalingForSimulering = it,
                    periode = regulering.periode,
                    simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                        utbetalingService.simulerUtbetaling(
                            utbetalingForSimulering,
                            periode,
                        )
                    },
                    kontrollerMotTidligereSimulering = regulering.simulering,
                    clock = clock,
                )
            }.getOrHandle { feil ->
                throw KunneIkkeSendeTilUtbetalingException(UtbetalingFeilet.KunneIkkeSimulere(feil))
            }
            sessionFactory.withTransactionContext { tx ->
                val nyUtbetaling = utbetalingService.klargjørNyUtbetaling(
                    utbetaling = utbetaling,
                    transactionContext = tx,
                ).getOrHandle {
                    throw KunneIkkeSendeTilUtbetalingException(it)
                }

                val vedtak = VedtakSomKanRevurderes.from(
                    regulering = regulering,
                    utbetalingId = nyUtbetaling.utbetaling.id,
                    clock = clock,
                )

                vedtakService.lagre(
                    vedtak = vedtak,
                    sessionContext = tx,
                )
                reguleringRepo.lagre(
                    regulering = regulering,
                    sessionContext = tx,
                )
                nyUtbetaling.sendUtbetaling()
                    .getOrHandle { throw KunneIkkeSendeTilUtbetalingException(it) }

                vedtak
            }
        }.mapLeft {
            log.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering",
                it,
            )
            KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale
        }.map {
            Pair(regulering, it)
        }
    }

    private data class KunneIkkeSendeTilUtbetalingException(val feil: UtbetalingFeilet) : RuntimeException()
}
