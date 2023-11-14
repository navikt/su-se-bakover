package tilbakekreving.domain.opprett

import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
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
        meta = DefaultHendelseMetadata(
            correlationId = command.correlationId,
            ident = command.opprettetAv,
            brukerroller = command.brukerroller,
        ),
        versjon = forrigeVersjon.inc(),
        clock = clock,
        kravgrunnlagPåSakHendelseId = kravgrunnlag.hendelseId,
    ).let {
        it to it.toDomain(kravgrunnlag, erKravgrunnlagUtdatert)
    }
}
