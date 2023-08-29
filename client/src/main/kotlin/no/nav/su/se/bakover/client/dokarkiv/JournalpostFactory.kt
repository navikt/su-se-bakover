package no.nav.su.se.bakover.client.dokarkiv

import dokument.domain.Dokument
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Sakstype

data object JournalpostFactory {
    fun lagJournalpost(
        person: Person,
        saksnummer: Saksnummer,
        dokument: Dokument.MedMetadata,
        sakstype: Sakstype,
    ): Journalpost = when (dokument) {
        is Dokument.MedMetadata.Informasjon,
        -> JournalpostForSak.Info.from(
            person = person,
            saksnummer = saksnummer,
            dokument = dokument,
            sakstype = sakstype,
        )

        is Dokument.MedMetadata.Vedtak,
        -> JournalpostForSak.Vedtakspost.from(
            person = person,
            saksnummer = saksnummer,
            dokument = dokument,
            sakstype = sakstype,
        )
    }
}
