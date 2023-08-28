package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.getOrElse
import dokument.domain.Dokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.brev.command.GenererDokumentCommand
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.slf4j.LoggerFactory
import java.util.UUID

class LukkSøknadServiceImpl(
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
    ): Sak {
        val søknadId = command.søknadId
        val sak = sakService.hentSakForSøknad(søknadId).getOrElse {
            throw IllegalArgumentException("Fant ikke sak for søknadId $søknadId")
        }
        return sessionFactory.withTransactionContext { tx ->
            sak.lukkSøknadOgSøknadsbehandling(
                lukkSøknadCommand = command,
                saksbehandler = command.saksbehandler,
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
        genererDokumentCommand: GenererDokumentCommand,
        sakId: UUID,
        søknadId: UUID,
    ) {
        return brevService.lagDokument(genererDokumentCommand).getOrElse {
            // TODO jah: Kan vel være en left? Lag en overload på lagreDokument som tar inn GenererDokumentCommand+Dokument.Metadata+tx.
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
            hentSaksnummer = {
                sakService.hentSakidOgSaksnummer(fnr).getOrElse {
                    throw RuntimeException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - fant ikke saksnummer. Underliggende feil: $it")
                }.saksnummer
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
