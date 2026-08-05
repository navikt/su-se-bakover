package dokument.domain.journalføring.kontrollnotat

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

data class JournalførKontrollnotatCommand(
    val sakstype: Sakstype,
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val kontrollsamtaleNotatId: UUID,
    val tittel: String,
    val kontrollnotatJson: String,
    val kontrollnotatPdf: PdfA,
    val datoDokument: Tidspunkt,
)
