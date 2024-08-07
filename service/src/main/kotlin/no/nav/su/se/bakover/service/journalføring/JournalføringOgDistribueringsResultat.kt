package no.nav.su.se.bakover.service.journalføring

import arrow.core.Either
import dokument.domain.Dokumentdistribusjon
import dokument.domain.brev.BrevbestillingId
import dokument.domain.brev.KunneIkkeBestilleBrevForDokument
import dokument.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.common.journal.JournalpostId
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.Logger
import vilkår.skatt.domain.Skattedokument
import java.util.UUID

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

    companion object {
        /**
         * dokumentdistribusjonen må for i feil-caser
         */
        @JvmName("dokumentDistribusjonTilResultat")
        fun Either<KunneIkkeBestilleBrevForDokument, Dokumentdistribusjon>.tilResultat(
            distribusjon: Dokumentdistribusjon,
            log: Logger,
        ): JournalføringOgDistribueringsResultat {
            return this.fold(
                ifLeft = {
                    when (it) {
                        KunneIkkeBestilleBrevForDokument.ForTidligÅPrøvePåNytt -> log.info("Kunne ikke distribuere ${distribusjon.id} fordi det er for tidlig å prøve på nytt for dokument ${distribusjon.dokument.id} for sak ${distribusjon.dokument.metadata.sakId}")
                        KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev,
                        KunneIkkeBestilleBrevForDokument.MåJournalføresFørst,
                        -> log.error(
                            "Kunne ikke distribuere ${distribusjon.id}. Feilen var $it for dokument ${distribusjon.dokument.id} for sak ${distribusjon.dokument.metadata.sakId}",
                            RuntimeException("Genererer en stacktrace for enklere debugging."),
                        )
                    }
                    Feil(
                        distribusjon.id,
                        distribusjon.journalføringOgBrevdistribusjon.journalpostId(),
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

fun List<JournalføringOgDistribueringsResultat>.logResultat(logContext: String, log: Logger) {
    this.ifNotEmpty {
        val ok = this.ok()
        val feil = this.feil()
        if (feil.second.isEmpty()) {
            log.info(
                """
                $logContext ferdig:
                Distribueringer som gikk ok: $ok
                Distribueringer som er blitt ignorert: ${feil.first}
                """.trimIndent(),
            )
        } else {
            log.error(
                """
                    $logContext feilet:
                    Distribueringer som feilet: ${feil.second}
                    Distribueringer som gikk ok: $ok
                    Distribueringer som er blitt ignorert: ${feil.first}
                """.trimIndent(),
            )
        }
    }
}

fun List<JournalføringOgDistribueringsResultat>.ok(): List<UUID> =
    this.filterIsInstance<JournalføringOgDistribueringsResultat.Ok>().map { it.id }

/**
 * denne brukes kun i context for logging
 *
 * @return Pair<Feil som er for tidlig å prøve på nytt, faktisk feil>
 */
private fun List<JournalføringOgDistribueringsResultat>.feil(): Pair<List<UUID>, List<UUID>> =
    this.filterIsInstance<JournalføringOgDistribueringsResultat.Feil>().partition {
        it.originalFeil is JournalføringOgDistribueringsFeil.Distribuering && it.originalFeil.originalFeil is KunneIkkeBestilleBrevForDokument.ForTidligÅPrøvePåNytt
    }.let {
        it.first.map { it.id } to it.second.map { it.id }
    }
