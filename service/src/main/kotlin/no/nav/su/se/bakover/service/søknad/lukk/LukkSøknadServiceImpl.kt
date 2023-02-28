package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.getOrElse
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class LukkSøknadServiceImpl(
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
        val sak = sakService.hentSakForSøknad(søknadId).getOrElse {
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
                it.lagBrevRequest.onRight { lagBrevRequest ->
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
                oppgaveService.lukkOppgave(it.søknad.oppgaveId).onLeft { feil ->
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
        }.getOrElse {
            throw IllegalArgumentException("Kunne ikke konvertere LagBrevRequest til dokument ved lukking av søknad $søknadId og søknadsbehandling. Underliggende grunn: $it")
        }.let {
            brevService.lagreDokument(
                it.leggTilMetadata(metadata = Dokument.Metadata(sakId = sakId, søknadId = søknadId)),
                tx,
            )
        }
    }

    override fun lagBrevutkast(
        command: LukkSøknadCommand,
    ): Pair<Fnr, ByteArray> {
        val søknadId = command.søknadId
        val søknad = søknadService.hentSøknad(søknadId).getOrElse {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - Fant ikke sak. Underliggende feil: $it")
        } as? Søknad.Journalført.MedOppgave.IkkeLukket
            ?: throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - Søknad i feil tilstand.")
        val fnr = søknad.fnr
        val brevRequest = søknad.toBrevRequest(
            lukkSøknadCommand = command,
            hentPerson = {
                personService.hentPerson(fnr).getOrElse {
                    throw RuntimeException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - fant ikke person. Underliggende feil: $it")
                }
            },
            clock = clock,
            hentSaksbehandlerNavn = { identClient.hentNavnForNavIdent(it).getOrElse { "" } },
            hentSaksnummer = {
                sakService.hentSakidOgSaksnummer(fnr).getOrElse {
                    throw RuntimeException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - fant ikke saksnummer. Underliggende feil: $it")
                }.saksnummer
            },
        ).getOrElse {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - kan ikke lages brev i denne tilstanden. Underliggende feil: $it")
        }
        return brevService.lagBrev(brevRequest).getOrElse {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - feil ved generering av brev. Underliggende feil: $it")
        }.let {
            Pair(fnr, it)
        }
    }
}
