package dokument.domain.hendelser

import dokument.domain.DokumentMedMetadataUtenFil
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * Hendelse som representerer et dokument som er generert for en sak.
 * Eksempel på et slik dokument kan være et informasjonsbrev, vedtaksbrev eller generell jorunalføring av grunnlagsdata som [no.nav.su.se.bakover.domain.skatt.Skattedokument]
 * Alle slike hendelser vil føre til en asynkron [JournalførtDokumentHendelse]
 * Merk at [DokumentMedMetadataUtenFil] inneholder informasjon om distribusjon, selvom det avhenger av [skalSendeBrev]
 *
 * @param skalSendeBrev Avhengig av behandlingstypen og dokumenttypen. Vi har foreløpig bare 2 tilfeller der vi ikke sender brev, det er journalføring av søknaden og skattegrunnlag.
 */
data class GenerertDokumentHendelse(
    override val hendelseId: HendelseId,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val sakId: UUID,
    override val relatertHendelse: HendelseId,
    val dokumentUtenFil: DokumentMedMetadataUtenFil,
    val skalSendeBrev: Boolean,
) : DokumentHendelse {

    // Vi har ingen mulighet for å korrigere/annullere denne hendelsen.
    override val tidligereHendelseId: HendelseId? = null

    override val entitetId: UUID = sakId

    val dokumentId = dokumentUtenFil.id

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
            dokument: DokumentMedMetadataUtenFil,
            skalSendeBrev: Boolean,
        ): GenerertDokumentHendelse {
            return GenerertDokumentHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                sakId = sakId,
                versjon = versjon,
                relatertHendelse = relatertHendelse,
                dokumentUtenFil = dokument,
                skalSendeBrev = skalSendeBrev,
            ).also {
                require(it.entitetId == entitetId) {
                    "Persistert entitetId $entitetId var ulik den utleda fra domenet ${it.entitetId}"
                }
            }
        }
    }
}
