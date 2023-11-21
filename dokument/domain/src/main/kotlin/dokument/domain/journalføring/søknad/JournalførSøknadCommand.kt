package dokument.domain.journalføring.søknad

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import person.domain.Person
import java.util.UUID

/**
 * Merk at søknadsPDFer ikke lagres i SU-app sin database, i motsetning til brev og dokumenter.
 */
data class JournalførSøknadCommand(
    val saksnummer: Saksnummer,
    val sakstype: Sakstype,
    val søknadInnholdJson: String,
    val pdf: PdfA,
    val datoDokument: Tidspunkt,
    val fnr: Fnr,
    val navn: Person.Navn,
    val internDokumentId: UUID,
)
