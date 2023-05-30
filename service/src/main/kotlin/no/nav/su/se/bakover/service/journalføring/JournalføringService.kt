package no.nav.su.se.bakover.service.journalføring

import arrow.core.Either
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
        journalførSkattDokumentService.journalfør()
    }
}

sealed interface JournalføringOgDistribueringsResultat {
    val id: UUID

    @JvmInline
    value class Ok(override val id: UUID) : JournalføringOgDistribueringsResultat

    @JvmInline
    value class Feil(override val id: UUID) : JournalføringOgDistribueringsResultat

    companion object {
        fun List<Either<Feil, Ok>>.ok() = this.filterIsInstance<Either.Right<Ok>>().map { it.value.id }
        fun List<Either<Feil, Ok>>.feil() = this.filterIsInstance<Either.Left<Feil>>().map { it.value.id }
        fun List<Either<Feil, Ok>>.logResultat(logContext: String, log: Logger) = this.ifNotEmpty {
            val ok = this.ok()
            val feil = this.feil()
            if (feil.isEmpty()) {
                log.info("$logContext $ok")
            } else {
                log.error("$logContext feilet: $feil. Disse gikk ok: $ok")
            }
        }
    }
}
