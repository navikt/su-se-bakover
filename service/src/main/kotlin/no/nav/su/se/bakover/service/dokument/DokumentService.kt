package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import no.nav.su.se.bakover.service.dokument.DokumentResultatSet.Companion.feil
import no.nav.su.se.bakover.service.dokument.DokumentResultatSet.Companion.ok
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.Logger
import java.util.UUID

interface DokumentService {
    fun journalførDokumenter()
    fun distribuer()
}

sealed interface DokumentResultatSet {
    val id: UUID

    @JvmInline
    value class Ok(override val id: UUID) : DokumentResultatSet

    @JvmInline
    value class Feil(override val id: UUID) : DokumentResultatSet

    companion object {
        fun List<Either<Feil, Ok>>.ok() = this.filterIsInstance<Either.Right<Ok>>().map { it.value.id }
        fun List<Either<Feil, Ok>>.feil() = this.filterIsInstance<Either.Left<Feil>>().map { it.value.id }
        fun List<Either<Feil, Ok>>.logResultat(log: Logger) = this.ifNotEmpty {
            val ok = this.ok()
            val feil = this.feil()
            if (feil.isEmpty()) {
                log.info("Journalførte/distribuerte: $ok")
            } else {
                log.error("Kunne ikke journalføre/distribuere: $feil. Disse gikk ok: $ok")
            }
        }
    }
}
