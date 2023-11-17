@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import arrow.core.NonEmptyList
import dokument.domain.DokumentMedMetadataUtenFil
import dokument.domain.hendelser.GenerertDokumentHendelse
import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.avbrutt.AvbruttTilbakekrevingsbehandlingDokumentCommand
import java.time.Clock

data class AvbruttTilbakekrevingsbehandling(
    val forrigeSteg: KanEndres,
    override val avsluttetTidspunkt: Tidspunkt,
    override val avsluttetAv: NavIdentBruker,
    val begrunnelse: String,
) : Tilbakekrevingsbehandling by forrigeSteg, Avbrutt {

    override fun erÅpen(): Boolean = false

    fun lagDokumentHendelse(
        command: AvbruttTilbakekrevingsbehandlingDokumentCommand,
        dokumentMedMetadataUtenFil: DokumentMedMetadataUtenFil,
        nesteVersjon: Hendelsesversjon,
        relaterteHendelser: NonEmptyList<HendelseId>,
        clock: Clock,
    ): GenerertDokumentHendelse = GenerertDokumentHendelse(
        hendelseId = HendelseId.generer(),
        hendelsestidspunkt = Tidspunkt.now(clock),
        versjon = nesteVersjon,
        meta = command.toDefaultHendelsesMetadata(),
        sakId = command.sakId,
        relaterteHendelser = relaterteHendelser,
        dokumentUtenFil = dokumentMedMetadataUtenFil,
        skalSendeBrev = true,
    )
}
