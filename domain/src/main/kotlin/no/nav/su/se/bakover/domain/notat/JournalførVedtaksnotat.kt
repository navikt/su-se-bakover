package no.nav.su.se.bakover.domain.notat

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

interface JournalførVedtaksnotatClient {
    fun journalførVedtaksnotat(command: JournalførVedtaksnotatCommand): Either<ClientError, JournalpostId>
}

data class JournalførVedtaksnotatCommand(
    val sakstype: Sakstype,
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val notatId: UUID,
    val tittel: String,
    val notat: String,
    val attestantNotat: String,
    /**
     * Arkivvariant (PDF) av selve notat-teksten. Joark krever at hvert dokument har en variant av
     * typen ARKIV, så denne må være satt dersom [notat] eller [attestantNotat] har innhold.
     */
    val notatPdf: PdfA?,
    /**
     * Vedleggene ferdig konvertert til PDF. Bildefiler (PNG/JPEG) må konverteres til PDF før
     * journalføring fordi Joark ikke støtter lagring av bildefiler som arkivvariant.
     */
    val vedlegg: List<JournalførbartVedlegg>,
    val datoDokument: Tidspunkt,
)

/**
 * Et vedlegg som er klart for journalføring, dvs. allerede konvertert til PDF ([pdf]).
 */
data class JournalførbartVedlegg(
    val filnavn: String,
    val pdf: PdfA,
)
