package no.nav.su.se.bakover.domain.journalpost

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.Skattedokument

/**
 * kan brukes som mal for 'Notat' poster i Joark.
 */
data class JournalpostSkatt(
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    override val fnr: Fnr,
    val dokument: Skattedokument,
) : JournalpostForSakCommand {

    companion object {
        fun Skattedokument.lagJournalpost(sakInfo: SakInfo): JournalpostSkatt = JournalpostSkatt(
            saksnummer = sakInfo.saksnummer,
            sakstype = sakInfo.type,
            dokument = this,
            fnr = sakInfo.fnr,
        )
    }
}
