package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import java.util.UUID

object JournalpostFactory {
    fun lagJournalpost(
        person: Person,
        sakId: UUID,
        brevInnhold: BrevInnhold,
        pdf: ByteArray
    ): Journalpost {
        return when (brevInnhold.brevTemplate) {
            BrevTemplate.InnvilgetVedtak -> Journalpost.Vedtakspost(person, sakId.toString(), brevInnhold, pdf)
            BrevTemplate.AvslagsVedtak -> Journalpost.Vedtakspost(person, sakId.toString(), brevInnhold, pdf)
            BrevTemplate.TrukketSøknad -> Journalpost.Info(person, sakId.toString(), brevInnhold, pdf)
            BrevTemplate.AvvistSøknadVedtak -> Journalpost.Vedtakspost(person, sakId.toString(), brevInnhold, pdf)
            BrevTemplate.AvvistSøknadFritekst -> Journalpost.Info(person, sakId.toString(), brevInnhold, pdf)
        }
    }
}
