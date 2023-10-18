@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import arrow.core.NonEmptyList
import dokument.domain.DokumentMedMetadataUtenFil
import dokument.domain.LagretDokumentHendelse
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import java.time.Clock
import java.util.UUID

sealed interface KanForhåndsvarsle : KanEndres {

    fun leggTilForhåndsvarselDokumentId(
        hendelseId: HendelseId,
        dokumentId: UUID,
    ): UnderBehandling

    override fun erÅpen() = true

    /**
     *
     * Denne lagrer nye dokumenter på det som er forhåndsvarsel. Dersom det skal være andre brev, må noe endres
     */
    fun nyLagretDokumentHendelse(
        command: SakshendelseCommand,
        dokumentMedMetadataUtenFil: DokumentMedMetadataUtenFil,
        nesteVersjon: Hendelsesversjon,
        relaterteHendelser: NonEmptyList<HendelseId>,
        clock: Clock,
    ): LagretDokumentHendelse {
        return LagretDokumentHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            meta = command.toDefaultHendelsesMetadata(),
            sakId = command.sakId,
            relaterteHendelser = relaterteHendelser,
            dokumentUtenFil = dokumentMedMetadataUtenFil,
        )
    }
}
