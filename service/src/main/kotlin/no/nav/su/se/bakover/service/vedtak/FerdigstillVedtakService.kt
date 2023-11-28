package no.nav.su.se.bakover.service.vedtak

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Stønadsbehandling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtak
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtakMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.brev.lagDokumentKommando
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import org.slf4j.LoggerFactory
import sats.domain.SatsFactory
import java.time.Clock
import java.util.UUID

interface FerdigstillVedtakService {
    fun ferdigstillVedtakEtterUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
    ): Either<KunneIkkeFerdigstilleVedtakMedUtbetaling, Unit>

    fun ferdigstillVedtak(
        vedtakId: UUID,
    ): Either<KunneIkkeFerdigstilleVedtak, VedtakSomKanRevurderes>

    fun lukkOppgaveMedBruker(
        behandling: Stønadsbehandling,
    ): Either<KunneIkkeLukkeOppgave, Unit>
}

class FerdigstillVedtakServiceImpl(
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val vedtakRepo: VedtakRepo,
    private val behandlingMetrics: BehandlingMetrics,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
) : FerdigstillVedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Entry point for kvittering consumer.
     */
    override fun ferdigstillVedtakEtterUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
    ): Either<KunneIkkeFerdigstilleVedtakMedUtbetaling, Unit> {
        // TODO jah: Gjør denne idempotent? Kan lage hendelser for oppgaven+dokumentet
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
            }.map {
                log.info("Ferdigstilte vedtak ${vedtak.id}")
                vedtak
            }
        }
    }

    override fun lukkOppgaveMedBruker(behandling: Stønadsbehandling): Either<KunneIkkeLukkeOppgave, Unit> {
        return lukkOppgaveIntern(behandling) {
            oppgaveService.lukkOppgave(it).map { }
        }.map { /* Unit */ }
    }

    private fun Utbetaling.trengerIkkeFerdigstilles(): Boolean {
        return erStans() || erReaktivering()
    }

    private fun ferdigstillVedtak(
        vedtak: VedtakSomKanRevurderes,
    ): Either<KunneIkkeFerdigstilleVedtak, VedtakFerdigstilt> {
        return if (vedtak.skalGenerereDokumentVedFerdigstillelse()) {
            val dokument = lagreDokument(vedtak).getOrElse { return it.left() }
            lukkOppgaveMedSystembruker(vedtak.behandling).fold(
                {
                    VedtakFerdigstilt.DokumentLagret.KunneIkkeLukkeOppgave(dokument, it.oppgaveId).right()
                },
                { oppgaveId ->
                    if (oppgaveId != null) {
                        VedtakFerdigstilt.DokumentLagret.OppgaveLukket(dokument, oppgaveId).right()
                    } else {
                        VedtakFerdigstilt.DokumentLagret.SkalIkkeLukkeOppgave(dokument).right()
                    }
                },
            )
        } else {
            // I disse tilfellene skal det ikke sendes brev, men vi prøver likevel å lukke oppgaven, dersom det finnes en.
            lukkOppgaveMedSystembruker(vedtak.behandling).fold(
                {
                    VedtakFerdigstilt.DokumentSkalIkkeLagres.KunneIkkeLukkeOppgave(it.oppgaveId).right()
                },
                { oppgaveId ->
                    if (oppgaveId != null) {
                        VedtakFerdigstilt.DokumentSkalIkkeLagres.OppgaveLukket(oppgaveId).right()
                    } else {
                        VedtakFerdigstilt.DokumentSkalIkkeLagres.SkalIkkeLukkeOppgave.right()
                    }
                },
            )
        }
    }

    private fun lagreDokument(vedtak: VedtakSomKanRevurderes): Either<KunneIkkeFerdigstilleVedtak, Dokument.MedMetadata> {
        return brevService.lagDokument(vedtak.lagDokumentKommando(clock, satsFactory)).mapLeft {
            KunneIkkeFerdigstilleVedtak.KunneIkkeGenerereBrev(it)
        }.map {
            val dokumentMedMetadata: Dokument.MedMetadata = it.leggTilMetadata(
                metadata = Dokument.Metadata(
                    sakId = vedtak.behandling.sakId,
                    søknadId = null,
                    vedtakId = vedtak.id,
                    revurderingId = null,
                ),
            )
            brevService.lagreDokument(
                dokumentMedMetadata,
            )
            dokumentMedMetadata
        }
    }

    /**
     * @return null dersom behandlingen ikke har oppgave som skal lukkes.
     */
    private fun lukkOppgaveMedSystembruker(
        behandling: Stønadsbehandling,
    ): Either<KunneIkkeLukkeOppgave, OppgaveId?> {
        return lukkOppgaveIntern(behandling) {
            oppgaveService.lukkOppgaveMedSystembruker(it).map {
                it.oppgaveId
            }
        }
    }

    /**
     * @return null dersom behandlingen ikke har oppgave som skal lukkes.
     */
    private fun lukkOppgaveIntern(
        behandling: Stønadsbehandling,
        lukkOppgave: (oppgaveId: OppgaveId) -> Either<KunneIkkeLukkeOppgave, Unit>,
    ): Either<KunneIkkeLukkeOppgave, OppgaveId?> {
        val oppgaveId = if (behandling is BehandlingMedOppgave) {
            behandling.oppgaveId
        } else {
            return null.right()
        }

        return lukkOppgave(oppgaveId).onLeft {
            log.error("Kunne ikke lukke oppgave: $oppgaveId for behandling: ${behandling.id}")
        }.map {
            log.info("Lukket oppgave: $oppgaveId for behandling: ${behandling.id}")
            incrementLukketOppgave(behandling)
            oppgaveId
        }
    }

    private fun incrementLukketOppgave(behandling: Stønadsbehandling) {
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

/**
 * Inneholder kun de positive utfallene (Right).
 * I alle tilfeller vil dokument være lagret (dersom vi skal sende brev)
 */
sealed interface VedtakFerdigstilt {
    val dokument: Dokument.MedMetadata?
    val oppgaveId: OppgaveId?

    sealed interface DokumentLagret : VedtakFerdigstilt {

        override val dokument: Dokument.MedMetadata

        data class OppgaveLukket(
            override val dokument: Dokument.MedMetadata,
            override val oppgaveId: OppgaveId,
        ) : DokumentLagret

        data class SkalIkkeLukkeOppgave(
            override val dokument: Dokument.MedMetadata,
        ) : DokumentLagret {
            override val oppgaveId = null
        }

        data class KunneIkkeLukkeOppgave(
            override val dokument: Dokument.MedMetadata,
            override val oppgaveId: OppgaveId,
        ) : DokumentLagret
    }

    sealed interface DokumentSkalIkkeLagres : VedtakFerdigstilt {

        override val dokument: Dokument.MedMetadata? get() = null

        data class OppgaveLukket(
            override val oppgaveId: OppgaveId,
        ) : DokumentSkalIkkeLagres

        data object SkalIkkeLukkeOppgave : DokumentSkalIkkeLagres {
            override val oppgaveId = null
        }

        data class KunneIkkeLukkeOppgave(
            override val oppgaveId: OppgaveId,
        ) : DokumentSkalIkkeLagres
    }
}
