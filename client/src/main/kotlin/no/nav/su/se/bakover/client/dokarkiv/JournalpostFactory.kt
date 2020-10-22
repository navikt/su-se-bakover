package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.Brevinnhold
import no.nav.su.se.bakover.domain.brev.PdfTemplate

object JournalpostFactory {
    fun lagJournalpost(
        person: Person,
        sakId: String,
        brevinnhold: Brevinnhold,
        pdf: ByteArray,
    ): Journalpost {
        return when (brevinnhold.pdfTemplate()) {
            PdfTemplate.VedtakAvslag -> Journalpost.Vedtakspost(
                person = person,
                sakId = sakId,
                brevinnhold = brevinnhold,
                pdf = pdf
            )
            PdfTemplate.VedtakInnvilget -> Journalpost.Vedtakspost(
                person = person,
                sakId = sakId,
                brevinnhold = brevinnhold,
                pdf = pdf
            )
            PdfTemplate.TrukketSøknad -> Journalpost.LukkSøknad(
                person = person,
                sakId = sakId,
                brevinnhold = brevinnhold,
                pdf = pdf
            )
            PdfTemplate.Søknad -> Journalpost.Søknadspost(
                person = person,
                sakId = sakId,
                brevinnhold = brevinnhold,
                pdf = pdf
            )
        }
    }
}
