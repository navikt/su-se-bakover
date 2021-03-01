package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class RevurderingServiceImpl(
    private val sakService: SakService,
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val brevService: BrevService,
    private val clock: Clock,
) : RevurderingService {

    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }
    fun getObservers(): List<EventObserver> = observers.toList()

    override fun opprettRevurdering(
        sakId: UUID,
        fraOgMed: LocalDate,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeOppretteRevurdering, Revurdering> {

        val dagensDato = LocalDate.now(clock)
        if (!fraOgMed.isAfter(dagensDato.endOfMonth())) {
            return KunneIkkeOppretteRevurdering.KanIkkeRevurdereInneværendeMånedEllerTidligere.left()
        }
        val sak = sakService.hentSak(sakId).getOrElse {
            return KunneIkkeOppretteRevurdering.FantIkkeSak.left()
        }
        val tilRevurdering = sak.behandlinger
            .filterIsInstance(Søknadsbehandling.Iverksatt.Innvilget::class.java)
            .filter { fraOgMed.between(it.beregning.getPeriode()) }

        val søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget = when {
            tilRevurdering.isEmpty() -> return KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()
            tilRevurdering.size > 1 -> return KunneIkkeOppretteRevurdering.KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder.left()
            revurderingRepo.hentRevurderingForBehandling(tilRevurdering.single().id) != null -> {
                return KunneIkkeOppretteRevurdering.KanIkkeRevurdereEnPeriodeMedEksisterendeRevurdering.left()
            }
            else -> tilRevurdering.single()
        }
        val periode = Periode.tryCreate(fraOgMed, søknadsbehandling.beregning.getPeriode().getTilOgMed()).getOrHandle {
            return KunneIkkeOppretteRevurdering.UgyldigPeriode(it).left()
        }

        val aktørId = personService.hentAktørId(søknadsbehandling.fnr).getOrElse {
            log.error("Fant ikke aktør-id")
            return KunneIkkeOppretteRevurdering.FantIkkeAktørId.left()
        }

        return oppgaveService.opprettOppgave(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = søknadsbehandling.saksnummer,
                aktørId = aktørId,
                tilordnetRessurs = null
            )
        ).mapLeft {
            KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave
        }.map {
            OpprettetRevurdering(
                periode = periode,
                tilRevurdering = søknadsbehandling,
                saksbehandler = saksbehandler
            ).also {
                revurderingRepo.lagre(it)
                observers.forEach { observer ->
                    observer.handle(
                        Event.Statistikk.RevurderingStatistikk.RevurderingOpprettet(
                            it
                        )
                    )
                }
            }
        }
    }

    override fun oppdaterRevurderingsperiode(
        revurderingId: UUID,
        fraOgMed: LocalDate,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeOppdatereRevurderingsperiode, OpprettetRevurdering> {

        val revurdering = revurderingRepo.hent(revurderingId) ?: return KunneIkkeOppdatereRevurderingsperiode.FantIkkeRevurdering.left()

        if (!fraOgMed.between(revurdering.periode)) {
            return KunneIkkeOppdatereRevurderingsperiode.PeriodenMåVæreInnenforAlleredeValgtStønadsperiode(revurdering.periode).left()
        }
        // TODO jah: Her holder det kanskje ikke å bruke samme tilOgMed som forrige gang. Hva om vi har byttet stønadsperiode?
        val nyPeriode = Periode.tryCreate(fraOgMed, revurdering.periode.getTilOgMed()).getOrHandle {
            return KunneIkkeOppdatereRevurderingsperiode.UgyldigPeriode(it).left()
        }
        return when (revurdering) {
            is OpprettetRevurdering -> revurdering.oppdaterPeriode(nyPeriode).right()
            is BeregnetRevurdering -> revurdering.oppdaterPeriode(nyPeriode).right()
            is SimulertRevurdering -> revurdering.oppdaterPeriode(nyPeriode).right()
            else -> KunneIkkeOppdatereRevurderingsperiode.UgyldigTilstand(revurdering::class, OpprettetRevurdering::class).left()
        }.map {
            revurderingRepo.lagre(it)
            it
        }
    }

    override fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, Revurdering> {
        return when (val revurdering = revurderingRepo.hent(revurderingId)) {
            is BeregnetRevurdering, is OpprettetRevurdering, is SimulertRevurdering -> {
                when (
                    val beregnetRevurdering = revurdering.beregn(fradrag)
                        .getOrHandle {
                            return when (it) {
                                Revurdering.KunneIkkeBeregneRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden -> KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
                            }.left()
                        }
                ) {
                    is BeregnetRevurdering.Avslag -> {
                        revurderingRepo.lagre(beregnetRevurdering)
                        beregnetRevurdering.right()
                    }
                    is BeregnetRevurdering.Innvilget -> {
                        utbetalingService.simulerUtbetaling(
                            sakId = beregnetRevurdering.tilRevurdering.sakId,
                            saksbehandler = saksbehandler,
                            beregning = beregnetRevurdering.beregning
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet
                        }.map {
                            val simulert = beregnetRevurdering.toSimulert(it.simulering)
                            revurderingRepo.lagre(simulert)
                            simulert
                        }
                    }
                }
            }
            null -> return KunneIkkeBeregneOgSimulereRevurdering.FantIkkeRevurdering.left()
            else -> return KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand(revurdering::class, SimulertRevurdering::class).left()
        }
    }

    override fun sendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering> {
        val tilAttestering = when (val revurdering = revurderingRepo.hent(revurderingId)) {
            is SimulertRevurdering -> {
                val aktørId = personService.hentAktørId(revurdering.tilRevurdering.fnr).getOrElse {
                    log.error("Fant ikke aktør-id")
                    return KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId.left()
                }

                val oppgaveId = oppgaveService.opprettOppgave(
                    OppgaveConfig.AttesterRevurdering(
                        saksnummer = revurdering.tilRevurdering.saksnummer,
                        aktørId = aktørId,
                        // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                        // TODO: skal ikke være null. attestant kan endre seg. må legge til attestant på revurdering
                        tilordnetRessurs = null
                    )
                ).getOrElse {
                    log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
                    return KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave.left()
                }

                revurdering.tilAttestering(oppgaveId, saksbehandler)
            }
            null -> return KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering.left()
            else -> return KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(revurdering::class, RevurderingTilAttestering::class)
                .left()
        }
        revurderingRepo.lagre(tilAttestering)
        observers.forEach { observer ->
            observer.handle(
                Event.Statistikk.RevurderingStatistikk.RevurderingTilAttestering(
                    tilAttestering
                )
            )
        }
        return tilAttestering.right()
    }

    override fun lagBrevutkast(
        revurderingId: UUID,
        fritekst: String?
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering.left()

        return LagBrevRequestVisitor(
            hentPerson = { fnr ->
                personService.hentPerson(fnr)
                    .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson }
            },
            hentNavn = { ident ->
                microsoftGraphApiClient.hentNavnForNavIdent(ident)
                    .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant }
            },
            clock = clock
        ).let {
            revurdering.accept(it)
            it.brevRequest
        }.mapLeft {
            when (it) {
                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson -> KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson
            }
        }.flatMap {
            brevService.lagBrev(it).mapLeft { KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast }
        }
    }

    override fun iverksett(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
        return when (val revurdering = revurderingRepo.hent(revurderingId)) {
            is RevurderingTilAttestering -> {
                val iverksattRevurdering = revurdering.iverksett(attestant) {
                    utbetalingService.utbetal(
                        sakId = revurdering.sakId,
                        beregning = revurdering.beregning,
                        simulering = revurdering.simulering,
                        attestant = attestant,
                    ).mapLeft {
                        when (it) {
                            KunneIkkeUtbetale.KunneIkkeSimulere -> RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.KunneIkkeSimulere
                            KunneIkkeUtbetale.Protokollfeil -> RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.Protokollfeil
                            KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                        }
                    }.map {
                        it.id
                    }
                }.getOrHandle {
                    return when (it) {
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.KunneIkkeSimulere -> KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulere
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.Protokollfeil -> KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulere
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale
                    }.left()
                }
                revurderingRepo.lagre(iverksattRevurdering)
                observers.forEach { observer ->
                    observer.handle(
                        Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(iverksattRevurdering)
                    )
                }
                return iverksattRevurdering.right()
            }
            null -> KunneIkkeIverksetteRevurdering.FantIkkeRevurdering.left()
            else -> KunneIkkeIverksetteRevurdering.UgyldigTilstand(revurdering::class, IverksattRevurdering::class)
                .left()
        }
    }

    override fun hentRevurderingForUtbetaling(utbetalingId: UUID30): IverksattRevurdering? {
        return revurderingRepo.hentRevurderingForUtbetaling(utbetalingId)
    }
}
