@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.vurdering.KunneIkkeVurdereTilbakekrevingsbehandling
import tilbakekreving.domain.vurdering.VurderCommand
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.time.Clock
import java.util.UUID

data class VurdertTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val tidligereHendelseId: HendelseId,
    override val id: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    val vurderingerMedKrav: VurderingerMedKrav,
) : TilbakekrevingsbehandlingHendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    fun applyToState(behandling: Tilbakekrevingsbehandling): UnderBehandling {
        return when (behandling) {
            is KanVurdere -> behandling.leggTilVurderinger(
                månedsvurderinger = this.vurderingerMedKrav,
                hendelseId = this.hendelseId,
                versjon = this.versjon,
            )

            is AvbruttTilbakekrevingsbehandling,
            is IverksattTilbakekrevingsbehandling,
            is TilbakekrevingsbehandlingTilAttestering,
            is OpprettetTilbakekrevingsbehandlingUtenKravgrunnlag,
            is UnderBehandling.UtenKravgrunnlag,
            -> throw IllegalArgumentException("Kan ikke gå fra [Avbrutt, Iverksatt, TilAttestering] -> Vurdert. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")
        }
    }
}

fun KanVurdere.leggTilVurdering(
    command: VurderCommand,
    tidligereHendelsesId: HendelseId,
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
): Either<KunneIkkeVurdereTilbakekrevingsbehandling, Pair<VurdertTilbakekrevingsbehandlingHendelse, UnderBehandling>> {
    val hendelse = VurdertTilbakekrevingsbehandlingHendelse(
        hendelseId = HendelseId.generer(),
        sakId = command.sakId,
        hendelsestidspunkt = Tidspunkt.now(clock),
        versjon = nesteVersjon,
        tidligereHendelseId = tidligereHendelsesId,
        id = command.behandlingsId,
        utførtAv = command.utførtAv,
        vurderingerMedKrav = VurderingerMedKrav.utledFra(command.vurderinger, this.kravgrunnlag)
            .getOrElse { return it.left() },
    )
    return (hendelse to hendelse.applyToState(this)).right()
}
