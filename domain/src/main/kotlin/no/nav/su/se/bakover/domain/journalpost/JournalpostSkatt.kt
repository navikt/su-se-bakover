package no.nav.su.se.bakover.domain.journalpost

import dokument.domain.Dokument
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.Skattedokument

/**
 * kan brukes som mal for 'Notat' poster i Joark.
 */
data class JournalpostSkattForSak(
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    override val fnr: Fnr,
    val dokument: Skattedokument,
) : JournalpostForSakCommand {

    companion object {
        fun Skattedokument.lagJournalpost(sakInfo: SakInfo): JournalpostSkattForSak = JournalpostSkattForSak(
            saksnummer = sakInfo.saksnummer,
            sakstype = sakInfo.type,
            dokument = this,
            fnr = sakInfo.fnr,
        )
    }
}

data class JournalpostSkattUtenforSak(
    override val fnr: Fnr,
    override val sakstype: Sakstype,
    /**
     * i contexten av skatt, er det mulig at fagsystemId'en er en sak vi har i systemet. Må sees sammen med sakstype.
     */
    override val fagsystemId: String,
    val dokument: Dokument.UtenMetadata,
) : JournalpostUtenforSakCommand
