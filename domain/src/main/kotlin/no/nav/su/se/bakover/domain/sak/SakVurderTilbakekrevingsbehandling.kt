package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.domain.Sak
import org.slf4j.LoggerFactory
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.KanOppdatereVedtaksbrev
import tilbakekreving.domain.KanVurdere
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.VurdertTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.leggTilBrevtekst
import tilbakekreving.domain.leggTilVurdering
import tilbakekreving.domain.vedtaksbrev.OppdaterVedtaksbrevCommand
import tilbakekreving.domain.vurdering.KunneIkkeVurdereTilbakekrevingsbehandling
import tilbakekreving.domain.vurdering.VurderCommand
import java.time.Clock

private val log = LoggerFactory.getLogger("SakVurderTilbakekrevingsbehandling.kt")

fun Sak.vurderTilbakekrevingsbehandling(
    command: VurderCommand,
    clock: Clock,
): Either<KunneIkkeVurdereTilbakekrevingsbehandling, Pair<VurdertTilbakekrevingsbehandlingHendelse, UnderBehandling>> {
    val behandling = (this.hentTilbakekrevingsbehandling(command.behandlingsId) as? KanVurdere)
        ?: throw IllegalStateException("Tilbakekrevingsbehandling ${command.behandlingsId} enten fantes ikke eller var ikke i KanVurdere tilstanden. Sak id $id, saksnummer $saksnummer")

    if (this.uteståendeKravgrunnlag != behandling.kravgrunnlag) {
        log.info("Kunne ikke sende tilbakekrevingsbehandling $id til attestering, kravgrunnlaget på behandlingen (eksternKravgrunnlagId ${behandling.kravgrunnlag.eksternKravgrunnlagId}) er ikke det samme som det som er på saken (eksternKravgrunnlagId ${this.uteståendeKravgrunnlag?.eksternKravgrunnlagId}). For sakId ${this.id}")
        return KunneIkkeVurdereTilbakekrevingsbehandling.KravgrunnlagetHarEndretSeg.left()
    }
    return behandling.leggTilVurdering(
        command = command,
        tidligereHendelsesId = behandling.hendelseId,
        nesteVersjon = this.versjon.inc(),
        clock = clock,
    )
}

fun Sak.oppdaterVedtaksbrev(
    command: OppdaterVedtaksbrevCommand,
    clock: Clock,
): Pair<BrevTilbakekrevingsbehandlingHendelse, UnderBehandling.MedKravgrunnlag.Utfylt> {
    return (this.hentTilbakekrevingsbehandling(command.behandlingId) as? KanOppdatereVedtaksbrev)?.let { behandling ->
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
