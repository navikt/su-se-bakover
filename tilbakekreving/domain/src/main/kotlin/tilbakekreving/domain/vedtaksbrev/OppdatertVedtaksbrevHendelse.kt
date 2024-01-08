@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.vedtaksbrev.OppdaterVedtaksbrevCommand
import java.time.Clock
import java.util.UUID

data class BrevTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val tidligereHendelseId: HendelseId,
    override val id: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    val brevvalg: Brevvalg.SaksbehandlersValg,
) : TilbakekrevingsbehandlingHendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    init {
        when (brevvalg) {
            // TODO jah: Dette tyder på at vi bør ha en egen brevvalg-type for tilbakekreving som ikke åpner for dette valget.
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst ->
                throw IllegalStateException("Ved tilbakekreving for sak $sakId, må brevet være av typen Vedtaksbrev. Tidligere hendelse var $tidligereHendelseId")

            is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev,
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev,
            -> Unit
        }
    }

    fun applyToState(behandling: Tilbakekrevingsbehandling): UnderBehandling.Utfylt {
        return when (behandling) {
            is TilbakekrevingsbehandlingTilAttestering,
            is AvbruttTilbakekrevingsbehandling,
            is IverksattTilbakekrevingsbehandling,
            is OpprettetTilbakekrevingsbehandling,
            -> throw IllegalArgumentException("Kan ikke gå fra [Avbrutt, Iverksatt, TilAttestering, Opprettet] -> Vurdert.Utfylt. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")

            is UnderBehandling -> behandling.oppdaterVedtaksbrev(
                vedtaksbrevvalg = this.brevvalg,
                hendelseId = this.hendelseId,
                versjon = this.versjon,
            )
        }
    }
}

fun KanOppdatereVedtaksbrev.leggTilBrevtekst(
    command: OppdaterVedtaksbrevCommand,
    tidligereHendelsesId: HendelseId,
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
): Pair<BrevTilbakekrevingsbehandlingHendelse, UnderBehandling.Utfylt> {
    val hendelse = BrevTilbakekrevingsbehandlingHendelse(
        hendelseId = HendelseId.generer(),
        sakId = command.sakId,
        hendelsestidspunkt = Tidspunkt.now(clock),
        versjon = nesteVersjon,
        tidligereHendelseId = tidligereHendelsesId,
        id = command.behandlingId,
        utførtAv = command.utførtAv,
        brevvalg = command.brevvalg,
    )

    return hendelse to hendelse.applyToState(this)
}
