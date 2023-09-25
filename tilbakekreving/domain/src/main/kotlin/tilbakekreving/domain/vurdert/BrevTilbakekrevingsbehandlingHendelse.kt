package tilbakekreving.domain.vurdert

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.lang.IllegalStateException
import java.util.UUID

data class BrevTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: HendelseMetadata,
    override val tidligereHendelseId: HendelseId,
    val id: TilbakekrevingsbehandlingId,
    val utførtAv: NavIdentBruker.Saksbehandler,
    val brevvalg: Brevvalg.SaksbehandlersValg,
) : Sakshendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    init {
        when (brevvalg) {
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst ->
                throw IllegalStateException("Ved tilbakekreving for sak $sakId, må brevet være av typen Vedtaksbrev. Tidligere hendelse var $tidligereHendelseId")

            is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev,
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev,
            -> Unit
        }
    }
}
