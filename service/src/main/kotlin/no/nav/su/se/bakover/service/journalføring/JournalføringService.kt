package no.nav.su.se.bakover.service.journalføring

import arrow.core.Either
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.KunneIkkeBestilleBrevForDokument
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.service.dokument.JournalførDokumentService
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.Logger
import java.util.UUID

/**
 * Wrapper klasse som kaller journalfør() for alle klasser. ment for å bli kallt fra en jobb.
 */
class JournalføringService(
    private val journalførDokumentService: JournalførDokumentService,
    private val journalførSkattDokumentService: JournalførSkattDokumentService,
) {
    fun journalfør() {
        journalførDokumentService.journalfør()
        journalførSkattDokumentService.journalførAlleSkattedokumenter()
    }
}

sealed interface JournalføringOgDistribueringsResultat {
    val id: UUID
    val journalpostId: JournalpostId?
    val brevbestillingsId: BrevbestillingId?

    data class Ok(
        override val id: UUID,
        override val journalpostId: JournalpostId?,
        override val brevbestillingsId: BrevbestillingId?,
    ) : JournalføringOgDistribueringsResultat

    data class Feil(
        override val id: UUID,
        override val journalpostId: JournalpostId?,
        val originalFeil: JournalføringOgDistribueringsFeil,
    ) : JournalføringOgDistribueringsResultat {
        override val brevbestillingsId: BrevbestillingId? = null
    }

    sealed interface JournalføringOgDistribueringsFeil {
        @JvmInline
        value class Distribuering(val originalFeil: KunneIkkeBestilleBrevForDokument) :
            JournalføringOgDistribueringsFeil

        @JvmInline
        value class Journalføring(val originalFeil: KunneIkkeJournalføreDokument) : JournalføringOgDistribueringsFeil
    }

    companion object {
        fun List<JournalføringOgDistribueringsResultat>.ok() = this.filterIsInstance<Ok>().map { it.id }
        fun List<JournalføringOgDistribueringsResultat>.feil() = this.filterIsInstance<Feil>().map { it.id }
        fun List<JournalføringOgDistribueringsResultat>.logResultat(logContext: String, log: Logger) {
            this.ifNotEmpty {
                val ok = this.ok()
                val feil = this.feil()
                if (feil.isEmpty()) {
                    log.info("$logContext $ok")
                } else {
                    log.error("$logContext feilet: $feil. Disse gikk ok: $ok")
                }
            }
        }

        /**
         * dokumentdistribusjonen må for i feil-caser
         */
        @JvmName("dokumentDistribusjonTilResultat")
        fun Either<KunneIkkeBestilleBrevForDokument, Dokumentdistribusjon>.tilResultat(
            dokument: Dokumentdistribusjon,
            log: Logger,
        ): JournalføringOgDistribueringsResultat {
            return this.fold(
                ifLeft = {
                    log.error(
                        "Kunne ikke distribuere dokument ${dokument.id}: $it",
                        RuntimeException("Genererer en stacktrace for enklere debugging."),
                    )
                    Feil(
                        dokument.id,
                        dokument.journalføringOgBrevdistribusjon.journalpostId(),
                        JournalføringOgDistribueringsFeil.Distribuering(it),
                    )
                },
                ifRight = {
                    Ok(
                        it.id,
                        it.journalføringOgBrevdistribusjon.journalpostId(),
                        it.journalføringOgBrevdistribusjon.brevbestillingsId(),
                    )
                },
            )
        }

        /**
         * dokumentdistribusjonen må for i feil-caser
         */
        fun Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon>.tilResultat(
            dokument: Dokumentdistribusjon,
            log: Logger,
        ): JournalføringOgDistribueringsResultat {
            return this.fold(
                ifLeft = {
                    log.error(
                        "Kunne ikke journalføre dokument ${dokument.id}: $it",
                        RuntimeException("Genererer en stacktrace for enklere debugging."),
                    )
                    Feil(
                        dokument.id,
                        dokument.journalføringOgBrevdistribusjon.journalpostId(),
                        JournalføringOgDistribueringsFeil.Journalføring(it),
                    )
                },
                ifRight = {
                    Ok(
                        it.id,
                        it.journalføringOgBrevdistribusjon.journalpostId(),
                        it.journalføringOgBrevdistribusjon.brevbestillingsId(),
                    )
                },
            )
        }

        @JvmName("skattedokumentTilReesultat")
        fun Either<KunneIkkeJournalføreDokument, Skattedokument.Journalført>.tilResultat(
            skattedokument: Skattedokument.Generert,
            log: Logger,
        ): JournalføringOgDistribueringsResultat {
            return this.fold(
                ifLeft = {
                    log.error(
                        "Kunne ikke journalføre skattedokument ${skattedokument.id}: $it",
                        RuntimeException("Genererer en stacktrace for enklere debugging."),
                    )
                    Feil(
                        skattedokument.id,
                        skattedokument.journalpostid,
                        JournalføringOgDistribueringsFeil.Journalføring(it),
                    )
                },
                ifRight = { Ok(it.id, it.journalpostid, null) },
            )
        }
    }
}
