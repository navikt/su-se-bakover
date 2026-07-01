package no.nav.su.se.bakover.domain.notat

import arrow.core.Either
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
    val vedlegg: List<NotatVedlegg>,
    val datoDokument: Tidspunkt,
)
