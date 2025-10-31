@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.underkjennelse.UnderkjennAttesteringsgrunnTilbakekreving
import java.time.Clock
import java.util.UUID

data class UnderkjentHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val id: TilbakekrevingsbehandlingId,
    override val tidligereHendelseId: HendelseId,
    override val utførtAv: NavIdentBruker.Attestant,
    val grunn: UnderkjennAttesteringsgrunnTilbakekreving,
    val begrunnelse: String,
) : TilbakekrevingsbehandlingHendelse {

    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun create(
            sakId: UUID,
            tidligereHendelseId: HendelseId,
            versjon: Hendelsesversjon,
            clock: Clock,
            id: TilbakekrevingsbehandlingId,
            utførtAv: NavIdentBruker.Attestant,
            grunn: UnderkjennAttesteringsgrunnTilbakekreving,
            begrunnelse: String,
        ) = UnderkjentHendelse(
            hendelseId = HendelseId.generer(),
            sakId = sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            id = id,
            tidligereHendelseId = tidligereHendelseId,
            utførtAv = utførtAv,
            grunn = grunn,
            begrunnelse = begrunnelse,
        )
    }

    fun applyToState(behandling: Tilbakekrevingsbehandling): UnderBehandling.MedKravgrunnlag.Utfylt {
        return when (behandling) {
            is OpprettetTilbakekrevingsbehandling,
            is UnderBehandling,
            is AvbruttTilbakekrevingsbehandling,
            is IverksattTilbakekrevingsbehandling,
            -> throw IllegalArgumentException("Kan ikke gå fra [Opprettet, Vurdert, Avbrutt, Iverksatt] -> Underkjenn. Støtter kun å fra TilAttestering. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")

            is TilbakekrevingsbehandlingTilAttestering -> {
                UnderBehandling.MedKravgrunnlag.Utfylt(
                    forrigeSteg = behandling,
                    hendelseId = this.hendelseId,
                    versjon = this.versjon,
                    attesteringer = behandling.attesteringer.leggTilNyAttestering(
                        attestering = Attestering.Underkjent(
                            attestant = this.utførtAv,
                            opprettet = this.hendelsestidspunkt,
                            grunn = grunn,
                            kommentar = begrunnelse,
                        ),
                    ),
                    vurderingerMedKrav = behandling.vurderingerMedKrav,
                    vedtaksbrevvalg = behandling.vedtaksbrevvalg,
                    forhåndsvarselsInfo = behandling.forhåndsvarselsInfo,
                    notat = behandling.notat,
                )
            }
        }
    }
}

fun TilbakekrevingsbehandlingTilAttestering.underkjenn(
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
    utførtAv: NavIdentBruker.Attestant,
    grunn: UnderkjennAttesteringsgrunnTilbakekreving,
    kommentar: String,
): Pair<UnderkjentHendelse, UnderBehandling.MedKravgrunnlag.Utfylt> {
    return UnderkjentHendelse.create(
        sakId = this.sakId,
        tidligereHendelseId = this.hendelseId,
        versjon = nesteVersjon,
        clock = clock,
        id = this.id,
        utførtAv = utførtAv,
        grunn = grunn,
        begrunnelse = kommentar,
    ).let { it to it.applyToState(this) }
}
