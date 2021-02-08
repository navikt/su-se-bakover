package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.RevurderingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.util.UUID

internal class RevurderingServiceImpl(
    private val sakService: SakService,
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val brevService: BrevService,
) : RevurderingService {

    override fun opprettRevurdering(
        sakId: UUID,
        periode: Periode,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeRevurdere, Revurdering> {
        if (!periode.erPeriodenIMånederEtter()) return KunneIkkeRevurdere.KanIkkeRevurdereInneværendeMånedEllerTidligere.left()

        return hentSak(sakId)
            .map { sak ->
                // TODO: `
                val tilRevurdering = sak.behandlinger()
                    .filter { it.status() == Behandling.BehandlingsStatus.IVERKSATT_INNVILGET }
                    .filter { it.beregning()!!.getPeriode() inneholder periode }

                if (tilRevurdering.isEmpty()) return KunneIkkeRevurdere.FantIngentingSomKanRevurderes.left()
                if (tilRevurdering.size > 1) return KunneIkkeRevurdere.KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder.left()

                tilRevurdering.single().let {
                    val revurdering = OpprettetRevurdering(
                        periode = periode,
                        tilRevurdering = it,
                        saksbehandler = saksbehandler
                    )
                    revurderingRepo.lagre(revurdering)
                    revurdering
                }
            }
    }

    override fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeRevurdere, SimulertRevurdering> {
        return when (val revurdering = revurderingRepo.hent(revurderingId)) {
            is BeregnetRevurdering, is OpprettetRevurdering, is SimulertRevurdering -> {
                val beregnetRevurdering = revurdering.beregn(fradrag)
                utbetalingService.simulerUtbetaling(
                    sakId = beregnetRevurdering.tilRevurdering.sakId,
                    saksbehandler = saksbehandler,
                    beregning = beregnetRevurdering.beregning
                ).mapLeft {
                    KunneIkkeRevurdere.SimuleringFeilet
                }.map {
                    val simulert = beregnetRevurdering.toSimulert(it.simulering)
                    revurderingRepo.lagre(simulert)
                    simulert
                }
            }
            else -> {
                throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")
            }
        }
    }

    override fun sendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeRevurdere, Revurdering> {
        val tilAttestering = when (val revurdering = revurderingRepo.hent(revurderingId)) {
            is SimulertRevurdering -> {
                val aktørId = personService.hentAktørId(revurdering.tilRevurdering.fnr).getOrElse {
                    log.error("Fant ikke aktør-id")
                    return KunneIkkeRevurdere.FantIkkeAktørid.left()
                }

                val oppgaveId = oppgaveService.opprettOppgave(
                    OppgaveConfig.Attestering(
                        revurdering.tilRevurdering.søknad.id,
                        aktørId = aktørId,
                        // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                        // TODO: skal ikke være null. attestant kan endre seg. må legge til attestant på revurdering
                        tilordnetRessurs = null
                    )
                ).getOrElse {
                    log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
                    return KunneIkkeRevurdere.KunneIkkeOppretteOppgave.left()
                }

                revurdering.tilAttestering(oppgaveId, saksbehandler)
            }
            else -> throw RuntimeException("Revurdering er ikke i riktig status for å sendes til attestering")
        }

        revurderingRepo.lagre(tilAttestering)

        return tilAttestering.right()
    }

    private fun hentSak(sakId: UUID) = sakService.hentSak(sakId)
        .mapLeft { KunneIkkeRevurdere.FantIkkeSak }

    override fun lagBrevutkast(revurderingId: UUID, fritekst: String?): Either<KunneIkkeRevurdere, ByteArray> {
        val revurdering = revurderingRepo.hent(revurderingId) ?: return KunneIkkeRevurdere.FantIkkeRevurdering.left()

        fun lagBrevutkastForRevurderingAvInntekt(revurdertBeregning: Beregning): Either<KunneIkkeRevurdere, ByteArray> {

            val person = personService.hentPerson(revurdering.tilRevurdering.fnr)
                .getOrHandle { return KunneIkkeRevurdere.FantIkkePerson.left() }

            val saksbehandlerNavn = microsoftGraphApiClient.hentNavnForNavIdent(revurdering.saksbehandler)
                .getOrHandle { return KunneIkkeRevurdere.MicrosoftApiGraphFeil.left() }

            val request = LagBrevRequest.Revurdering.Inntekt(
                person = person,
                saksbehandlerNavn = saksbehandlerNavn,
                revurdertBeregning = revurdertBeregning,
                fritekst = fritekst,
                vedtattBeregning = revurdering.tilRevurdering.beregning()!!,
                harEktefelle = revurdering.tilRevurdering.behandlingsinformasjon().harEktefelle()
            )

            return brevService.lagBrev(request).mapLeft {
                KunneIkkeRevurdere.KunneIkkeLageBrevutkast
            }
        }

        return when (revurdering) {
            is SimulertRevurdering -> {
                lagBrevutkastForRevurderingAvInntekt(revurdering.beregning)
            }
            is RevurderingTilAttestering -> {
                lagBrevutkastForRevurderingAvInntekt(revurdering.beregning)
            }
            else -> KunneIkkeRevurdere.KunneIkkeLageBrevutkast.left()
        }
    }

    override fun iverksett(revurderingId: UUID, attestant: NavIdentBruker.Attestant): Either<KunneIkkeRevurdere.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, IverksattRevurdering> {
        return when (val revurdering = revurderingRepo.hent(revurderingId)) {
            is RevurderingTilAttestering -> {
                if (attestant.navIdent == revurdering.saksbehandler.navIdent) {
                    return KunneIkkeRevurdere.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }

                utbetalingService.utbetal(
                    sakId = revurdering.sakId,
                    beregning = revurdering.beregning,
                    simulering = revurdering.simulering,
                    attestant = attestant,
                ).mapLeft {
                    KunneIkkeRevurdere.AttestantOgSaksbehandlerKanIkkeVæreSammePerson // change
                }.map { utbetaling ->
                    revurdering.iverksett(attestant, utbetaling.id)
                        .fold(
                            ifLeft = { TODO() },
                            ifRight = {
                                revurderingRepo.lagre(it)
                                it
                            }
                        )
                }
            }
            null -> TODO("fant ingen revurdering")
            else -> TODO("fel status")
        }
    }
}
