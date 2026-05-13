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
import no.nav.su.se.bakover.domain.oppgave.ALLEREDE_FERDIGSTILT
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
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
    private val sakStatistikkService: SakStatistikkService,
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
            sak.lukkSøknadOgSøknadsbehandling(command).let { søknadswrapper ->
                søknadswrapper.lagBrevRequest.onRight { lagBrevRequest ->
                    persisterBrevKlartForSending(
                        tx = tx,
                        genererDokumentCommand = lagBrevRequest,
                        sakId = søknadswrapper.sak.id,
                        søknadId = søknadswrapper.søknad.id,
                    )
                }
                søknadService.persisterSøknad(søknadswrapper.søknad, tx)
                søknadsbehandlingService.persisterSøknadsbehandling(søknadswrapper.søknadsbehandling, tx)

                val avvistForTidligSøknad = command is LukkSøknadCommand.MedBrev.AvvistSøknad
                val sakStatistikkEvent = StatistikkEvent.Behandling.Søknad.Lukket(
                    søknadswrapper.søknadsbehandling,
                    command.saksbehandler,
                    avvistForTidligSøknad,
                )
                observers.notify(sakStatistikkEvent, tx)
                sakStatistikkService.lagre(sakStatistikkEvent, tx)
                // Bruk søknadsbehandlingens oppgaveId. For en vanlig (ikke-omgjøring) søknadsbehandling er
                // denne identisk med søknad.oppgaveId. For en omgjøring opprettes en ny oppgave på behandlingen
                // mens søknad.oppgaveId fortsatt peker på den opprinnelige (allerede ferdigstilte) oppgaven.
                val oppgaveId = søknadswrapper.søknadsbehandling.oppgaveId
                log.info("Forsøker å ferdigstille oppgave med id $oppgaveId for søknad id ${søknadswrapper.søknad.id} og saksnummer ${sak.saksnummer}")
                oppgaveService.lukkOppgave(
                    oppgaveId = oppgaveId,
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(command.saksbehandler.navIdent),
                ).onLeft { feil ->
                    if (feil.feilPgaAlleredeFerdigstilt()) {
                        log.warn("$ALLEREDE_FERDIGSTILT Oppgave med id $oppgaveId er allerede ferdigstilt, for søknad id ${søknadswrapper.søknad.id}")
                    } else {
                        // Fire and forget. De som følger med på alerts kan evt. gi beskjed til saksbehandlerene.
                        log.error("Kunne ikke lukke oppgave knyttet til søknad/søknadsbehandling med søknadId ${søknadswrapper.søknad.id} og oppgaveId $oppgaveId saksnummer: ${sak.saksnummer}. Underliggende feil $feil. Se sikkerlogg for mer context.")
                        sikkerLogg.error("Kunne ikke lukke oppgave knyttet til søknad/søknadsbehandling med søknadId ${søknadswrapper.søknad.id} og oppgaveId $oppgaveId saksnummer: ${sak.saksnummer}. Underliggende feil: ${feil.toSikkerloggString()}.")
                    }
                }.also { log.info("Ferdigstilte oppgave med id $oppgaveId for søknad id ${søknadswrapper.søknad.id} og saksnummer ${sak.saksnummer}") }

                Triple(søknadswrapper.søknad, søknadswrapper.søknadsbehandling, søknadswrapper.sak.fnr)
            }
        }
    }

    private fun persisterBrevKlartForSending(
        tx: TransactionContext,
        genererDokumentCommand: GenererDokumentCommand,
        sakId: UUID,
        søknadId: UUID,
    ) {
        return brevService.lagDokumentPdf(genererDokumentCommand).getOrElse {
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
        return brevService.lagDokumentPdf(pdfCommand).getOrElse {
            throw IllegalArgumentException("Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - feil ved generering av brev. Underliggende feil: $it")
        }.let {
            Pair(fnr, it.generertDokument)
        }
    }
}
