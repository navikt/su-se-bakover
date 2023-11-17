package dokument.domain.journalføring.brev

import dokument.domain.Dokument
import dokument.domain.journalføring.søknad.JournalførCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.Person

data class JournalførBrevCommand(
    val fnr: Fnr,
    val saksnummer: Saksnummer,
    val dokument: Dokument.MedMetadata,
    val sakstype: Sakstype,
    val navn: Person.Navn,
) : JournalførCommand {
    override val internDokumentId = dokument.id
}
