package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.RevurderingRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.FerdigstillRevurderingService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse
import org.slf4j.LoggerFactory
import java.time.Clock

internal class FerdigstillIverksettingServiceImpl(
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val oppgaveService: OppgaveService,
    private val behandlingMetrics: BehandlingMetrics,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val personService: PersonService,
    private val brevService: BrevService,
    private val clock: Clock,
    private val revurderingRepo: RevurderingRepo
) : FerdigstillIverksettingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<EventObserver> = mutableListOf()

    private val ferdigstillSøknadsbehandlingService = FerdigstillSøknadsbehandlingService(
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        behandlingMetrics = behandlingMetrics,
        brevService = brevService,
        ferdigstillIverksettingService = this
    )
    private val opprettManglendeJournalpostOgBrevdistribusjonService = OpprettManglendeJournalpostOgBrevdistribusjonService(
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        brevService = brevService,
        ferdigstillSøknadsbehandlingService = ferdigstillSøknadsbehandlingService,
        behandlingMetrics = behandlingMetrics
    )
    private val ferdigstillRevurderingService = FerdigstillRevurderingService(
        brevService = brevService,
        revurderingRepo = revurderingRepo,
        ferdigstillIverksettingService = this
    )

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
        ferdigstillSøknadsbehandlingService.addObserver(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun ferdigstillIverksetting(utbetalingId: UUID30) {
        val søknadsbehandling = søknadsbehandlingRepo.hentBehandlingForUtbetaling(utbetalingId)
        val revurdering = revurderingRepo.hentRevurderingForUtbetaling(utbetalingId)

        check(listOfNotNull(søknadsbehandling, revurdering).count() == 1) {
            "Fant ingen eller mange elementer knyttet til utbetaling: $utbetalingId. Kan ikke ferdigstille iverksetting."
        }

        søknadsbehandling?.let { ferdigstillSøknadsbehandlingService.ferdigstillSøknadsbehandling(søknadsbehandling) }
        revurdering?.let { ferdigstillRevurderingService.ferdigstillRevurdering(revurdering) }
    }

    override fun opprettManglendeJournalpostOgBrevdistribusjon(): FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat {
        return opprettManglendeJournalpostOgBrevdistribusjonService.opprettManglendeJournalpostOgBrevdistribusjon()
    }

    fun lagBrevRequest(visitable: Visitable<LagBrevRequestVisitor>): Either<KunneIkkeFerdigstilleInnvilgelse, LagBrevRequest> {
        return LagBrevRequestVisitor(
            hentPerson = { fnr ->
                personService.hentPersonMedSystembruker(fnr)
                    .mapLeft { LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHentePerson }
            },
            hentNavn = { ident ->
                hentNavnForNavIdent(ident)
                    .mapLeft { LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant }
            },
            clock = clock,
        ).let { visitor ->
            visitable.accept(visitor)
            visitor.brevRequest
                .mapLeft {
                    when (it) {
                        LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> {
                            KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant
                        }
                        LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHentePerson -> {
                            KunneIkkeFerdigstilleInnvilgelse.FantIkkePerson
                        }
                    }
                }
        }
    }

    fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }

    fun lukkOppgave(
        oppgaveId: OppgaveId
    ): Either<KunneIkkeFerdigstilleInnvilgelse.KunneIkkeLukkeOppgave, Unit> {
        return oppgaveService.lukkOppgaveMedSystembruker(oppgaveId)
            .mapLeft { KunneIkkeFerdigstilleInnvilgelse.KunneIkkeLukkeOppgave }
    }
}
