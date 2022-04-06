package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.hentGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.inneholderAvslag
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
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
) : ReguleringService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    private fun blirBeregningEndret(sak: Sak, regulering: Regulering.OpprettetRegulering): Boolean {
        if (regulering.inneholderAvslag()) return true

        val reguleringMedBeregning = regulering.beregn(clock = clock, begrunnelse = null)
            .getOrHandle {
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

            val regulering = sak.opprettEllerOppdaterRegulering(startDato, clock).getOrHandle { feil ->
                // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
                log.info("Regulering for saksnummer $saksnummer: Skippet. Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer")
                return@map KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(feil).left()
            }

            if (!blirBeregningEndret(sak, regulering)) {
                // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
                return@map KunneIkkeOppretteRegulering.FørerIkkeTilEnEndring.left()
                    .also { log.info("Regulering for saksnummer $saksnummer: Skippet. Lager ikke regulering for $saksnummer, da den ikke fører til noen endring i utbetaling") }
            }

            tilbakekrevingService.hentAvventerKravgrunnlag(sak.id)
                .ifNotEmpty {
                    log.info("Regulering for saksnummer $saksnummer: Kan ikke sende oppdragslinjer mens vi venter på et kravgrunnlag, siden det kan annulere nåværende kravgrunnlag. Setter reguleringen til manuell.")
                    return@map regulering.copy(
                        reguleringstype = Reguleringstype.MANUELL,
                    ).right().tap {
                        reguleringRepo.lagre(regulering)
                    }
                }

            reguleringRepo.lagre(regulering)

            if (regulering.reguleringstype == Reguleringstype.AUTOMATISK) {
                regulerAutomatisk(regulering)
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
            val antallAutomatiske = regulert.filter { regulering -> regulering.reguleringstype == Reguleringstype.AUTOMATISK }.size
            val antallManuelle = regulert.filter { regulering -> regulering.reguleringstype == Reguleringstype.MANUELL }.size

            log.info("Totalt antall prosesserte reguleringer: ${regulert.size}, antall automatiske: $antallAutomatiske, antall manuelle: $antallManuelle")
        }
    }

    private fun regulerAutomatisk(regulering: Regulering.OpprettetRegulering): Either<KunneIkkeRegulereAutomatisk, Regulering.IverksattRegulering> {
        return regulering.beregn(clock = clock, begrunnelse = null)
            .mapLeft { kunneikkeBeregne ->
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
                KunneIkkeRegulereAutomatisk.KunneIkkeBeregne
            }
            .flatMap { beregnetRegulering ->
                beregnetRegulering.simuler(utbetalingService::simulerUtbetaling)
                    .mapLeft {
                        log.error("Regulering for saksnummer ${regulering.saksnummer}. Simulering feilet.")
                        KunneIkkeRegulereAutomatisk.KunneIkkeSimulere
                    }.flatMap {
                        if (it.simulering!!.harFeilutbetalinger()) {
                            log.error("Regulering for saksnummer ${regulering.saksnummer}: Simuleringen inneholdt feilutbetalinger.")
                            KunneIkkeRegulereAutomatisk.KanIkkeAutomatiskRegulereSomFørerTilFeilutbetaling.left()
                        } else {
                            it.right()
                        }
                    }
            }
            .map { simulertRegulering -> simulertRegulering.tilIverksatt() }
            .flatMap { lagVedtakOgUtbetal(it) }
            .tapLeft { reguleringRepo.lagre(regulering.copy(reguleringstype = Reguleringstype.MANUELL)) }
            .map {
                val (iverksattRegulering, vedtak) = it
                observers.forEach { observer -> observer.handle(Event.Statistikk.Vedtaksstatistikk(vedtak)) }

                iverksattRegulering
            }
    }

    /**
     * Denne brukes kun i den manuelle flyten som ikke er implementert ferdig enda.
     */
    override fun leggTilFradrag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradrag, Regulering> {
        reguleringRepo.hent(request.behandlingId)?.let { regulering ->
            return when (regulering) {
                is Regulering.IverksattRegulering -> {
                    KunneIkkeLeggeTilFradrag.ReguleringErAlleredeIverksatt.left()
                }
                is Regulering.OpprettetRegulering -> {
                    regulering.leggTilFradrag(request.fradragsgrunnlag)
                    reguleringRepo.lagre(regulering)
                    regulering.right()
                }
            }
        }
        return KunneIkkeLeggeTilFradrag.FantIkkeRegulering.left()
    }

    /**
     * Denne brukes kun i den manuelle flyten som ikke er implementert ferdig enda.
     */
    override fun beregnOgSimuler(request: BeregnRequest): Either<BeregnOgSimulerFeilet, Regulering.OpprettetRegulering> {
        val regulering =
            (reguleringRepo.hent(request.behandlingId) as? Regulering.OpprettetRegulering)
                ?: return BeregnOgSimulerFeilet.FantIkkeRegulering.left()

        val beregnetRegulering = regulering.beregn(clock = clock, begrunnelse = request.begrunnelse).getOrHandle {
            when (it) {
                is Regulering.KunneIkkeBeregne.BeregningFeilet -> log.error(
                    "Regulering for saksnummer ${regulering.saksnummer}: Feilet. Kunne ikke beregne",
                    it.feil,
                )
                is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> log.error("Regulering for saksnummer ${regulering.saksnummer}: Feilet. Kan ikke beregne i status ${it.status}")
            }
            return BeregnOgSimulerFeilet.KunneIkkeBeregne.left()
        }

        return beregnetRegulering.simuler(utbetalingService::simulerUtbetaling)
            .mapLeft { BeregnOgSimulerFeilet.KunneIkkeSimulere }
    }

    override fun hentStatus(): List<Regulering> {
        return reguleringRepo.hentReguleringerSomIkkeErIverksatt()
    }

    override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
        return reguleringRepo.hentSakerMedÅpenBehandlingEllerStans()
    }

    /**
     * Denne brukes kun i den manuelle flyten som ikke er implementert ferdig enda.
     */
    override fun iverksett(reguleringId: UUID): Either<KunneIkkeIverksetteRegulering, Regulering> {
        reguleringRepo.hent(reguleringId)?.let { regulering ->
            return when (regulering) {
                is Regulering.IverksattRegulering -> {
                    KunneIkkeIverksetteRegulering.ReguleringErAlleredeIverksatt.left()
                }
                is Regulering.OpprettetRegulering -> {
                    regulering.tilIverksatt().also { iverksattRegulering ->
                        reguleringRepo.lagre(iverksattRegulering)
                    }.right()
                }
            }
        }
        return KunneIkkeIverksetteRegulering.FantIkkeRegulering.left()
    }

    private fun lagVedtakOgUtbetal(regulering: Regulering.IverksattRegulering): Either<KunneIkkeRegulereAutomatisk.KunneIkkeUtbetale, Pair<Regulering.IverksattRegulering, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering>> {
        return utbetalingService.verifiserOgSimulerUtbetaling(
            request = UtbetalRequest.NyUtbetaling(
                request = SimulerUtbetalingRequest.NyUtbetaling(
                    sakId = regulering.sakId,
                    saksbehandler = regulering.saksbehandler,
                    beregning = regulering.beregning,
                    uføregrunnlag = regulering.vilkårsvurderinger.tilVilkårsvurderingerRevurdering().uføre.grunnlag,
                ),
                simulering = regulering.simulering,
            ),
        ).mapLeft {
            log.error("Regulering for saksnummer ${regulering.saksnummer}: Kunne ikke verifisere og simulere utbetaling for regulering med underliggende grunn: $it")
            KunneIkkeRegulereAutomatisk.KunneIkkeUtbetale
        }.flatMap {
            val vedtak = VedtakSomKanRevurderes.from(regulering, it.id, clock)

            Either.catch {
                sessionFactory.withTransactionContext { tx ->
                    utbetalingService.lagreUtbetaling(it, tx)
                    vedtakService.lagre(vedtak, tx)
                    reguleringRepo.lagre(regulering, tx)
                    utbetalingService.publiserUtbetaling(it).getOrHandle { utbetalingsfeil ->
                        throw KunneIkkeSendeTilUtbetalingException(utbetalingsfeil)
                    }
                }

                Pair(regulering, vedtak)
            }.mapLeft {
                log.error(
                    "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering",
                    it,
                )
                KunneIkkeRegulereAutomatisk.KunneIkkeUtbetale
            }
        }
    }

    private data class KunneIkkeSendeTilUtbetalingException(val feil: UtbetalingFeilet) : RuntimeException()
}
