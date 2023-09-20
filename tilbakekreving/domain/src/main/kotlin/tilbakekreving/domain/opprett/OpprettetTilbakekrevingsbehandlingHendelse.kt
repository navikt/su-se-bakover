package tilbakekreving.domain.opprett

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.time.Clock
import java.util.UUID

/**
 * [OpprettTilbakekrevingsbehandlingCommand] fører potensielt til en [OpprettetTilbakekrevingsbehandlingHendelse].
 * Selve tilstanden (som knyttes til Sak.kt) representeres ved [OpprettetTilbakekrevingsbehandling]
 *
 * @param id knytter en serie med tilbakekrevingsbehandling hendelser (de som hører til samme behandling)
 */
data class OpprettetTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: HendelseMetadata,
    val id: TilbakekrevingsbehandlingId,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val kravgrunnlag: Kravgrunnlag,
) : Sakshendelse {

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
            meta: HendelseMetadata,
            versjon: Hendelsesversjon,
            clock: Clock,
            kravgrunnlag: Kravgrunnlag,
        ) = OpprettetTilbakekrevingsbehandlingHendelse(
            hendelseId = HendelseId.generer(),
            sakId = sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            opprettetAv = opprettetAv,
            versjon = versjon,
            meta = meta,
            id = TilbakekrevingsbehandlingId.generer(),
            kravgrunnlag = kravgrunnlag,
        )
    }

    fun toDomain(): OpprettetTilbakekrevingsbehandling {
        return OpprettetTilbakekrevingsbehandling(
            id = id,
            sakId = sakId,
            opprettet = hendelsestidspunkt,
            opprettetAv = opprettetAv,
            kravgrunnlag = kravgrunnlag,
        )
    }
}
