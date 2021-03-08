package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjonFeil
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak
import org.slf4j.LoggerFactory
import java.time.Clock

interface FerdigstillVedtakService {
    fun journalførOgLagre(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev, Vedtak>
    fun distribuerOgLagre(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev, Vedtak>
    fun lukkOppgave(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak>

    sealed class KunneIkkeFerdigstilleVedtak {

        sealed class KunneIkkeJournalføreBrev : KunneIkkeFerdigstilleVedtak() {
            object FantIkkeNavnPåSaksbehandlerEllerAttestant : KunneIkkeJournalføreBrev()
            object FantIkkePerson : KunneIkkeJournalføreBrev()
            object FeilVedJournalføring : KunneIkkeJournalføreBrev()
            data class AlleredeJournalført(val journalpostId: JournalpostId) : KunneIkkeJournalføreBrev()
        }

        sealed class KunneIkkeDistribuereBrev : KunneIkkeFerdigstilleVedtak() {
            object MåJournalføresFørst : KunneIkkeDistribuereBrev()
            data class FeilVedDistribusjon(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
            data class AlleredeDistribuert(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
        }

        object KunneIkkeLukkeOppgave : KunneIkkeFerdigstilleVedtak()
    }
}

// TODO handle metrics?
// TODO handle statistikk?
internal class FerdigstillVedtakServiceImpl(
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val vedtakRepo: VedtakRepo,
    private val personService: PersonService,
    private val microsoftGraphApiOppslag: MicrosoftGraphApiOppslag,
    private val clock: Clock
) : FerdigstillVedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun journalførOgLagre(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev, Vedtak> {
        val brevRequest = lagBrevRequest(vedtak).getOrHandle { return it.left() }
        return vedtak.journalfør {
            brevService.journalførBrev(brevRequest, vedtak.behandling.saksnummer)
                .mapLeft { JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre.FeilVedJournalføring }
        }.mapLeft {
            when (it) {
                is JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre.AlleredeJournalført -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.AlleredeJournalført(it.journalpostId)
                }
                JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre.FeilVedJournalføring -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FeilVedJournalføring
                }
            }
        }.map { journalførtVedtak ->
            vedtakRepo.lagre(journalførtVedtak)
            journalførtVedtak
        }
    }

    override fun distribuerOgLagre(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev, Vedtak> {
        return vedtak.distribuerBrev { journalpostId ->
            brevService.distribuerBrev(journalpostId)
                .mapLeft { JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(journalpostId) }
        }.mapLeft {
            when (it) {
                is JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.AlleredeDistribuert(it.journalpostId)
                }
                is JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.FeilVedDistribusjon(it.journalpostId)
                }
                JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.MåJournalføresFørst -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.MåJournalføresFørst
                }
            }
        }.map { distribuertVedtak ->
            vedtakRepo.lagre(distribuertVedtak)
            distribuertVedtak
        }
    }

    private fun lagBrevRequest(visitable: Visitable<LagBrevRequestVisitor>): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev, LagBrevRequest> {
        return lagBrevVisitor().let { visitor ->
            visitable.accept(visitor)
            visitor.brevRequest
                .mapLeft {
                    when (it) {
                        LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> {
                            KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant
                        }
                        LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson -> {
                            KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkePerson
                        }
                    }
                }
        }
    }

    private fun lagBrevVisitor(): LagBrevRequestVisitor = LagBrevRequestVisitor(
        hentPerson = { fnr ->
            personService.hentPersonMedSystembruker(fnr)
                .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson }
        },
        hentNavn = { ident ->
            hentNavnForNavIdent(ident)
                .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant }
        },
        clock = clock,
    )

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiOppslag.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }

    override fun lukkOppgave(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak> {
        return oppgaveService.lukkOppgave(vedtak.behandling.oppgaveId)
            .mapLeft {
                KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave
            }.map {
                vedtak
            }
    }
}
