package tilbakekreving.domain.opprettelse

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingUtenKravgrunnlag
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.time.Clock

fun opprettTilbakekrevingsbehandling(
    command: OpprettTilbakekrevingsbehandlingCommand,
    forrigeVersjon: Hendelsesversjon,
    clock: Clock,
    fnr: Fnr,
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
        it to it.toDomain(fnr, kravgrunnlag, erKravgrunnlagUtdatert)
    }
}

fun opprettTilbakekrevingsbehandlingUtenKravgrunnlag(
    command: OpprettTilbakekrevingsbehandlingCommand,
    forrigeVersjon: Hendelsesversjon,
    clock: Clock,
    fnr: Fnr,
    saksnummer: Saksnummer,
): Pair<OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse, OpprettetTilbakekrevingsbehandlingUtenKravgrunnlag> {
    return OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse.opprett(
        sakId = command.sakId,
        opprettetAv = command.opprettetAv,
        versjon = forrigeVersjon.inc(),
        clock = clock,
    ).let {
        it to it.toDomain(fnr, saksnummer)
    }
}
