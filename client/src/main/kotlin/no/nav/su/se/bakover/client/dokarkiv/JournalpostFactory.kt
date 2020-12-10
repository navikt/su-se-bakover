package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate

object JournalpostFactory {
    fun lagJournalpost(
        person: Person,
        saksnummer: Saksnummer,
        brevInnhold: BrevInnhold,
        pdf: ByteArray
    ): Journalpost {
        return when (brevInnhold.brevTemplate) {
            BrevTemplate.InnvilgetVedtak -> Journalpost.Vedtakspost(person, saksnummer, brevInnhold, pdf)
            BrevTemplate.AvslagsVedtak -> Journalpost.Vedtakspost(person, saksnummer, brevInnhold, pdf)
            BrevTemplate.TrukketSøknad -> Journalpost.Info(person, saksnummer, brevInnhold, pdf)
            BrevTemplate.AvvistSøknadVedtak -> Journalpost.Vedtakspost(person, saksnummer, brevInnhold, pdf)
            BrevTemplate.AvvistSøknadFritekst -> Journalpost.Info(person, saksnummer, brevInnhold, pdf)
        }
    }
}
