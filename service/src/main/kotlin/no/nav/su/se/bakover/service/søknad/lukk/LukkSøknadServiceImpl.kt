package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.AvvistSøknadBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest.Companion.lukk
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingLukket
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class LukkSøknadServiceImpl(
    private val søknadService: SøknadService,
    private val sakService: SakService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) : LukkSøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers = mutableListOf<EventObserver>()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    override fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, Sak> {
        val søknad = hentSøknad(request.søknadId).getOrHandle {
            return it.left()
        }
        if (request is LukkSøknadRequest.MedBrev.TrekkSøknad && !request.erDatoGyldig(søknad.mottaksdato, clock)) {
            log.info(
                "Kan ikke lukke søknad ${søknad.id}. ${request.trukketDato} må være mellom ${søknad.mottaksdato} og idag (${
                LocalDate.now(clock)
                })",
            )
            return KunneIkkeLukkeSøknad.UgyldigTrukketDato.left()
        }
        val lukketSøknadbehandling = søknadsbehandlingService.hentForSøknad(søknad.id)?.let { søknadsbehandling ->
            søknadsbehandling.lukkSøknadsbehandling().getOrHandle {
                return KunneIkkeLukkeSøknad.BehandlingErIFeilTilstand(it).left()
            }
        }
        return when (søknad) {
            is Søknad.Journalført.MedOppgave.Lukket -> {
                log.info("Søknad ${søknad.id} er allerede lukket")
                KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
            }
            is Søknad.Ny, is Søknad.Journalført.UtenOppgave -> {
                log.warn("Kan ikke lukke søknad ${søknad.id} siden den mangler oppgave. Se drifts-endepunktet /drift/søknader/fix.")
                KunneIkkeLukkeSøknad.SøknadManglerOppgave.left()
            }
            is Søknad.Journalført.MedOppgave.IkkeLukket -> {
                val person: Person = personService.hentPerson(søknad.søknadInnhold.personopplysninger.fnr)
                    .getOrElse {
                        log.error("Kan ikke lukke søknad ${søknad.id}. Fant ikke person.")
                        return KunneIkkeLukkeSøknad.FantIkkePerson.left()
                    }
                log.info("Lukker journalført søknad ${søknad.id} og tilhørende oppgave ${søknad.oppgaveId}")

                lukkSøknad(person, request, søknad, lukketSøknadbehandling)
                    .flatMap { lukket ->
                        oppgaveService.lukkOppgave(søknad.oppgaveId)
                            .mapLeft {
                                log.warn("Kunne ikke lukke oppgave ${søknad.oppgaveId} for søknad ${søknad.id}")
                            }
                        val sak = hentSak(lukket.sakId)
                        observers.forEach { observer ->
                            observer.handle(
                                Event.Statistikk.SøknadStatistikk.SøknadLukket(
                                    søknad = lukket,
                                    saksnummer = sak.saksnummer,
                                ),
                            )
                            lukketSøknadbehandling?.let {
                                observer.handle(SøknadsbehandlingLukket(lukketSøknadbehandling))
                            }
                        }
                        sak.right()
                    }
            }
        }
    }

    private fun lukkSøknad(
        person: Person,
        request: LukkSøknadRequest,
        søknad: Søknad.Journalført.MedOppgave.IkkeLukket,
        lukketSøknadsbehandling: LukketSøknadsbehandling?,
    ): Either<KunneIkkeLukkeSøknad, Søknad.Journalført.MedOppgave.Lukket> {
        return when (request) {
            is LukkSøknadRequest.MedBrev -> {
                lukkSøknadMedBrev(person, request, søknad, lukketSøknadsbehandling)
            }
            is LukkSøknadRequest.UtenBrev -> {
                lukkSøknadUtenBrev(request, søknad, lukketSøknadsbehandling).right()
            }
        }
    }

    override fun lagBrevutkast(
        request: LukkSøknadRequest,
    ): Either<KunneIkkeLageBrevutkast, ByteArray> {
        return hentSøknad(request.søknadId).mapLeft {
            KunneIkkeLageBrevutkast.FantIkkeSøknad
        }.flatMap { søknad ->
            personService.hentPerson(søknad.søknadInnhold.personopplysninger.fnr)
                .mapLeft {
                    log.error("Kunne ikke lage brevutkast siden vi ikke fant personen for søknad ${request.søknadId}")
                    KunneIkkeLageBrevutkast.FantIkkePerson
                }.flatMap { person ->
                    val brevRequest = when (request) {
                        is LukkSøknadRequest.MedBrev -> lagBrevRequest(person, søknad, request)
                        is LukkSøknadRequest.UtenBrev -> return KunneIkkeLageBrevutkast.UkjentBrevtype.left()
                    }
                    brevService.lagBrev(brevRequest)
                        .mapLeft {
                            KunneIkkeLageBrevutkast.KunneIkkeLageBrev
                        }
                }
        }
    }

    private fun lagBrevRequest(person: Person, søknad: Søknad, request: LukkSøknadRequest.MedBrev): LagBrevRequest {
        return when (request) {
            is LukkSøknadRequest.MedBrev.TrekkSøknad -> TrukketSøknadBrevRequest(
                person = person,
                søknad = søknad,
                trukketDato = request.trukketDato,
                saksbehandlerNavn = hentNavnForNavIdent(request.saksbehandler).getOrHandle { "" },
                dagensDato = LocalDate.now(clock),
            )
            is LukkSøknadRequest.MedBrev.AvvistSøknad -> AvvistSøknadBrevRequest(
                person = person,
                brevConfig = request.brevConfig,
                saksbehandlerNavn = hentNavnForNavIdent(request.saksbehandler).getOrHandle { "" },
                dagensDato = LocalDate.now(clock),
            )
        }
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<MicrosoftGraphApiOppslagFeil, String> {
        return microsoftGraphApiClient.hentNavnForNavIdent(navIdent)
    }

    private fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad> {
        return søknadService.hentSøknad(søknadId).mapLeft { KunneIkkeLukkeSøknad.FantIkkeSøknad }
    }

    private fun lukkSøknadUtenBrev(
        request: LukkSøknadRequest.UtenBrev,
        søknad: Søknad.Journalført.MedOppgave.IkkeLukket,
        lukketSøknadsbehandling: LukketSøknadsbehandling?,
    ): Søknad.Journalført.MedOppgave.Lukket {
        val lukketSøknad = søknad.lukk(request, Tidspunkt.now(clock))
        sessionFactory.withTransactionContext { transactionContext ->
            søknadService.lukkSøknad(lukketSøknad, transactionContext)
            lukketSøknadsbehandling?.also { søknadsbehandlingService.lukk(it, transactionContext) }
        }
        return lukketSøknad
    }

    private fun lukkSøknadMedBrev(
        person: Person,
        request: LukkSøknadRequest.MedBrev,
        søknad: Søknad.Journalført.MedOppgave.IkkeLukket,
        lukketSøknadsbehandling: LukketSøknadsbehandling?,
    ): Either<KunneIkkeLukkeSøknad, Søknad.Journalført.MedOppgave.Lukket> {
        val dokument = lagBrevRequest(person, søknad, request)
            .tilDokument {
                brevService.lagBrev(it).mapLeft { LagBrevRequest.KunneIkkeGenererePdf }
            }.map {
                it.leggTilMetadata(
                    metadata = Dokument.Metadata(
                        sakId = søknad.sakId,
                        søknadId = søknad.id,
                        bestillBrev = true,
                    ),
                )
            }.getOrHandle { return KunneIkkeLukkeSøknad.KunneIkkeGenerereDokument.left() }

        val lukketSøknad = søknad.lukk(request, Tidspunkt.now(clock))

        sessionFactory.withTransactionContext { transactionContext ->
            søknadService.lukkSøknad(lukketSøknad, transactionContext)
            brevService.lagreDokument(dokument, transactionContext)
            lukketSøknadsbehandling?.also { søknadsbehandlingService.lukk(it, transactionContext) }
        }

        return lukketSøknad.right()
    }

    private fun hentSak(id: UUID) = sakService.hentSak(id).orNull()!!
}
