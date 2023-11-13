@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagDetaljerPåSakHendelse
import java.time.Clock
import java.util.UUID

/**
 * [tilbakekreving.domain.opprett.OpprettTilbakekrevingsbehandlingCommand] fører potensielt til en [OpprettetTilbakekrevingsbehandlingHendelse].
 * Selve tilstanden (som knyttes til Sak.kt) representeres ved [OpprettetTilbakekrevingsbehandling]
 *
 * @param id knytter en serie med tilbakekrevingsbehandling hendelser (de som hører til samme behandling)
 */
data class OpprettetTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: DefaultHendelseMetadata,
    override val id: TilbakekrevingsbehandlingId,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val kravgrunnlagsId: String,
) : TilbakekrevingsbehandlingHendelse {

    override val utførtAv: NavIdentBruker.Saksbehandler = opprettetAv

    // Dette vil være den første hendelsen i denne behandlingen.
    override val tidligereHendelseId: HendelseId? = null

    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        /**
         * Oppretter en opprettet tilbakekrevingsbehandlinghendelse med en tilfeldig id.
         */
        fun opprett(
            sakId: UUID,
            opprettetAv: NavIdentBruker.Saksbehandler,
            meta: DefaultHendelseMetadata,
            versjon: Hendelsesversjon,
            clock: Clock,
            eksternKravgrunnlagId: String,
        ) = OpprettetTilbakekrevingsbehandlingHendelse(
            hendelseId = HendelseId.generer(),
            sakId = sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            opprettetAv = opprettetAv,
            versjon = versjon,
            meta = meta,
            id = TilbakekrevingsbehandlingId.generer(),
            kravgrunnlagsId = eksternKravgrunnlagId,
        )
    }
    override fun applyToState(behandling: Tilbakekrevingsbehandling): Tilbakekrevingsbehandling {
        throw IllegalArgumentException("En tilbakekrevingsbehandling kan kun starte med en Opprettet hendelse ${this.hendelseId}, for sak ${this.sakId} ")
    }

    fun toDomain(kravgrunnlagPåSakHendelse: KravgrunnlagDetaljerPåSakHendelse, erKravgrunnlagUtdatert: Boolean): OpprettetTilbakekrevingsbehandling {
        return toDomain(kravgrunnlagPåSakHendelse.kravgrunnlag, erKravgrunnlagUtdatert)
    }

    fun toDomain(kravgrunnlag: Kravgrunnlag, erKravgrunnlagUtdatert: Boolean): OpprettetTilbakekrevingsbehandling {
        require(kravgrunnlag.eksternKravgrunnlagId == this.kravgrunnlagsId)
        return OpprettetTilbakekrevingsbehandling(
            id = id,
            sakId = sakId,
            opprettet = hendelsestidspunkt,
            opprettetAv = opprettetAv,
            kravgrunnlag = kravgrunnlag,
            versjon = versjon,
            hendelseId = hendelseId,
            erKravgrunnlagUtdatert = erKravgrunnlagUtdatert,
        )
    }
}
