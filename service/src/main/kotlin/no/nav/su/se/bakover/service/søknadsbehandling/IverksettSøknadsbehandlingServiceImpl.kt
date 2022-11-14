package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.forsøkStatusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.LoggerFactory
import java.time.Clock

class IverksettSøknadsbehandlingServiceImpl(
    private val sakService: SakService,
    private val avkortingsvarselRepo: AvkortingsvarselRepo,
    private val tilbakekrevingService: TilbakekrevingService,
    private val clock: Clock,
    private val utbetalingService: UtbetalingService,
    private val sessionFactory: SessionFactory,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val vedtakRepo: VedtakRepo,
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val behandlingMetrics: BehandlingMetrics,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val brevService: BrevService,
) : IverksettSøknadsbehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    override fun iverksett(
        request: IverksettRequest,
    ): Either<KunneIkkeIverksette, Søknadsbehandling.Iverksatt> {
        val sak = sakService.hentSakForSøknadsbehandling(request.behandlingId)

        val søknadsbehandling = sak.hentSøknadsbehandling(request.behandlingId)
            .getOrHandle { return KunneIkkeIverksette.FantIkkeBehandling.left() }

        return forsøkStatusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilIverksatt(
                request.attestering,
                hentOpprinneligAvkorting = { avkortingid ->
                    avkortingsvarselRepo.hent(id = avkortingid)
                },
            ),
        ).flatMap { iverksattBehandling ->
            when (iverksattBehandling) {
                is Søknadsbehandling.Iverksatt.Innvilget -> {
                    tilbakekrevingService.hentAvventerKravgrunnlag(søknadsbehandling.sakId)
                        .ifNotEmpty {
                            return KunneIkkeIverksette.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
                        }

                    Either.catch {
                        val simulertUtbetaling = sak.lagNyUtbetaling(
                            saksbehandler = request.attestering.attestant,
                            beregning = iverksattBehandling.beregning,
                            clock = clock,
                            utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                            uføregrunnlag = when (iverksattBehandling.sakstype) {
                                Sakstype.ALDER -> {
                                    null
                                }

                                Sakstype.UFØRE -> {
                                    iverksattBehandling.vilkårsvurderinger.uføreVilkår()
                                        .getOrHandle { throw IllegalStateException("Søknadsbehandling uføre: ${iverksattBehandling.id} mangler uføregrunnlag") }
                                        .grunnlag
                                        .toNonEmptyList()
                                }
                            },
                        ).let {
                            sak.simulerUtbetaling(
                                utbetalingForSimulering = it,
                                periode = iverksattBehandling.periode,
                                simuler = utbetalingService::simulerUtbetaling,
                                kontrollerMotTidligereSimulering = iverksattBehandling.simulering,
                                clock = clock,
                            )
                        }.getOrHandle { feil ->
                            throw IverksettTransactionException(
                                "Kunne ikke opprette utbetaling. Underliggende feil:$feil.",
                                KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(feil)),
                            )
                        }
                        sessionFactory.withTransactionContext { tx ->
                            /**
                             * OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake.
                             * Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
                             * Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka.
                             * Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. statistikk.
                             */
                            val nyUtbetaling = utbetalingService.klargjørUtbetaling(
                                utbetaling = simulertUtbetaling,
                                transactionContext = tx,
                            ).getOrHandle { feil ->
                                log.error("Kunne ikke innvilge behandling ${søknadsbehandling.id} siden utbetaling feilet. Feiltype: $feil")
                                throw IverksettTransactionException(
                                    "Kunne ikke opprette utbetaling. Underliggende feil:$feil.",
                                    KunneIkkeIverksette.KunneIkkeUtbetale(feil),
                                )
                            }
                            val vedtak = VedtakSomKanRevurderes.fromSøknadsbehandling(
                                søknadsbehandling = iverksattBehandling,
                                utbetalingId = nyUtbetaling.utbetaling.id,
                                clock = clock,
                            )

                            søknadsbehandlingRepo.lagre(
                                søknadsbehandling = iverksattBehandling,
                                sessionContext = tx,
                            )
                            vedtakRepo.lagre(
                                vedtak = vedtak,
                                sessionContext = tx,
                            )
                            // Så fremt denne ikke kaster ønsker vi å gå igjennom med iverksettingen.
                            kontrollsamtaleService.opprettPlanlagtKontrollsamtale(
                                vedtak = vedtak,
                                sessionContext = tx,
                            )
                            nyUtbetaling.sendUtbetaling()
                                .getOrHandle { feil ->
                                    throw IverksettTransactionException(
                                        "Kunne ikke publisere utbetaling på køen. Underliggende feil: $feil.",
                                        KunneIkkeIverksette.KunneIkkeUtbetale(feil),
                                    )
                                }
                            vedtak
                        }
                    }.mapLeft {
                        log.error(
                            "Kunne ikke iverksette søknadsbehandling for sak ${iverksattBehandling.sakId} og søknadsbehandling ${iverksattBehandling.id}.",
                            it,
                        )
                        when (it) {
                            is IverksettTransactionException -> it.feil
                            else -> KunneIkkeIverksette.LagringFeilet
                        }
                    }.map { vedtak ->
                        log.info("Iverksatt innvilgelse for behandling ${iverksattBehandling.id}, vedtak: ${vedtak.id}")

                        behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

                        observers.notify(StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget(vedtak))
                        // TODO jah: Vi har gjort endringer på saken underveis - endret regulering, ny utbetaling og nytt vedtak - uten at selve saken blir oppdatert underveis. Når saken returnerer en oppdatert versjon av seg selv for disse tilfellene kan vi fjerne det ekstra kallet til hentSak.
                        observers.notify(
                            StatistikkEvent.Stønadsvedtak(vedtak) {
                                sakService.hentSak(sak.id).orNull()!!
                            },
                        )

                        iverksattBehandling
                    }
                }

                is Søknadsbehandling.Iverksatt.Avslag -> {
                    val vedtak: Avslagsvedtak = opprettAvslagsvedtak(iverksattBehandling)

                    val dokument = brevService.lagDokument(vedtak)
                        .getOrHandle { return KunneIkkeIverksette.KunneIkkeGenerereVedtaksbrev.left() }
                        .leggTilMetadata(
                            Dokument.Metadata(
                                sakId = vedtak.behandling.sakId,
                                søknadId = null,
                                vedtakId = vedtak.id,
                                revurderingId = null,
                                bestillBrev = true,
                            ),
                        )

                    Either.catch {
                        sessionFactory.withTransactionContext {
                            /**
                             * OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake.
                             * Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
                             * Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka.
                             * Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. statistikk.
                             */
                            søknadsbehandlingRepo.lagre(iverksattBehandling, it)
                            vedtakRepo.lagre(vedtak, it)
                            brevService.lagreDokument(dokument, it)
                        }
                    }.mapLeft {
                        log.error(
                            "Kunne ikke iverksette søknadsbehandling for sak ${iverksattBehandling.sakId} og søknadsbehandling ${iverksattBehandling.id}.",
                            it,
                        )
                        KunneIkkeIverksette.LagringFeilet
                    }.map {
                        log.info("Iverksatt avslag for behandling: ${iverksattBehandling.id}, vedtak: ${vedtak.id}")

                        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)

                        ferdigstillVedtakService.lukkOppgaveMedBruker(vedtak.behandling)
                            .mapLeft {
                                log.error("Lukking av oppgave for behandlingId: ${(vedtak.behandling as BehandlingMedOppgave).oppgaveId} feilet. Må ryddes opp manuelt.")
                            }

                        observers.notify(StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag(vedtak))

                        iverksattBehandling
                    }
                }
            }
        }
    }

    private fun opprettAvslagsvedtak(iverksattBehandling: Søknadsbehandling.Iverksatt.Avslag): Avslagsvedtak =
        when (iverksattBehandling) {
            is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
                Avslagsvedtak.fromSøknadsbehandlingMedBeregning(
                    avslag = iverksattBehandling,
                    clock = clock,
                )
            }

            is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
                Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
                    avslag = iverksattBehandling,
                    clock = clock,
                )
            }
        }

    private data class IverksettTransactionException(
        override val message: String,
        val feil: KunneIkkeIverksette,
    ) : RuntimeException(message)
}
