package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtak
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtakMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import org.slf4j.LoggerFactory
import java.util.UUID

interface FerdigstillVedtakService {
    fun ferdigstillVedtakEtterUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
    ): Either<KunneIkkeFerdigstilleVedtakMedUtbetaling, Unit>

    fun ferdigstillVedtak(
        vedtakId: UUID,
    ): Either<KunneIkkeFerdigstilleVedtak, VedtakSomKanRevurderes>

    fun lukkOppgaveMedBruker(
        behandling: Behandling,
    ): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Unit>
}

class FerdigstillVedtakServiceImpl(
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val vedtakRepo: VedtakRepo,
    private val behandlingMetrics: BehandlingMetrics,
) : FerdigstillVedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Entry point for kvittering consumer.
     */
    override fun ferdigstillVedtakEtterUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
    ): Either<KunneIkkeFerdigstilleVedtakMedUtbetaling, Unit> {
        return if (utbetaling.trengerIkkeFerdigstilles()) {
            log.info("Utbetaling ${utbetaling.id} trenger ikke ferdigstilles.")
            Unit.right()
        } else {
            if (!utbetaling.kvittering.erKvittertOk()) {
                log.error("Prøver ikke å ferdigstille innvilgelse siden kvitteringen fra oppdrag ikke var OK.")
                Unit.right()
            } else {
                log.info("Ferdigstiller vedtak etter utbetaling")
                vedtakRepo.hentForUtbetaling(utbetaling.id)?.let { return ferdigstillVedtak(it).map { Unit } }
                    ?: return KunneIkkeFerdigstilleVedtakMedUtbetaling.FantIkkeVedtakForUtbetalingId(utbetaling.id)
                        .left()
                        .also { log.warn("Kunne ikke ferdigstille vedtak - fant ikke vedtaket som tilhører utbetaling ${utbetaling.id}.") }
            }
        }
    }

    override fun ferdigstillVedtak(vedtakId: UUID): Either<KunneIkkeFerdigstilleVedtak, VedtakSomKanRevurderes> {
        return vedtakRepo.hentVedtakForId(vedtakId)!!.let { vedtak ->
            vedtak as VedtakSomKanRevurderes
            ferdigstillVedtak(vedtak).onLeft {
                log.error(
                    "Kunne ikke ferdigstille vedtak ${vedtak.id}: $it",
                    RuntimeException("Trigger stacktrace for enklere debugging"),
                )
            }.onRight {
                log.info("Ferdigstilte vedtak ${vedtak.id}")
            }
        }
    }

    override fun lukkOppgaveMedBruker(behandling: Behandling): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Unit> {
        return lukkOppgaveIntern(behandling) {
            oppgaveService.lukkOppgave(it)
        }
    }

    private fun Utbetaling.trengerIkkeFerdigstilles(): Boolean {
        return erStans() || erReaktivering()
    }

    private fun ferdigstillVedtak(vedtak: VedtakSomKanRevurderes): Either<KunneIkkeFerdigstilleVedtak, VedtakSomKanRevurderes> {
        return if (vedtak.skalGenerereDokumentVedFerdigstillelse()) {
            lagreDokument(vedtak).getOrElse { return it.left() }
            lukkOppgaveMedSystembruker(vedtak.behandling)
            vedtak.right()
        } else {
            lukkOppgaveMedSystembruker(vedtak.behandling)
            vedtak.right()
        }
    }

    private fun lagreDokument(vedtak: VedtakSomKanRevurderes): Either<KunneIkkeFerdigstilleVedtak, VedtakSomKanRevurderes> {
        return brevService.lagDokument(vedtak).mapLeft {
            KunneIkkeFerdigstilleVedtak.KunneIkkeGenerereBrev(it)
        }.map {
            brevService.lagreDokument(
                it.leggTilMetadata(
                    metadata = Dokument.Metadata(
                        sakId = vedtak.behandling.sakId,
                        søknadId = null,
                        vedtakId = vedtak.id,
                        revurderingId = null,
                    ),
                ),
            )
            vedtak
        }
    }

    private fun lukkOppgaveMedSystembruker(behandling: Behandling): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Unit> {
        return lukkOppgaveIntern(behandling) {
            oppgaveService.lukkOppgaveMedSystembruker(it)
        }
    }

    private fun lukkOppgaveIntern(
        behandling: Behandling,
        lukkOppgave: (oppgaveId: OppgaveId) -> Either<KunneIkkeLukkeOppgave, Unit>,
    ): Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Unit> {
        val oppgaveId = if (behandling is BehandlingMedOppgave) {
            behandling.oppgaveId
        } else {
            return KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave.left()
        }

        return lukkOppgave(oppgaveId).mapLeft {
            log.error("Kunne ikke lukke oppgave: $oppgaveId for behandling: ${behandling.id}")
            KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave
        }.map {
            log.info("Lukket oppgave: $oppgaveId for behandling: ${behandling.id}")
            incrementLukketOppgave(behandling)
        }
    }

    private fun incrementLukketOppgave(behandling: Behandling) {
        return when (behandling) {
            is IverksattSøknadsbehandling.Avslag -> {
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
            }

            is IverksattSøknadsbehandling.Innvilget -> {
                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
            }
            // TODO jah: Nå som vi har vedtakstyper og revurdering må vi vurdere hva vi ønsker grafer på.
            else -> Unit
        }
    }
}
