package dokument.domain.hendelser

import arrow.core.NonEmptyList
import dokument.domain.DokumentMedMetadataUtenFil
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * Denne hendelsen er ment for bruk dersom du har et dokument som kun behov for journalføring
 * Eksempel på et slik dokument kan være [Skattedokument]
 */
data class LagretDokumentForJournalføringHendelse(
    override val hendelseId: HendelseId,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: DefaultHendelseMetadata,
    override val sakId: UUID,
    override val relaterteHendelser: NonEmptyList<HendelseId>,
    override val dokumentUtenFil: DokumentMedMetadataUtenFil,
) : DokumentHendelse {

    // Vi har ingen mulighet for å korrigere/annullere denne hendelsen atm.
    override val tidligereHendelseId: HendelseId? = null

    override val entitetId: UUID = sakId

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun fraPersistert(
            hendelseId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            hendelseMetadata: DefaultHendelseMetadata,
            entitetId: UUID,
            versjon: Hendelsesversjon,
            sakId: UUID,
            relaterteHendelser: List<HendelseId>,
            dokument: DokumentMedMetadataUtenFil,
        ): LagretDokumentForJournalføringHendelse {
            return LagretDokumentForJournalføringHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = hendelseMetadata,
                sakId = sakId,
                versjon = versjon,
                relaterteHendelser = relaterteHendelser.toNonEmptyList(),
                dokumentUtenFil = dokument,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId. "
                }
            }
        }
    }
}
