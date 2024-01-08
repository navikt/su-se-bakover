package tilbakekreving.domain.opprettelse

import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.time.Clock

fun opprettTilbakekrevingsbehandling(
    command: OpprettTilbakekrevingsbehandlingCommand,
    forrigeVersjon: Hendelsesversjon,
    clock: Clock,
    kravgrunnlag: Kravgrunnlag,
    erKravgrunnlagUtdatert: Boolean,
): Pair<OpprettetTilbakekrevingsbehandlingHendelse, OpprettetTilbakekrevingsbehandling> {
    return OpprettetTilbakekrevingsbehandlingHendelse.opprett(
        sakId = command.sakId,
        opprettetAv = command.opprettetAv,
        versjon = forrigeVersjon.inc(),
        clock = clock,
        kravgrunnlagPåSakHendelseId = kravgrunnlag.hendelseId,
    ).let {
        it to it.toDomain(kravgrunnlag, erKravgrunnlagUtdatert)
    }
}
