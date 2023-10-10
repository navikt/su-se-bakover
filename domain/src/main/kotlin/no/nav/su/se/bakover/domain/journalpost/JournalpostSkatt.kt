package no.nav.su.se.bakover.domain.journalpost

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import org.jetbrains.annotations.TestOnly
import java.lang.IllegalArgumentException

/**
 * kan brukes som mal for 'Notat' poster i Joark.
 */
data class JournalpostSkattForSak(
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    override val fnr: Fnr,
    val dokument: Skattedokument,
) : JournalpostForSakCommand {

    companion object {
        fun Skattedokument.lagJournalpost(sakInfo: SakInfo): JournalpostSkattForSak = JournalpostSkattForSak(
            saksnummer = sakInfo.saksnummer,
            sakstype = sakInfo.type,
            dokument = this,
            fnr = sakInfo.fnr,
        )
    }
}

data class JournalpostSkattUtenforSak private constructor(
    override val fnr: Fnr,
    override val sakstype: Sakstype,
    /**
     * i contexten av skatt, er det mulig at fagsystemId'en er en sak vi har i systemet. Må sees sammen med sakstype.
     */
    override val fagsystemId: String,
    val dokument: Dokument.UtenMetadata,
) : JournalpostUtenforSakCommand {

    companion object {
        @TestOnly
        fun create(
            fnr: Fnr,
            sakstype: Sakstype,
            fagsystemId: String,
            dokument: Dokument.UtenMetadata,
        ): JournalpostSkattUtenforSak {
            return tryCreate(fnr, sakstype, fagsystemId, dokument).getOrElse {
                throw IllegalArgumentException("Valideringsfeil i JournalpostSkattUtenforSak - ")
            }
        }

        fun tryCreate(
            fnr: Fnr,
            sakstype: Sakstype,
            fagsystemId: String,
            dokument: Dokument.UtenMetadata,
        ): Either<KunneIkkeLageJournalpostUtenforSak, JournalpostSkattUtenforSak> {
            if (fagsystemId.isBlank()) {
                return KunneIkkeLageJournalpostUtenforSak.FagsystemIdErTom.left()
            }

            return JournalpostSkattUtenforSak(
                fnr,
                sakstype,
                fagsystemId,
                dokument,
            ).right()
        }
    }
}
