package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.Brevdata
import java.util.UUID

object JournalpostFactory {
    fun lagJournalpost(
        person: Person,
        sakId: UUID,
        brevdata: Brevdata,
        pdf: ByteArray
    ): Journalpost {
        return when (brevdata.brevtype()) {
            BrevTemplate.InnvilgetVedtak -> Journalpost.Vedtakspost(person, sakId.toString(), brevdata, pdf)
            BrevTemplate.AvslagsVedtak -> Journalpost.Vedtakspost(person, sakId.toString(), brevdata, pdf)
            BrevTemplate.TrukketSøknad -> Journalpost.Info(person, sakId.toString(), brevdata, pdf)
            BrevTemplate.AvvistSøknadVedtak -> Journalpost.Vedtakspost(person, sakId.toString(), brevdata, pdf)
            BrevTemplate.AvvistSøknadFritekst -> Journalpost.Info(person, sakId.toString(), brevdata, pdf)
        }
    }
}
