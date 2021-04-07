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
        pdf: ByteArray,
    ): Journalpost = when (brevInnhold.brevTemplate) {
        BrevTemplate.InnvilgetVedtak,
        BrevTemplate.AvslagsVedtak,
        BrevTemplate.AvvistSøknadVedtak,
        BrevTemplate.Opphørsvedtak,
        BrevTemplate.Revurdering.Inntekt,
        BrevTemplate.VedtakIngenEndring,
        -> Journalpost.Vedtakspost(person, saksnummer, brevInnhold, pdf)
        BrevTemplate.TrukketSøknad,
        BrevTemplate.AvvistSøknadFritekst,
        -> Journalpost.Info(person, saksnummer, brevInnhold, pdf)
    }
}
