package no.nav.su.se.bakover.vedtak.application

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.domain.BehandlingMedOppgave
import behandling.domain.Stønadsbehandling
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtak
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtakMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.brev.lagDokumentKommando
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock
import java.util.UUID

interface FerdigstillVedtakService {
    fun ferdigstillVedtakEtterUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
        transactionContext: TransactionContext? = null,
    ): Either<KunneIkkeFerdigstilleVedtakMedUtbetaling, Unit>

    fun ferdigstillVedtak(
        vedtakId: UUID,
    ): Either<KunneIkkeFerdigstilleVedtak, VedtakSomKanRevurderes>

    /**
     * TODO jah: Brukes kun av IverksettSøknadsbehandlingServiceImpl, flytt dit?
     */
    fun lukkOppgaveMedBruker(
        behandling: Stønadsbehandling,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ): Either<KunneIkkeLukkeOppgave, Unit>
}

class FerdigstillVedtakServiceImpl(
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val vedtakService: VedtakService,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
) : FerdigstillVedtakService {
    private val log = LoggerFactory.getLogger(this::class.java)

    /** Laget spesifikt for [økonomi.application.kvittering.FerdigstillVedtakEtterMottattKvitteringKonsument] */
    override fun ferdigstillVedtakEtterUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
        transactionContext: TransactionContext?,
    ): Either<KunneIkkeFerdigstilleVedtakMedUtbetaling, Unit> {
        return if (utbetaling.trengerIkkeFerdigstilles()) {
            // Merk at vi heller ikke prøver lukke oppgaver her. Det går fint så lenge vi ikke oppretter oppgaver for stans/gjenopptak.
            log.info("Utbetaling trenger ikke ferdigstilles. Lukker heller ikke oppgaver. UtbetalingId: ${utbetaling.id}, sakId: ${utbetaling.sakId}, saksnummer: ${utbetaling.saksnummer}")
            Unit.right()
        } else {
            if (!utbetaling.kvittering.erKvittertOk()) {
                log.error("Prøver ikke å ferdigstille innvilgelse siden kvitteringen fra oppdrag ikke var OK. Denne vil ikke bli prøvd på nytt. UtbetalingId: ${utbetaling.id}, sakId: ${utbetaling.sakId}, saksnummer: ${utbetaling.saksnummer}, kvittering: ${utbetaling.kvittering.utbetalingsstatus}")
                Unit.right()
            } else {
                log.info("Kvittering OK. Ferdigstiller vedtak etter utbetaling. UtbetalingId: ${utbetaling.id}, sakId: ${utbetaling.sakId}, saksnummer: ${utbetaling.saksnummer}")
                val vedtak = vedtakService.hentForUtbetaling(utbetaling.id, transactionContext)
                    ?: return KunneIkkeFerdigstilleVedtakMedUtbetaling.FantIkkeVedtakForUtbetalingId(utbetaling.id)
                        .left().also {
                            log.error("Kunne ikke ferdigstille vedtak - fant ikke vedtaket som tilhører utbetaling. UtbetalingId: ${utbetaling.id}, sakId: ${utbetaling.sakId}, saksnummer: ${utbetaling.saksnummer}")
                        }
                return ferdigstillVedtak(vedtak, transactionContext).map { Unit }
            }
        }
    }

    override fun ferdigstillVedtak(
        vedtakId: UUID,
    ): Either<KunneIkkeFerdigstilleVedtak, VedtakSomKanRevurderes> {
        return vedtakService.hentForVedtakId(vedtakId)!!.let { vedtak ->
            vedtak as VedtakSomKanRevurderes
            ferdigstillVedtak(vedtak, null).onLeft {
                log.error(
                    "Kunne ikke ferdigstille vedtak. VedtakId: $vedtakId, behandlingId: ${vedtak.behandling.id}, sakId: ${vedtak.behandling.sakId}, saksnummer: ${vedtak.behandling.saksnummer}. Feil: $it.",
                    RuntimeException("Trigger stacktrace for enklere debugging"),
                )
            }.map {
                log.info("Ferdigstilte vedtak. VedtakId: $vedtakId, behandlingId: ${vedtak.behandling.id}, sakId: ${vedtak.behandling.sakId}, saksnummer: ${vedtak.behandling.saksnummer}")
                vedtak
            }
        }
    }

    override fun lukkOppgaveMedBruker(
        behandling: Stønadsbehandling,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ): Either<KunneIkkeLukkeOppgave, Unit> {
        return lukkOppgaveIntern(behandling) {
            oppgaveService.lukkOppgave(it, tilordnetRessurs).map { }
        }.map { /* Unit */ }
    }

    private fun Utbetaling.trengerIkkeFerdigstilles(): Boolean {
        return erStans() || erReaktivering()
    }

    private fun ferdigstillVedtak(
        vedtak: VedtakSomKanRevurderes,
        transactionContext: TransactionContext?,
    ): Either<KunneIkkeFerdigstilleVedtak, VedtakFerdigstilt> {
        val tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(vedtak.attestant.navIdent)

        // Denne fungerer også som dedup. Dersom vi allerede har lagret et dokument knyttet til vedtaket vil denne gi false.
        return if (vedtak.skalGenerereDokumentVedFerdigstillelse()) {
            val dokument = lagreDokument(vedtak, transactionContext).getOrElse { return it.left() }
            lukkOppgaveMedSystembruker(vedtak.behandling, tilordnetRessurs).fold(
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
            lukkOppgaveMedSystembruker(vedtak.behandling, tilordnetRessurs).fold(
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

    private fun lagreDokument(
        vedtak: VedtakSomKanRevurderes,
        transactionContext: TransactionContext?,
    ): Either<KunneIkkeFerdigstilleVedtak, Dokument.MedMetadata> {
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
                // kan ikke sende vedtaksbrev til en annen adresse enn brukerens adresse per nå
                distribueringsadresse = null,
            )
            brevService.lagreDokument(dokumentMedMetadata, transactionContext)

            dokumentMedMetadata
        }
    }

    /**
     * @return null dersom behandlingen ikke har oppgave som skal lukkes.
     */
    private fun lukkOppgaveMedSystembruker(
        behandling: Stønadsbehandling,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ): Either<KunneIkkeLukkeOppgave, OppgaveId?> {
        return lukkOppgaveIntern(behandling) {
            oppgaveService.lukkOppgaveMedSystembruker(it, tilordnetRessurs).map {
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
            if (it.feilPgaAlleredeFerdigstilt()) {
                log.warn("Kunne ikke lukke oppgave fordi den allerede er ferdigstilt. OppgaveId: $oppgaveId for behandlingId: ${behandling.id}, sakId: ${behandling.sakId}, saksnummer: ${behandling.saksnummer}. Feil: $it. Se sikklerlogg for detaljer.", RuntimeException("Trigger stacktrace for enklere debugging"))
                sikkerLogg.warn("Kunne ikke lukke oppgave fordi den allerede er ferdigstilt. OppgaveId: $oppgaveId for behandlingId: ${behandling.id}, sakId: ${behandling.sakId}, saksnummer: ${behandling.saksnummer}. Feil: ${it.toSikkerloggString()}")
                return null.right()
            }
            log.error(
                "Kunne ikke lukke oppgave. OppgaveId: $oppgaveId for behandlingId: ${behandling.id}, sakId: ${behandling.sakId}, saksnummer: ${behandling.saksnummer}. Feil: $it. Se sikkerlogg for detaljer.",
                RuntimeException("Trigger stacktrace for enklere debugging"),
            )
            sikkerLogg.error("Kunne ikke lukke oppgave. OppgaveId: $oppgaveId for behandlingId: ${behandling.id}, sakId: ${behandling.sakId}, saksnummer: ${behandling.saksnummer}. Feil: ${it.toSikkerloggString()}")
        }.map {
            log.info("Lukket oppgave. OppgaveId: $oppgaveId for behandlingId: ${behandling.id}, sakId: ${behandling.sakId}, saksnummer: ${behandling.saksnummer}")
            oppgaveId
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
