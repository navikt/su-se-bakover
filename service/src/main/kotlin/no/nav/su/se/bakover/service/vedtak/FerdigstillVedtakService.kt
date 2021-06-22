package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

interface FerdigstillVedtakService {
    fun ferdigstillVedtakEtterUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering)
    fun opprettManglendeJournalposterOgBrevbestillinger(): OpprettManglendeJournalpostOgBrevdistribusjonResultat
    fun journalførOgLagre(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev, Vedtak>
    fun distribuerOgLagre(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev, Vedtak>
    fun lukkOppgaverKnyttetTilVedtak(vedtak: List<Vedtak>)
    fun lukkOppgaveMedBruker(
        vedtak: Vedtak,
    ): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak>

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

    data class KunneIkkeOppretteJournalpostForIverksetting(
        val sakId: UUID,
        val behandlingId: UUID,
        val grunn: String,
    )

    data class OpprettetJournalpostForIverksetting(
        val sakId: UUID,
        val behandlingId: UUID,
        val journalpostId: JournalpostId,
    )

    data class BestiltBrev(
        val sakId: UUID,
        val behandlingId: UUID,
        val journalpostId: JournalpostId,
        val brevbestillingId: BrevbestillingId,
    )

    data class KunneIkkeBestilleBrev(
        val sakId: UUID,
        val behandlingId: UUID,
        val journalpostId: JournalpostId?,
        val grunn: String,
    )

    data class OpprettManglendeJournalpostOgBrevdistribusjonResultat(
        val journalpostresultat: List<Either<KunneIkkeOppretteJournalpostForIverksetting, OpprettetJournalpostForIverksetting>>,
        val brevbestillingsresultat: List<Either<KunneIkkeBestilleBrev, BestiltBrev>>,
        val vedtak: List<Vedtak>
    ) {
        fun harFeil(): Boolean = journalpostresultat.mapNotNull { it.swap().orNull() }.isNotEmpty() ||
            brevbestillingsresultat.mapNotNull { it.swap().orNull() }.isNotEmpty()
    }
}

internal class FerdigstillVedtakServiceImpl(
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val vedtakRepo: VedtakRepo,
    private val personService: PersonService,
    private val microsoftGraphApiOppslag: MicrosoftGraphApiOppslag,
    private val clock: Clock,
    private val utbetalingRepo: UtbetalingRepo,
    private val behandlingMetrics: BehandlingMetrics,
) : FerdigstillVedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Entry point for kvittering consumer.
     */
    override fun ferdigstillVedtakEtterUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering) {
        return when (utbetaling.type) {
            Utbetaling.UtbetalingsType.STANS,
            Utbetaling.UtbetalingsType.GJENOPPTA,
            -> {
                log.info("Utbetaling ${utbetaling.id} er av type ${utbetaling.type} og vil derfor ikke bli prøvd ferdigstilt.")
            }
            Utbetaling.UtbetalingsType.NY,
            Utbetaling.UtbetalingsType.OPPHØR,
            -> {
                if (!utbetaling.kvittering.erKvittertOk()) {
                    log.error("Prøver ikke å ferdigstille innvilgelse siden kvitteringen fra oppdrag ikke var OK.")
                } else {
                    val vedtak = vedtakRepo.hentForUtbetaling(utbetaling.id)
                        ?: throw KunneIkkeFerdigstilleVedtakException(utbetaling.id)
                    ferdigstillVedtak(vedtak).getOrHandle {
                        throw KunneIkkeFerdigstilleVedtakException(vedtak, it)
                    }
                    Unit
                }
            }
        }
    }

    /**
     * Entry point for drift-operations
     */
    override fun opprettManglendeJournalposterOgBrevbestillinger(): FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat {
        val alleUtenJournalpost = vedtakRepo.hentUtenJournalpost()
        val innvilgetUtenJournalpost = alleUtenJournalpost.filterIsInstance<Vedtak.EndringIYtelse>()
            .filter { it.skalSendeBrev() }
            /**
             * Unngår å journalføre og distribuere brev for innvilgelser hvor vi ikke har mottatt kvittering,
             * eller mottatt kvittering ikke er ok.
             */
            .filter { innvilgetVedtak ->
                utbetalingRepo.hentUtbetaling(innvilgetVedtak.utbetalingId)!!.let {
                    it is Utbetaling.OversendtUtbetaling.MedKvittering && it.kvittering.erKvittertOk()
                }
            }
        val vedtakSet = mutableSetOf<Vedtak>()
        val avslagUtenJournalpost = alleUtenJournalpost.filterIsInstance<Vedtak.Avslag>()
        val journalpostResultat = innvilgetUtenJournalpost.plus(avslagUtenJournalpost).map { vedtak ->
            journalførOgLagre(vedtak)
                .mapLeft { feilVedJournalføring ->
                    vedtakSet.add(vedtak)

                    when (feilVedJournalføring) {
                        is KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.AlleredeJournalført -> {
                            return@map FerdigstillVedtakService.KunneIkkeOppretteJournalpostForIverksetting(
                                sakId = vedtak.behandling.sakId,
                                behandlingId = vedtak.behandling.id,
                                grunn = "Kunne ikke opprette journalpost for iverksetting siden den allerede eksisterer",
                            ).left()
                        }
                        else -> {
                            FerdigstillVedtakService.KunneIkkeOppretteJournalpostForIverksetting(
                                sakId = vedtak.behandling.sakId,
                                behandlingId = vedtak.behandling.id,
                                grunn = feilVedJournalføring.javaClass.simpleName,
                            )
                        }
                    }
                }.map { journalførtVedtak ->
                    vedtakSet.add(vedtak)

                    FerdigstillVedtakService.OpprettetJournalpostForIverksetting(
                        sakId = journalførtVedtak.behandling.sakId,
                        behandlingId = journalførtVedtak.behandling.id,
                        journalpostId = journalførtVedtak.journalføringOgBrevdistribusjon.journalpostId()!!,
                    )
                }
        }


        val alleUtenBrevbestilling = vedtakRepo.hentUtenBrevbestilling()

        val innvilgetUtenBrevbestilling = alleUtenBrevbestilling.filterIsInstance<Vedtak.EndringIYtelse>()
            .filter {
                it.skalSendeBrev()
            }
            /**
             * Unngår å journalføre og distribuere brev for innvilgelser hvor vi ikke har mottatt kvittering,
             * eller mottatt kvittering ikke er ok.
             */
            .filter { innvilgetVedtak ->
                utbetalingRepo.hentUtbetaling(innvilgetVedtak.utbetalingId)!!.let {
                    it is Utbetaling.OversendtUtbetaling.MedKvittering && it.kvittering.erKvittertOk()
                }
            }

        val avslagUtenBrevbestilling = alleUtenBrevbestilling.filterIsInstance<Vedtak.Avslag>()
        val brevbestillingResultat = innvilgetUtenBrevbestilling.plus(avslagUtenBrevbestilling).map { vedtak ->
            distribuerOgLagre(vedtak)
                .mapLeft {
                    vedtakSet.add(vedtak)

                    kunneIkkeBestilleBrev(vedtak, it)
                }
                .map { distribuertVedtak ->
                    vedtakSet.add(vedtak)

                    val steg =
                        (distribuertVedtak.journalføringOgBrevdistribusjon as JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev)
                    FerdigstillVedtakService.BestiltBrev(
                        sakId = distribuertVedtak.behandling.sakId,
                        behandlingId = distribuertVedtak.behandling.id,
                        journalpostId = steg.journalpostId,
                        brevbestillingId = steg.brevbestillingId,
                    )
                }
        }

        return FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = journalpostResultat,
            brevbestillingsresultat = brevbestillingResultat,
            vedtak = vedtakSet.toList()
        )
    }

    private fun ferdigstillVedtak(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak, Vedtak> {
        if (vedtak.skalSendeBrev()) {
            val journalførtVedtak = journalførOgLagre(vedtak).getOrHandle { feilVedJournalføring ->
                when (feilVedJournalføring) {
                    is KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.AlleredeJournalført -> vedtak
                    else -> return feilVedJournalføring.left()
                }
            }

            val distribuertVedtak = distribuerOgLagre(journalførtVedtak).getOrHandle { feilVedDistribusjon ->
                when (feilVedDistribusjon) {
                    is KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.AlleredeDistribuert -> journalførtVedtak
                    else -> return feilVedDistribusjon.left()
                }
            }

            lukkOppgaveMedSystembruker(distribuertVedtak)
            return distribuertVedtak.right()
        } else {
            lukkOppgaveMedSystembruker(vedtak)
            return vedtak.right()
        }
    }

    override fun journalførOgLagre(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev, Vedtak> {
        if (vedtak.behandling is IverksattRevurdering.IngenEndring && !(vedtak.behandling as IverksattRevurdering.IngenEndring).skalFøreTilBrevutsending) {
            return vedtak.right()
        }
        val brevRequest = lagBrevRequest(vedtak).getOrHandle { return it.left() }

        return vedtak.journalfør {
            brevService.journalførBrev(brevRequest, vedtak.behandling.saksnummer)
                .mapLeft { KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring }
        }.mapLeft {
            when (it) {
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.AlleredeJournalført(it.journalpostId)
                }
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FeilVedJournalføring
                }
            }
        }.map { journalførtVedtak ->
            log.info("Journalført brev for vedtak: ${journalførtVedtak.id}")
            vedtakRepo.lagre(journalførtVedtak)
            incrementJournalført(journalførtVedtak)
            journalførtVedtak
        }
    }

    override fun distribuerOgLagre(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev, Vedtak> {
        return vedtak.distribuerBrev { journalpostId ->
            brevService.distribuerBrev(journalpostId)
                .mapLeft {
                    KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                        journalpostId,
                    )
                }
        }.mapLeft {
            when (it) {
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.AlleredeDistribuert(it.journalpostId)
                }
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.FeilVedDistribusjon(it.journalpostId)
                }
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.MåJournalføresFørst -> {
                    KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.MåJournalføresFørst
                }
            }
        }.map { distribuertVedtak ->
            log.info("Bestilt distribusjon av brev for vedtak: ${distribuertVedtak.id}")
            vedtakRepo.lagre(distribuertVedtak)
            incrementDistribuert(vedtak)
            distribuertVedtak
        }
    }

    override fun lukkOppgaverKnyttetTilVedtak(vedtak: List<Vedtak>) =
        vedtak.forEach { lukkOppgaveMedBruker(it) }

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
        return microsoftGraphApiOppslag.hentNavnForNavIdent(navIdent)
            .mapLeft { KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant }
    }

    private fun lukkOppgaveMedSystembruker(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak> {
        return lukkOppgaveMedBruker(vedtak) {
            oppgaveService.lukkOppgaveMedSystembruker(it)
        }
    }

    override fun lukkOppgaveMedBruker(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak> {
        return lukkOppgaveMedBruker(vedtak) {
            oppgaveService.lukkOppgave(it)
        }
    }

    private fun lukkOppgaveMedBruker(
        vedtak: Vedtak,
        lukkOppgave: (oppgaveId: OppgaveId) -> Either<KunneIkkeLukkeOppgave, Unit>,
    ): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak> {
        return lukkOppgave(vedtak.behandling.oppgaveId)
            .mapLeft {
                log.error("Kunne ikke lukke oppgave: ${vedtak.behandling.oppgaveId} for behandling: ${vedtak.behandling.id}")
                KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave
            }.map {
                log.info("Lukket oppgave: ${vedtak.behandling.oppgaveId} for behandling: ${vedtak.behandling.id}")
                incrementLukketOppgave(vedtak)
                vedtak
            }
    }

    private fun kunneIkkeBestilleBrev(
        vedtak: Vedtak,
        error: Any,
    ) = FerdigstillVedtakService.KunneIkkeBestilleBrev(
        sakId = vedtak.behandling.sakId,
        behandlingId = vedtak.behandling.id,
        journalpostId = vedtak.journalføringOgBrevdistribusjon.journalpostId(),
        grunn = error.javaClass.simpleName,
    )

    private fun incrementJournalført(vedtak: Vedtak) {
        return when (vedtak.behandling) {
            is Søknadsbehandling.Iverksatt.Avslag -> {
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)
            }
            is Søknadsbehandling.Iverksatt.Innvilget -> {
                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
            }
            // TODO jah: Nå som vi har vedtakstyper og revurdering må vi vurdere hva vi ønsker grafer på.
            else -> Unit
        }
    }

    private fun incrementDistribuert(vedtak: Vedtak) {
        return when (vedtak.behandling) {
            is Søknadsbehandling.Iverksatt.Avslag -> {
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
            }
            is Søknadsbehandling.Iverksatt.Innvilget -> {
                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
            }
            // TODO jah: Nå som vi har vedtakstyper og revurdering må vi vurdere hva vi ønsker grafer på.
            else -> Unit
        }
    }

    private fun incrementLukketOppgave(vedtak: Vedtak) {
        return when (vedtak.behandling) {
            is Søknadsbehandling.Iverksatt.Avslag -> {
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
            }
            is Søknadsbehandling.Iverksatt.Innvilget -> {
                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
            }
            // TODO jah: Nå som vi har vedtakstyper og revurdering må vi vurdere hva vi ønsker grafer på.
            else -> Unit
        }
    }

    internal data class KunneIkkeFerdigstilleVedtakException private constructor(
        val msg: String,
    ) : RuntimeException(msg) {

        constructor(vedtak: Vedtak, error: KunneIkkeFerdigstilleVedtak) : this(
            "Kunne ikke ferdigstille vedtak - id: ${vedtak.id}. Original feil: ${error::class.qualifiedName}",
        )

        constructor(utbetalingId: UUID30) : this("Kunne ikke ferdigstille vedtak - fant ikke vedtaket som tilhører utbetaling $utbetalingId. Dette kan være en timing issue.")
    }
}
