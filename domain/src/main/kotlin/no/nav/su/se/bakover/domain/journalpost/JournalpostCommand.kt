package no.nav.su.se.bakover.domain.journalpost

import dokument.domain.Dokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold

sealed interface JournalpostCommand {
    val sakstype: Sakstype
    val fnr: Fnr
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
    ) : JournalpostForSakCommand

    data class Brev(
        override val fnr: Fnr,
        override val saksnummer: Saksnummer,
        val dokument: Dokument.MedMetadata,
        override val sakstype: Sakstype,
        val navn: Person.Navn,
    ) : JournalpostForSakCommand
}
