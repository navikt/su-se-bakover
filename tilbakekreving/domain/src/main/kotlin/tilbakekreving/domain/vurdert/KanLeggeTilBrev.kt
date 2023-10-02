@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.vurdert.Månedsvurderinger
import tilbakekreving.domain.vurdert.OppdaterBrevtekstCommand
import java.time.Clock

sealed interface KanLeggeTilBrev : KanVurdere {
    override val månedsvurderinger: Månedsvurderinger
    override val brevvalg: Brevvalg.SaksbehandlersValg?

    fun leggTilBrevtekst(
        command: OppdaterBrevtekstCommand,
        tidligereHendelsesId: HendelseId,
        nesteVersjon: Hendelsesversjon,
        clock: Clock,
    ): Pair<BrevTilbakekrevingsbehandlingHendelse, VurdertTilbakekrevingsbehandling.Utfylt> {
        val hendelse = BrevTilbakekrevingsbehandlingHendelse(
            hendelseId = HendelseId.generer(),
            sakId = command.sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            meta = HendelseMetadata(
                correlationId = command.correlationId,
                ident = command.utførtAv,
                brukerroller = command.brukerroller,
            ),
            tidligereHendelseId = tidligereHendelsesId,
            id = command.behandlingId,
            utførtAv = command.utførtAv,
            brevvalg = command.brevvalg,
        )

        return hendelse to this.applyHendelse(hendelse)
    }
}
