package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.behandling

import no.nav.su.se.bakover.domain.Sak
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.KanLeggeTilBrev
import tilbakekreving.domain.KanVurdere
import tilbakekreving.domain.MånedsvurderingerTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.leggTilBrevtekst
import tilbakekreving.domain.leggTilVurdering
import tilbakekreving.domain.vurdert.OppdaterBrevtekstCommand
import tilbakekreving.domain.vurdert.VurderCommand
import java.time.Clock

fun Sak.vurderTilbakekrevingsbehandling(
    command: VurderCommand,
    clock: Clock,
): Pair<MånedsvurderingerTilbakekrevingsbehandlingHendelse, UnderBehandling> {
    return (this.hentTilbakekrevingsbehandling(command.behandlingsId) as? KanVurdere)?.let { behandling ->
        behandling.leggTilVurdering(
            command = command,
            tidligereHendelsesId = behandling.hendelseId,
            nesteVersjon = this.versjon.inc(),
            clock = clock,
        )
    }
        ?: throw IllegalStateException("Tilbakekrevingsbehandling ${command.behandlingsId} enten fantes ikke eller var ikke i KanVurdere tilstanden. Sak id $id, saksnummer $saksnummer")
}

fun Sak.oppdaterVedtaksbrev(
    command: OppdaterBrevtekstCommand,
    clock: Clock,
): Pair<BrevTilbakekrevingsbehandlingHendelse, UnderBehandling.Utfylt> {
    return (this.hentTilbakekrevingsbehandling(command.behandlingId) as? KanLeggeTilBrev)?.let { behandling ->
        behandling.leggTilBrevtekst(
            command = command,
            tidligereHendelsesId = behandling.hendelseId,
            nesteVersjon = this.versjon.inc(),
            clock = clock,
        )
    }
        ?: throw IllegalStateException("Tilbakekrevingsbehandling ${command.behandlingId} enten fantes ikke eller var ikke i KanLeggeTilBrev tilstanden. Sak id $id, saksnummer $saksnummer")
}

fun Sak.hentTilbakekrevingsbehandling(id: TilbakekrevingsbehandlingId): Tilbakekrevingsbehandling? =
    this.behandlinger.tilbakekrevinger.hent(id)
