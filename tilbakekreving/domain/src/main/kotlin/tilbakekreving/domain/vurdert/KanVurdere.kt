@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.vurdert.Månedsvurderinger
import tilbakekreving.domain.vurdert.OppdaterMånedsvurderingerCommand
import java.time.Clock

sealed interface KanVurdere : Tilbakekrevingsbehandling {
    fun leggTilVurdering(
        command: OppdaterMånedsvurderingerCommand,
        tidligereHendelsesId: HendelseId,
        nesteVersjon: Hendelsesversjon,
        clock: Clock,
    ): Pair<MånedsvurderingerTilbakekrevingsbehandlingHendelse, VurdertTilbakekrevingsbehandling> {
        val hendelse = MånedsvurderingerTilbakekrevingsbehandlingHendelse(
            hendelseId = HendelseId.generer(),
            sakId = command.sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            tidligereHendelseId = tidligereHendelsesId,
            meta = DefaultHendelseMetadata(
                correlationId = command.correlationId,
                ident = command.utførtAv,
                brukerroller = command.brukerroller,
            ),
            id = command.behandlingsId,
            utførtAv = command.utførtAv,
            vurderinger = Månedsvurderinger(command.vurderinger.toNonEmptyList()),
        )
        return hendelse to this.applyHendelse(hendelse)
    }

    fun leggTilBrevtekst(): VurdertTilbakekrevingsbehandling.Utfylt
}
