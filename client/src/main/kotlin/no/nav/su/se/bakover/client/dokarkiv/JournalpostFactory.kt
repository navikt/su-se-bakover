package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknadstype
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.dokument.Dokument

object JournalpostFactory {
    fun lagJournalpost(
        person: Person,
        saksnummer: Saksnummer,
        brevInnhold: BrevInnhold,
        pdf: ByteArray,
        søknadstype: Søknadstype = Søknadstype.UFØRE
    ): Journalpost = when (brevInnhold.brevTemplate) {
        BrevTemplate.InnvilgetVedtak,
        BrevTemplate.AvslagsVedtak,
        BrevTemplate.AvvistSøknadVedtak,
        BrevTemplate.Opphør.Opphørsvedtak,
        BrevTemplate.Opphør.OpphørMedTilbakekreving,
        BrevTemplate.Revurdering.Inntekt,
        BrevTemplate.Revurdering.MedTilbakekreving,
        BrevTemplate.VedtakIngenEndring,
        BrevTemplate.Klage.Avvist,
        -> Journalpost.Vedtakspost.from(
            person = person, saksnummer = saksnummer,
            brevInnhold = brevInnhold, pdf = pdf, søknadstype = søknadstype
        )
        BrevTemplate.TrukketSøknad,
        BrevTemplate.AvvistSøknadFritekst,
        BrevTemplate.Forhåndsvarsel,
        BrevTemplate.ForhåndsvarselTilbakekreving,
        BrevTemplate.Revurdering.AvsluttRevurdering,
        BrevTemplate.InnkallingTilKontrollsamtale,
        BrevTemplate.Klage.Oppretthold,
        BrevTemplate.PåminnelseNyStønadsperiode,
        -> Journalpost.Info.from(person, saksnummer, brevInnhold, pdf, søknadstype = søknadstype)
    }

    fun lagJournalpost(
        person: Person,
        saksnummer: Saksnummer,
        dokument: Dokument.MedMetadata,
        søknadstype: Søknadstype = Søknadstype.UFØRE,
    ): Journalpost = when (dokument) {
        is Dokument.MedMetadata.Informasjon,
        -> Journalpost.Info.from(
            person = person,
            saksnummer = saksnummer,
            dokument = dokument,
            søknadstype = søknadstype
        )
        is Dokument.MedMetadata.Vedtak,
        -> Journalpost.Vedtakspost.from(
            person = person,
            saksnummer = saksnummer,
            dokument = dokument,
            søknadstype = søknadstype
        )
    }
}
