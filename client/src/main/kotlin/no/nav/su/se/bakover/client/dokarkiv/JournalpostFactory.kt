package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.Brevdata
import no.nav.su.se.bakover.domain.brev.Brevtype
import java.util.UUID

object JournalpostFactory {
    fun lagJournalpost(
        person: Person,
        sakId: UUID,
        brevdata: Brevdata,
        pdf: ByteArray
    ): Journalpost {
        return when (brevdata.brevtype()) {
            Brevtype.InnvilgetVedtak -> Journalpost.Vedtakspost(person, sakId.toString(), brevdata, pdf)
            Brevtype.AvslagsVedtak -> Journalpost.Vedtakspost(person, sakId.toString(), brevdata, pdf)
        }
    }
}
