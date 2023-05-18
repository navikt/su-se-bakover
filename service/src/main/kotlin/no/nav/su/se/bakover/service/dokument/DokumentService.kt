package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import java.util.UUID

interface DokumentService {
    fun journalførDokumenter()
    fun distribuer()
}


//Her kunne vi kanskje bare slått sammen
sealed interface JournalføringsResultat {
    val id: UUID

    data class Ok(override val id: UUID) : JournalføringsResultat
    data class Feil(override val id: UUID) : JournalføringsResultat

    companion object {
        fun List<Either<Feil, Ok>>.ok() = this.filterIsInstance<Either.Right<Ok>>().map { it.value.id.toString() }
        fun List<Either<Feil, Ok>>.feil() = this.filterIsInstance<Either.Left<Feil>>().map { it.value }
    }
}

sealed interface DistribueringsResultat {
    val id: UUID

    data class Ok(override val id: UUID) : JournalføringsResultat
    data class Feil(override val id: UUID) : JournalføringsResultat

    companion object {
        fun List<Either<Feil, Ok>>.ok() = this.filterIsInstance<Either.Right<Ok>>().map { it.value.id.toString() }
        fun List<Either<Feil, Ok>>.feil() = this.filterIsInstance<Either.Left<Feil>>().map { it.value }
    }
}
