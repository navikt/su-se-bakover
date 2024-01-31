package vilkår.skatt.domain.journalpost

import dokument.domain.journalføring.søknad.JournalførCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import vilkår.skatt.domain.Skattedokument

/**
 * kan brukes som mal for 'Notat' poster i Joark.
 */
data class JournalførSkattedokumentPåSakCommand(
    val saksnummer: Saksnummer,
    val sakstype: Sakstype,
    val fnr: Fnr,
    val dokument: Skattedokument,
) : JournalførCommand {

    override val internDokumentId = dokument.id

    companion object {
        fun Skattedokument.lagJournalpost(
            sakInfo: SakInfo,
        ): JournalførSkattedokumentPåSakCommand {
            return JournalførSkattedokumentPåSakCommand(
                saksnummer = sakInfo.saksnummer,
                sakstype = sakInfo.type,
                dokument = this,
                fnr = sakInfo.fnr,
            )
        }
    }
}
