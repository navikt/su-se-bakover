package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.Skattedokument

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
            sakstype = sakstype,
        )

        is Dokument.MedMetadata.Vedtak,
        -> Journalpost.Vedtakspost.from(
            person = person,
            saksnummer = saksnummer,
            dokument = dokument,
            sakstype = sakstype,
        )
    }

    fun lagJournalpost(
        person: Person,
        sakInfo: SakInfo,
        skattedokument: Skattedokument
    ): Journalpost {
        /*
        TODO
        er journalførende enhet korrekt?
        NAV-enheten som har journalført forsendelsen.
        Dersom forsoekFerdigstill=true skal enhet alltid settes. Dersom  det ikke er noen Nav-enhet involvert (f.eks. ved automatisk journalføring), skal enhet være '9999'.
        Dersom foersoekFerdigstill=false bør journalførendeEnhet kun settes dersom oppgavene skal rutes på en annen måte enn Norg-reglene tilsier. Hvis enhet er blank, havner oppgavene på enheten som ligger i Norg-regelsettet.

         skattedokkument er heller ikke ment som et utgående-dokument
         mulig vi bruker notat - NOTAT brukes for dokumentasjon som NAV har produsert selv og uten mål om å distribuere dette ut av NAV. Eksempler på dette er forvaltningsnotater og referater fra telefonsamtaler med brukere.
         */
        return Journalpost.Info.from(
            sakInfo = sakInfo,
            person = person,
            tittel = skattedokument.dokumentTittel,
            pdf = skattedokument.generertDokument,
            originalDokumentJson = skattedokument.dokumentJson
        )
    }
}


fun Skattedokument.lagJournalpost(sakInfo: SakInfo, person: Person): Journalpost = JournalpostFactory.lagJournalpost(
    sakInfo = sakInfo, person = person, skattedokument = this
)
