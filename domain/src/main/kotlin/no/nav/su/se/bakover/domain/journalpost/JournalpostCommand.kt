package no.nav.su.se.bakover.domain.journalpost

import dokument.domain.Dokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import person.domain.Person
import java.util.UUID

/**
 * @property internDokumentId SU-app sin ID. Brukes for dedup og sporing gjennom verdikjeden.
 */
sealed interface JournalpostCommand {
    val sakstype: Sakstype
    val fnr: Fnr
    val internDokumentId: UUID
}

sealed interface JournalpostUtenforSakCommand : JournalpostCommand {
    val fagsystemId: String
}

sealed interface JournalpostForSakCommand : JournalpostCommand {
    val saksnummer: Saksnummer

    data class Søknadspost(
        override val saksnummer: Saksnummer,
        override val sakstype: Sakstype,
        // TODO - Bør erstatte søknadinnhold, pdf & datoDokument med [Dokument.MedMetadata]
        val søknadInnhold: SøknadInnhold,
        val pdf: PdfA,
        val datoDokument: Tidspunkt,
        override val fnr: Fnr,
        val navn: Person.Navn,
        override val internDokumentId: UUID,
    ) : JournalpostForSakCommand

    data class Brev(
        override val fnr: Fnr,
        override val saksnummer: Saksnummer,
        val dokument: Dokument.MedMetadata,
        override val sakstype: Sakstype,
        val navn: Person.Navn,
    ) : JournalpostForSakCommand {
        override val internDokumentId = dokument.id
    }
}
