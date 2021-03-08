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
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.AvvistSøknadBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest.Companion.lukk
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

internal class LukkSøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val clock: Clock,
) : LukkSøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers = mutableListOf<EventObserver>()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    override fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, LukketSøknad> {
        val søknad = hentSøknad(request.søknadId).getOrHandle {
            return it.left()
        }
        val opprettetDato = søknad.opprettet.toLocalDate(zoneIdOslo)
        if (request is LukkSøknadRequest.MedBrev.TrekkSøknad && !request.erDatoGyldig(opprettetDato)) {
            log.info("Kan ikke lukke søknad ${søknad.id}. ${request.trukketDato} må være mellom $opprettetDato og idag")
            return KunneIkkeLukkeSøknad.UgyldigTrukketDato.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknad.id)) {
            log.info("Kan ikke lukke søknad ${søknad.id} siden det finnes en behandling")
            return KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
        }

        return when (søknad) {
            is Søknad.Lukket -> {
                log.info("Søknad ${søknad.id} er allerede lukket")
                KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
            }
            is Søknad.Ny, is Søknad.Journalført.UtenOppgave -> {
                log.warn("Kan ikke lukke søknad ${søknad.id} siden den mangler oppgave. Se drifts-endepunktet /drift/søknader/fix.")
                KunneIkkeLukkeSøknad.SøknadManglerOppgave.left()
            }
            is Søknad.Journalført.MedOppgave -> {
                val person: Person = personService.hentPerson(søknad.søknadInnhold.personopplysninger.fnr)
                    .getOrElse {
                        log.error("Kan ikke lukke søknad ${søknad.id}. Fant ikke person.")
                        return KunneIkkeLukkeSøknad.FantIkkePerson.left()
                    }
                log.info("Lukker journalført søknad ${søknad.id} og tilhørende oppgave ${søknad.oppgaveId}")
                lukkSøknad(person, request, søknad)
                    .flatMap { lukketSøknad ->
                        oppgaveService.lukkOppgave(søknad.oppgaveId)
                            .mapLeft {
                                log.warn("Kunne ikke lukke oppgave ${søknad.oppgaveId} for søknad ${søknad.id}")
                                return (
                                    if (lukketSøknad is LukketSøknad.UtenMangler) {
                                        LukketSøknad.MedMangler.KunneIkkeLukkeOppgave(lukketSøknad.sak)
                                    } else lukketSøknad
                                    ).right()
                            }.map {
                                lukketSøknad.also {
                                    observers.forEach { observer ->
                                        observer.handle(
                                            Event.Statistikk.SøknadStatistikk.SøknadLukket(søknad, it.sak.saksnummer)
                                        )
                                    }
                                }
                            }
                    }
            }
        }
    }

    private fun lukkSøknad(
        person: Person,
        request: LukkSøknadRequest,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, LukketSøknad> {
        return when (request) {
            is LukkSøknadRequest.MedBrev -> lukkSøknadMedBrev(person, request, søknad)
            is LukkSøknadRequest.UtenBrev -> {
                lukkSøknadUtenBrev(request, søknad)
                LukketSøknad.UtenMangler(sak = hentSak(søknad.sakId)).right()
            }
        }
    }

    override fun lagBrevutkast(
        request: LukkSøknadRequest
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
                person,
                søknad,
                request.trukketDato,
                hentNavnForNavIdent(request.saksbehandler).getOrHandle { "" }
            )
            is LukkSøknadRequest.MedBrev.AvvistSøknad -> AvvistSøknadBrevRequest(
                person,
                request.brevConfig,
                hentNavnForNavIdent(request.saksbehandler).getOrHandle { "" }
            )
        }
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<MicrosoftGraphApiOppslagFeil, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .map { it.displayName }
    }

    private fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
    }

    private fun lukkSøknadUtenBrev(
        request: LukkSøknadRequest.UtenBrev,
        søknad: Søknad
    ) {
        val lukketSøknad = søknad.lukk(request, Tidspunkt.now(clock))
        søknadRepo.oppdaterSøknad(lukketSøknad)
    }

    private fun lukkSøknadMedBrev(
        person: Person,
        request: LukkSøknadRequest.MedBrev,
        søknad: Søknad
    ): Either<KunneIkkeLukkeSøknad, LukketSøknad> {
        val lukketSøknad = søknad.lukk(request, Tidspunkt.now(clock))
        val sak = hentSak(søknad.sakId)
        return brevService.journalførBrev(
            request = lagBrevRequest(person, lukketSøknad, request),
            saksnummer = sak.saksnummer
        ).mapLeft {
            log.error("Kunne ikke lukke søknad ${søknad.id} fordi journalføring feilet")
            return KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev.left()
        }.flatMap { journalpostId ->
            val lukketSøknadMedJournalpostId = lukketSøknad.medJournalpostId(journalpostId)

            brevService.distribuerBrev(journalpostId)
                .mapLeft {
                    søknadRepo.oppdaterSøknad(lukketSøknadMedJournalpostId)
                    log.error("Lukket søknad ${søknad.id} med journalpostId $journalpostId. Det skjedde en feil ved brevbestilling som må følges opp manuelt")
                    return LukketSøknad.MedMangler.KunneIkkeDistribuereBrev(hentSak(søknad.sakId)).right()
                }
                .flatMap { brevbestillingId ->
                    val lukketSøknadMedBrevbestillingId =
                        lukketSøknadMedJournalpostId.medBrevbestillingId(brevbestillingId)
                    søknadRepo.oppdaterSøknad(lukketSøknadMedBrevbestillingId)
                    log.info("Lukket søknad ${søknad.id} med journalpostId $journalpostId og bestilt brev $brevbestillingId")
                    LukketSøknad.UtenMangler(hentSak(søknad.sakId)).right()
                }
        }
    }

    private fun hentSak(id: UUID) = sakService.hentSak(id).orNull()!!
}
