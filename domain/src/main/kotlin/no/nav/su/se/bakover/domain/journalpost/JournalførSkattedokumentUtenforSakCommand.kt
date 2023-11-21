package no.nav.su.se.bakover.domain.journalpost

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.journalføring.søknad.JournalførCommand
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import org.jetbrains.annotations.TestOnly

data class JournalførSkattedokumentUtenforSakCommand private constructor(
    val fnr: Fnr,
    val sakstype: Sakstype,
    /**
     * i contexten av skatt, er det mulig at fagsystemId'en er en sak vi har i systemet. Må sees sammen med sakstype.
     */
    val fagsystemId: String,
    val dokument: Dokument.UtenMetadata,
) : JournalførCommand {

    override val internDokumentId = dokument.id

    companion object {
        @TestOnly
        fun create(
            fnr: Fnr,
            sakstype: Sakstype,
            fagsystemId: String,
            dokument: Dokument.UtenMetadata,
        ): JournalførSkattedokumentUtenforSakCommand {
            return tryCreate(fnr, sakstype, fagsystemId, dokument).getOrElse {
                throw IllegalArgumentException("Valideringsfeil i JournalpostSkattUtenforSak - ")
            }
        }

        fun tryCreate(
            fnr: Fnr,
            sakstype: Sakstype,
            fagsystemId: String,
            dokument: Dokument.UtenMetadata,
        ): Either<KunneIkkeLageJournalpostUtenforSak, JournalførSkattedokumentUtenforSakCommand> {
            if (fagsystemId.isBlank()) {
                return KunneIkkeLageJournalpostUtenforSak.FagsystemIdErTom.left()
            }
            return JournalførSkattedokumentUtenforSakCommand(
                fnr,
                sakstype,
                fagsystemId,
                dokument,
            ).right()
        }
    }
}
