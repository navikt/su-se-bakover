package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.dokument.Dokument

object JournalpostFactory {
    fun lagJournalpost(
        person: Person,
        saksnummer: Saksnummer,
        dokument: Dokument.MedMetadata,
        sakstype: Sakstype,
    ): Journalpost = when (dokument) {
        is Dokument.MedMetadata.Informasjon,
        -> Journalpost.Info.from(
            person = person,
            saksnummer = saksnummer,
            dokument = dokument,
            sakstype = sakstype
        )
        is Dokument.MedMetadata.Vedtak,
        -> Journalpost.Vedtakspost.from(
            person = person,
            saksnummer = saksnummer,
            dokument = dokument,
            sakstype = sakstype
        )
    }
}
