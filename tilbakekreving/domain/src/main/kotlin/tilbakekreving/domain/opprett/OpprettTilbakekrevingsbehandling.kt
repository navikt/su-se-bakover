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
        // TODO jah: Denne bør byttes med en intern id, men da må vi migrere eksisterende kravgrunnlag knyttet til sak i produksjon.
        eksternKravgrunnlagId = kravgrunnlag.eksternKravgrunnlagId,
    ).let {
        it to it.toDomain(kravgrunnlag)
    }
}
