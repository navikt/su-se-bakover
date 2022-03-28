package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.log
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
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
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

    private fun blirBeregningEndret(sak: Sak, regulering: Regulering.OpprettetRegulering): Boolean {
        if (regulering.inneholderAvslag()) return true

        val reguleringMedBeregning = regulering.beregn(clock = clock, begrunnelse = null)
            .getOrHandle {
                when (it) {
                    is Regulering.KunneIkkeBeregne.BeregningFeilet -> {
                        throw RuntimeException("Vi klarte ikke å beregne. Underliggende grunn ${it.feil}")
                    }
                    is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> {
                        throw RuntimeException("Vi klarte ikke å beregne. Feil status")
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
            log.info("Starter på : $saksnummer")

            val sak = Either.catch {
                sakRepo.hentSak(sakId = sakid) ?: return@map KunneIkkeOppretteRegulering.FantIkkeSak.left()
                    .also {
                        log.error(
                            "Klarte ikke hente sak : $saksnummer",
                            RuntimeException("Inkluderer stacktrace"),
                        )
                    }
            }.getOrHandle {
                log.error("Klarte ikke hente sak: $saksnummer", it)
                return@map KunneIkkeOppretteRegulering.FantIkkeSak.left()
            }

            val regulering = sak.opprettEllerOppdaterRegulering(startDato, clock).getOrHandle { feil ->
                // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
                return@map KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(feil).left()
            }

            if (!blirBeregningEndret(sak, regulering)) {
                // TODO jah: Dersom en [OpprettetRegulering] allerede eksisterte i databasen, bør vi kanskje slette den her.
                return@map KunneIkkeOppretteRegulering.FørerIkkeTilEnEndring.left()
                    .also { log.info("Lager ikke regulering for $saksnummer, da den ikke fører til noen endring i utbetaling") }
            }

            tilbakekrevingService.hentAvventerKravgrunnlag(sak.id)
                .ifNotEmpty {
                    log.info("Kan ikke sende oppdragslinjer mens vi venter på et kravgrunnlag, siden det kan annulere nåværende kravgrunnlag. Setter reguleringen til manuell.")
                    return@map regulering.copy(
                        reguleringstype = Reguleringstype.MANUELL,
                    ).right().tap {
                        reguleringRepo.lagre(regulering)
                    }
                }

            reguleringRepo.lagre(regulering)

            if (regulering.reguleringstype == Reguleringstype.AUTOMATISK) {
                regulerAutomatisk(regulering).mapLeft { feil ->
                    KunneIkkeOppretteRegulering.KunneIkkeRegulereAutomatisk(feil = feil)
                }
            } else {
                regulering.right()
            }
        }
    }

    private fun regulerAutomatisk(regulering: Regulering.OpprettetRegulering): Either<KunneIkkeRegulereAutomatisk, Regulering.IverksattRegulering> {
        return regulering.beregn(clock = clock, begrunnelse = null)
            .mapLeft { kunneikkeBeregne ->
                when (kunneikkeBeregne) {
                    is Regulering.KunneIkkeBeregne.BeregningFeilet -> {
                        log.error("Regulering feilet. Beregning feilet for saksnummer: ${regulering.saksnummer}", kunneikkeBeregne.feil)
                    }
                    is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> {
                        log.error("Regulering feilet. Beregning feilet for saksnummer: ${regulering.saksnummer}. Ikke lov å beregne i denne statusen")
                    }
                }
                KunneIkkeRegulereAutomatisk.KunneIkkeBeregne
            }
            .flatMap { beregnetRegulering ->
                beregnetRegulering.simuler(utbetalingService::simulerUtbetaling)
                    .mapLeft {
                        log.error("Regulering feilet. Simulering feilet for saksnummer: ${regulering.saksnummer}.")
                        KunneIkkeRegulereAutomatisk.KunneIkkeSimulere
                    }.flatMap {
                        if (it.simulering!!.harFeilutbetalinger()) {
                            log.error("Regulering feilet. Simuleringen inneholdt feilutbetalinger for saksnummer: ${regulering.saksnummer}.")
                            KunneIkkeRegulereAutomatisk.KanIkkeAutomatiskRegulereSomFørerTilFeilutbetaling.left()
                        } else {
                            it.right()
                        }
                    }
            }
            .map { simulertRegulering -> simulertRegulering.tilIverksatt() }
            .flatMap {
                lagVedtakOgUtbetal(it).tap {
                    reguleringRepo.lagre(it)
                }
            }
            .tapLeft {
                reguleringRepo.lagre(regulering.copy(reguleringstype = Reguleringstype.MANUELL))
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
                is Regulering.KunneIkkeBeregne.BeregningFeilet -> log.error("Regulering feilet. Kunne ikke beregne", it.feil)
                is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> log.error("Regulering feilet. Kan ikke beregne i status ${it.status}")
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

    private fun lagVedtakOgUtbetal(regulering: Regulering.IverksattRegulering): Either<KunneIkkeRegulereAutomatisk.KunneIkkeUtbetale, Regulering.IverksattRegulering> {
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
            log.error("Kunne ikke verifisere og simulere utbetaling for regulering med saksnummer ${regulering.saksnummer} med underliggende grunn: $it")
            KunneIkkeRegulereAutomatisk.KunneIkkeUtbetale
        }.flatMap {
            Either.catch {
                sessionFactory.withTransactionContext { tx ->
                    utbetalingService.lagreUtbetaling(it, tx)
                    vedtakService.lagre(
                        vedtak = VedtakSomKanRevurderes.from(regulering, it.id, clock),
                        sessionContext = tx,
                    )
                    utbetalingService.publiserUtbetaling(it).mapLeft {
                        throw KunneIkkeSendeTilUtbetalingException(it)
                    }
                    regulering
                }
            }.mapLeft {
                log.error(
                    "En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering med saksnummer ${regulering.saksnummer}",
                    it,
                )
                KunneIkkeRegulereAutomatisk.KunneIkkeUtbetale
            }
        }
    }

    private data class KunneIkkeSendeTilUtbetalingException(val feil: UtbetalingFeilet) : RuntimeException()
}
