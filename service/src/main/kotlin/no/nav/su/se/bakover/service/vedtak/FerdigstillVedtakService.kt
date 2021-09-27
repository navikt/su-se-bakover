package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak
import org.slf4j.LoggerFactory
import java.time.Clock

interface FerdigstillVedtakService {
    fun ferdigstillVedtakEtterUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering)
    fun lukkOppgaveMedBruker(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak>

    sealed class KunneIkkeFerdigstilleVedtak {

        sealed class KunneIkkeJournalføreBrev : KunneIkkeFerdigstilleVedtak() {
            object FantIkkeNavnPåSaksbehandlerEllerAttestant : KunneIkkeJournalføreBrev()
            object FantIkkePerson : KunneIkkeJournalføreBrev()
            object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeJournalføreBrev()
        }

        object KunneIkkeGenerereBrev : KunneIkkeFerdigstilleVedtak()
        object KunneIkkeLukkeOppgave : KunneIkkeFerdigstilleVedtak()
    }
}

internal class FerdigstillVedtakServiceImpl(
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val vedtakRepo: VedtakRepo,
    private val personService: PersonService,
    private val utbetalingService: UtbetalingService,
    private val microsoftGraphApiOppslag: MicrosoftGraphApiOppslag,
    private val clock: Clock,
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

    private fun ferdigstillVedtak(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak, Vedtak> {
        // TODO jm: sjekk om vi allerede har distribuert?
        return if (vedtak.skalSendeBrev()) {
            lagreDokumentJournalførOgDistribuer(vedtak).getOrHandle { return it.left() }
            lukkOppgaveMedSystembruker(vedtak)
            vedtak.right()
        } else {
            lukkOppgaveMedSystembruker(vedtak)
            vedtak.right()
        }
    }

    fun lagreDokumentJournalførOgDistribuer(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak, Vedtak> {
        val brevRequest = lagBrevRequest(vedtak)
            .getOrHandle { return it.left() }
        val dokument = brevRequest.tilDokument {
            brevService.lagBrev(it)
                .mapLeft { LagBrevRequest.KunneIkkeGenererePdf }
        }.map {
            it.leggTilMetadata(
                metadata = Dokument.Metadata(
                    sakId = vedtak.behandling.sakId,
                    søknadId = null,
                    vedtakId = vedtak.id,
                    revurderingId = null,
                    bestillBrev = true,
                ),
            )
        }.getOrHandle { return KunneIkkeFerdigstilleVedtak.KunneIkkeGenerereBrev.left() }

        brevService.lagreDokument(dokument)

        return vedtak.right()
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
                        LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling -> {
                            KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.KunneIkkeFinneGjeldendeUtbetaling
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
        hentGjeldendeUtbetaling = { sakId, forDato ->
            utbetalingService.hentGjeldendeUtbetaling(sakId, forDato)
                .bimap(
                    { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling },
                    { it.beløp },
                )
        },
        clock = clock,
    )

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiOppslag.hentNavnForNavIdent(navIdent)
            .mapLeft { KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant }
    }

    private fun lukkOppgaveMedSystembruker(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak> {
        return lukkOppgaveIntern(vedtak) {
            oppgaveService.lukkOppgaveMedSystembruker(it)
        }
    }

    override fun lukkOppgaveMedBruker(vedtak: Vedtak): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak> {
        return lukkOppgaveIntern(vedtak) {
            oppgaveService.lukkOppgave(it)
        }
    }

    private fun lukkOppgaveIntern(
        vedtak: Vedtak,
        lukkOppgave: (oppgaveId: OppgaveId) -> Either<KunneIkkeLukkeOppgave, Unit>,
    ): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak> {
        val oppgaveId = if (vedtak.behandling is BehandlingMedOppgave) {
            (vedtak.behandling as BehandlingMedOppgave).oppgaveId
        } else {
            return KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave.left()
        }

        return lukkOppgave(oppgaveId)
            .mapLeft {
                log.error("Kunne ikke lukke oppgave: $oppgaveId for behandling: ${vedtak.behandling.id}")
                KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave
            }.map {
                log.info("Lukket oppgave: $oppgaveId for behandling: ${vedtak.behandling.id}")
                incrementLukketOppgave(vedtak)
                vedtak
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
