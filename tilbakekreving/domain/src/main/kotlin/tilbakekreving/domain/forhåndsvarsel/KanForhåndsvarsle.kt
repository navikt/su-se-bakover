@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.DokumentMedMetadataUtenFil
import dokument.domain.hendelser.GenerertDokumentHendelse
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import java.time.Clock
import java.util.UUID

sealed interface KanForhåndsvarsle : KanEndres {

    fun leggTilForhåndsvarselDokumentId(
        dokumentId: UUID,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
        hendelsesTidspunkt: Tidspunkt,
    ): UnderBehandling

    override fun erÅpen() = true

    fun lagDokumenthendelseForForhåndsvarsel(
        command: SakshendelseCommand,
        dokumentMedMetadataUtenFil: DokumentMedMetadataUtenFil,
        nesteVersjon: Hendelsesversjon,
        relaterteHendelse: HendelseId,
        clock: Clock,
    ): GenerertDokumentHendelse {
        return GenerertDokumentHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            sakId = command.sakId,
            relatertHendelse = relaterteHendelse,
            dokumentUtenFil = dokumentMedMetadataUtenFil,
            skalSendeBrev = true,
        )
    }
}
