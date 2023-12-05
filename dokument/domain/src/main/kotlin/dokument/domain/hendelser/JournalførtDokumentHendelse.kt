package dokument.domain.hendelser

import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * Hendelse som representerer at et dokument har blitt journalført (hos Joark) og skal muligens sendes ut til bruker avhengig av hva [skalSendeBrev] er satt til.
 *
 * @param skalSendeBrev vil være utledet av brevvalget under behandlingen og skal alltid samsvare med dette.
 */
data class JournalførtDokumentHendelse(
    override val hendelseId: HendelseId,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val sakId: UUID,
    override val relatertHendelse: HendelseId,
    val journalpostId: JournalpostId,
    val skalSendeBrev: Boolean,
) : DokumentHendelse {
    override val entitetId: UUID = sakId

    override val tidligereHendelseId: HendelseId? = null

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun fraPersistert(
            hendelseId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            entitetId: UUID,
            versjon: Hendelsesversjon,
            sakId: UUID,
            relatertHendelse: HendelseId,
            journalpostId: JournalpostId,
            skalSendeBrev: Boolean,
        ): JournalførtDokumentHendelse {
            return JournalførtDokumentHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                sakId = sakId,
                versjon = versjon,
                relatertHendelse = relatertHendelse,
                journalpostId = journalpostId,
                skalSendeBrev = skalSendeBrev,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId. "
                }
            }
        }
    }
}
