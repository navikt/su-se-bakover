package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.UUID30
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

    override fun opprettRevurdering(
        sakId: UUID,
        fraOgMed: LocalDate,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeRevurdere, Revurdering> {

        val dagensDato = LocalDate.now(clock)
        if (!fraOgMed.isAfter(dagensDato.withDayOfMonth(dagensDato.lengthOfMonth()))) return KunneIkkeRevurdere.KanIkkeRevurdereInneværendeMånedEllerTidligere.left()

        return hentSak(sakId)
            .map { sak ->
                val revurderingsPeriode = Periode.create(
                    fraOgMed = fraOgMed,
                    tilOgMed = sak.utbetalinger.map { it.senesteDato() }.maxByOrNull { it }!!
                )
                val tilRevurdering = sak.behandlinger
                    .filterIsInstance(Søknadsbehandling.Iverksatt.Innvilget::class.java)
                    .filter { it.beregning.getPeriode() inneholder revurderingsPeriode }

                if (tilRevurdering.isEmpty()) return KunneIkkeRevurdere.FantIngentingSomKanRevurderes.left()
                if (tilRevurdering.size > 1) return KunneIkkeRevurdere.KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder.left()
                if (revurderingRepo.hentRevurderingForBehandling(tilRevurdering.single().id) != null) return KunneIkkeRevurdere.KanIkkeRevurdereEnPeriodeMedEksisterendeRevurdering.left()

                tilRevurdering.single().let { søknadsbehandling ->
                    val aktørId = personService.hentAktørId(søknadsbehandling.fnr).getOrElse {
                        log.error("Fant ikke aktør-id")
                        return KunneIkkeRevurdere.FantIkkeAktørid.left()
                    }

                    return oppgaveService.opprettOppgave(
                        OppgaveConfig.Revurderingsbehandling(
                            saksnummer = søknadsbehandling.saksnummer,
                            aktørId = aktørId,
                            tilordnetRessurs = null
                        )
                    ).mapLeft {
                        KunneIkkeRevurdere.KunneIkkeOppretteOppgave
                    }.map {
                        val revurdering = OpprettetRevurdering(
                            periode = revurderingsPeriode,
                            tilRevurdering = søknadsbehandling,
                            saksbehandler = saksbehandler
                        )
                        revurderingRepo.lagre(revurdering)
                        revurdering
                    }
                }
            }
    }

    override fun oppdaterRevurderingsperiode(
        revurderingId: UUID,
        fraOgMed: LocalDate,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeRevurdere, OpprettetRevurdering> {

        val revurdering = revurderingRepo.hent(revurderingId) ?: return KunneIkkeRevurdere.FantIkkeRevurdering.left()

        // TODO jah: Her holder det kanskje ikke å bruke samme tilOgMed som forrige gang. Hva om vi har byttet stønadsperiode?
        val nyPeriode = Periode.tryCreate(fraOgMed, revurdering.periode.getTilOgMed()).getOrHandle {
            return KunneIkkeRevurdere.UgyldigPeriode(it).left()
        }
        return when (revurdering) {
            is OpprettetRevurdering -> revurdering.oppdaterPeriode(nyPeriode).right()
            is BeregnetRevurdering -> revurdering.oppdaterPeriode(nyPeriode).right()
            is SimulertRevurdering -> revurdering.oppdaterPeriode(nyPeriode).right()
            else -> KunneIkkeRevurdere.UgyldigTilstand(revurdering::class, OpprettetRevurdering::class).left()
        }.map {
            revurderingRepo.lagre(it)
            it
        }
    }

    override fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeRevurdere, Revurdering> {
        return when (val revurdering = revurderingRepo.hent(revurderingId)) {
            is BeregnetRevurdering, is OpprettetRevurdering, is SimulertRevurdering -> {
                when (val beregnetRevurdering = revurdering.beregn(fradrag).getOrElse { return KunneIkkeRevurdere.NesteMånedErUtenforStønadsperioden.left() }) {
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
                            KunneIkkeRevurdere.SimuleringFeilet
                        }.map {
                            val simulert = beregnetRevurdering.toSimulert(it.simulering)
                            revurderingRepo.lagre(simulert)
                            simulert
                        }
                    }
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
                    OppgaveConfig.AttesterRevurdering(
                        saksnummer = revurdering.tilRevurdering.saksnummer,
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
                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeRevurdere.KunneIkkeLageBrevutkast
                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson -> KunneIkkeRevurdere.FantIkkePerson
            }
        }.flatMap {
            brevService.lagBrev(it).mapLeft { KunneIkkeRevurdere.KunneIkkeLageBrevutkast }
        }
    }

    override fun iverksett(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
        return when (val revurdering = revurderingRepo.hent(revurderingId)) {
            is RevurderingTilAttestering -> {
                if (attestant.navIdent == revurdering.saksbehandler.navIdent) {
                    return KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }

                utbetalingService.utbetal(
                    sakId = revurdering.sakId,
                    beregning = revurdering.beregning,
                    simulering = revurdering.simulering,
                    attestant = attestant,
                ).fold(
                    ifLeft = {
                        when (it) {
                            KunneIkkeUtbetale.KunneIkkeSimulere -> KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulere
                            KunneIkkeUtbetale.Protokollfeil -> KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale
                            KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> KunneIkkeIverksetteRevurdering.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                        }.left()
                    },
                    ifRight = { utbetaling ->
                        revurdering.iverksett(attestant, utbetaling.id)
                            .mapLeft { KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson }
                            .map {
                                revurderingRepo.lagre(it)
                                it
                            }
                    }
                )
            }
            null -> KunneIkkeIverksetteRevurdering.FantIkkeRevurdering.left()
            else -> KunneIkkeIverksetteRevurdering.FeilTilstand.left()
        }
    }

    override fun hentRevurderingForUtbetaling(utbetalingId: UUID30): IverksattRevurdering? =
        revurderingRepo.hentRevurderingForUtbetaling(utbetalingId)
}
