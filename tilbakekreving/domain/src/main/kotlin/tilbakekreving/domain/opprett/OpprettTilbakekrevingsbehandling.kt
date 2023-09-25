package tilbakekreving.domain.opprett

import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.time.Clock

fun opprettTilbakekrevingsbehandling(
    command: OpprettTilbakekrevingsbehandlingCommand,
    forrigeVersjon: Hendelsesversjon,
    clock: Clock,
    kravgrunnlag: Kravgrunnlag,
): Pair<OpprettetTilbakekrevingsbehandlingHendelse, OpprettetTilbakekrevingsbehandling> {
    return OpprettetTilbakekrevingsbehandlingHendelse.opprett(
        sakId = command.sakId,
        opprettetAv = command.opprettetAv,
        meta = HendelseMetadata(
            correlationId = command.correlationId,
            ident = command.opprettetAv,
            brukerroller = command.brukerroller,
        ),
        versjon = forrigeVersjon.inc(),
        clock = clock,
        kravgrunnlagsId = kravgrunnlag.kravgrunnlagId,
    ).let {
        it to it.toDomain(kravgrunnlag)
    }
}
