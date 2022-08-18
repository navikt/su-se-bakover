package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

internal class LukkSøknadServiceImpl(
    private val søknadService: SøknadService,
    private val sakService: SakService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val identClient: IdentClient,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) : LukkSøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers = mutableListOf<StatistikkEventObserver>()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<StatistikkEventObserver> = observers.toList()

    override fun lukkSøknad(
        command: LukkSøknadCommand,
    ): Sak {
        val søknadId = command.søknadId
        val sak = sakService.hentSakForSøknad(søknadId).getOrHandle {
            throw IllegalArgumentException("Fant ikke sak for søknadId $søknadId")
        }
        return sessionFactory.withTransactionContext { tx ->
            sak.lukkSøknadOgSøknadsbehandling(
                lukkSøknadCommand = command,
                hentPerson = { personService.hentPerson(sak.fnr) },
                clock = clock,
                hentSaksbehandlerNavn = { identClient.hentNavnForNavIdent(it) },
                saksbehandler = command.saksbehandler,
            ).let {
                it.lagBrevRequest.tap { lagBrevRequest ->
                    persisterBrevKlartForSending(
                        tx = tx,
                        brev = lagBrevRequest,
                        sakId = it.sak.id,
                        søknadId = it.søknad.id,
                    )
                }
                søknadService.persisterSøknad(it.søknad, tx)
                it.søknadsbehandling?.also {
                    søknadsbehandlingService.persisterSøknadsbehandling(it, tx)
                }
                oppgaveService.lukkOppgave(it.søknad.oppgaveId).tapLeft { feil ->
                    // Fire and forget. De som følger med på alerts kan evt. gi beskjed til saksbehandlerene.
                    log.error("Kunne ikke lukke oppgave knyttet til søknad/søknadsbehandling med søknadId ${it.søknad.id} og oppgaveId ${it.søknad.oppgaveId}. Underliggende feil: $feil")
                }
                observers.forEach { e ->
                    // TODO: Fire and forget. Det vil logges i observerne, men vil ikke kunne resende denne dersom dette feiler.
                    e.handle(it.hendelse)
                }
                it.sak
            }
        }
    }

    private fun persisterBrevKlartForSending(
        tx: TransactionContext,
        brev: LagBrevRequest,
        sakId: UUID,
        søknadId: UUID,
    ) {
        brev.tilDokument(clock) {
            brevService.lagBrev(it).mapLeft {
                log.error("Kunne ikke konvertere LagBrevRequest til dokument ved lukking av søknad $søknadId og søknadsbehandling. Underliggende grunn: $it")
                LagBrevRequest.KunneIkkeGenererePdf
            }
        }.getOrHandle {
            throw IllegalArgumentException("Kunne ikke konvertere LagBrevRequest til dokument ved lukking av søknad $søknadId og søknadsbehandling. Underliggende grunn: $it")
        }.let {
            brevService.lagreDokument(
                it.leggTilMetadata(
                    metadata = Dokument.Metadata(
                        sakId = sakId,
                        søknadId = søknadId,
                        bestillBrev = true,
                    ),
                ),
                tx,
            )
        }
    }

    override fun lagBrevutkast(
        command: LukkSøknadCommand,
    ): Pair<Fnr, ByteArray> {
        val søknadId = command.søknadId
        val søknad = søknadService.hentSøknad(søknadId).getOrHandle {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - Fant ikke sak. Underliggende feil: $it")
        } as? Søknad.Journalført.MedOppgave.IkkeLukket
            ?: throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - Søknad i feil tilstand.")
        val fnr = søknad.fnr
        val brevRequest = søknad.toBrevRequest(
            lukkSøknadCommand = command,
            hentPerson = {
                personService.hentPerson(fnr).getOrHandle {
                    throw RuntimeException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - fant ikke person. Underliggende feil: $it")
                }
            },
            clock = clock,
            hentSaksbehandlerNavn = { identClient.hentNavnForNavIdent(it).getOrHandle { "" } },
            hentSaksnummer = {
                sakService.hentSakidOgSaksnummer(fnr).getOrHandle {
                    throw RuntimeException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - fant ikke saksnummer. Underliggende feil: $it")
                }.saksnummer
            },
        ).getOrHandle {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - kan ikke lages brev i denne tilstanden. Underliggende feil: $it")
        }
        return brevService.lagBrev(brevRequest).getOrHandle {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - feil ved generering av brev. Underliggende feil: $it")
        }.let {
            Pair(fnr, it)
        }
    }
}
