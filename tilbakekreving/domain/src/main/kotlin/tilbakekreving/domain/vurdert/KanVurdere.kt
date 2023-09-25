package tilbakekreving.domain.vurdert

import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.time.Clock

interface KanVurdere : Tilbakekrevingsbehandling {
    fun leggTilVurdering(
        command: LeggTilVurderingerCommand,
        behandlingsId: TilbakekrevingsbehandlingId,
        tidligereHendelsesId: HendelseId,
        nesteVersjon: Hendelsesversjon,
        clock: Clock,
    ): MånedsvurderingerTilbakekrevingsbehandlingHendelse {
        return MånedsvurderingerTilbakekrevingsbehandlingHendelse(
            hendelseId = HendelseId.generer(),
            sakId = command.sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            tidligereHendelseId = tidligereHendelsesId,
            meta = HendelseMetadata(
                correlationId = command.correlationId,
                ident = command.utførtAv,
                brukerroller = command.brukerroller,
            ),
            id = behandlingsId,
            utførtAv = command.utførtAv,
            vurderinger = Månedsvurderinger(command.vurderinger.toNonEmptyList()),
        )
    }

    fun leggTilBrevtekst(): VurdertTilbakekrevingsbehandling.Utfylt
}
