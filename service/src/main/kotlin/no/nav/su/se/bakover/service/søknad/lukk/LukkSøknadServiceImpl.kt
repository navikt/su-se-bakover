package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.getOrElse
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class LukkSøknadServiceImpl(
    private val clock: Clock,
    private val søknadService: SøknadService,
    private val sakService: SakService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val søknadsbehandlingService: SøknadsbehandlingService,
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
    ): Triple<Søknad.Journalført.MedOppgave.Lukket, LukketSøknadsbehandling?, Fnr> {
        val søknadId = command.søknadId
        val sak = sakService.hentSakForSøknad(søknadId).getOrElse {
            throw IllegalArgumentException("Fant ikke sak for søknadId $søknadId")
        }
        return sessionFactory.withTransactionContext { tx ->
            sak.lukkSøknadOgSøknadsbehandling(
                lukkSøknadCommand = command,
                clock = clock,
            ).let {
                it.lagBrevRequest.onRight { lagBrevRequest ->
                    persisterBrevKlartForSending(
                        tx = tx,
                        genererDokumentCommand = lagBrevRequest,
                        sakId = it.sak.id,
                        søknadId = it.søknad.id,
                    )
                }
                søknadService.persisterSøknad(it.søknad, tx)
                søknadsbehandlingService.persisterSøknadsbehandling(it.søknadsbehandling, tx)
                oppgaveService.lukkOppgave(
                    oppgaveId = it.søknad.oppgaveId,
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(command.saksbehandler.navIdent),
                ).onLeft { feil ->
                    if (feil.feilPgaAlleredeFerdigstilt()) {
                        log.warn("Oppgave med id ${it.søknad.oppgaveId} er allerede ferdigstilt, for søknad id ${it.søknad.id}")
                    } else {
                        // Fire and forget. De som følger med på alerts kan evt. gi beskjed til saksbehandlerene.
                        log.error("Kunne ikke lukke oppgave knyttet til søknad/søknadsbehandling med søknadId ${it.søknad.id} og oppgaveId ${it.søknad.oppgaveId} saksnummer: ${sak.saksnummer}. Underliggende feil $feil. Se sikkerlogg for mer context.")
                        sikkerLogg.error("Kunne ikke lukke oppgave knyttet til søknad/søknadsbehandling med søknadId ${it.søknad.id} og oppgaveId ${it.søknad.oppgaveId} saksnummer: ${sak.saksnummer}. Underliggende feil: ${feil.toSikkerloggString()}.")
                    }
                }

                Triple(it.søknad, it.søknadsbehandling, it.sak.fnr)
            }
        }.also {
            val (_, søknadsbehandling, _) = it
            observers.forEach { e ->
                e.handle(StatistikkEvent.Behandling.Søknad.Lukket(søknadsbehandling, command.saksbehandler))
            }
        }
    }

    private fun persisterBrevKlartForSending(
        tx: TransactionContext,
        genererDokumentCommand: GenererDokumentCommand,
        sakId: UUID,
        søknadId: UUID,
    ) {
        return brevService.lagDokument(genererDokumentCommand).getOrElse {
            // TODO jah: Kan vel være en left? Lag en overload på lagreDokument som tar inn GenererDokumentCommand+Dokument.Metadata+tx.
            throw IllegalArgumentException("Kunne ikke konvertere LagBrevRequest til dokument ved lukking av søknad $søknadId og søknadsbehandling. Underliggende grunn: $it")
        }.let {
            brevService.lagreDokument(
                it.leggTilMetadata(
                    metadata = Dokument.Metadata(sakId = sakId, søknadId = søknadId),
                    // kan ikke sende brev til en annen adresse enn brukerens adresse per nå
                    distribueringsadresse = null,
                ),
                tx,
            )
        }
    }

    override fun lagBrevutkast(
        command: LukkSøknadCommand,
    ): Pair<Fnr, PdfA> {
        val søknadId = command.søknadId
        val søknad = søknadService.hentSøknad(søknadId).getOrElse {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - Fant ikke sak. Underliggende feil: $it")
        } as? Søknad.Journalført.MedOppgave.IkkeLukket
            ?: throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - Søknad i feil tilstand.")
        val fnr = søknad.fnr
        val pdfCommand = søknad.toBrevRequest(
            lukkSøknadCommand = command,
            // TODO jah: Kanskje vi kan legge saksnummer på Søknad? Da kan vi fjerne denne.
            sak = sakService.hentSakInfo(søknad.sakId).getOrElse {
                throw RuntimeException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - fant ikke saksnummer. Underliggende feil: $it")
            },
        ).getOrElse {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - kan ikke lages brev i denne tilstanden. Underliggende feil: $it")
        }
        return brevService.lagDokument(pdfCommand).getOrElse {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - feil ved generering av brev. Underliggende feil: $it")
        }.let {
            Pair(fnr, it.generertDokument)
        }
    }
}
