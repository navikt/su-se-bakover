package tilbakekreving.infrastructure.repo.iverksatt

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.infrastructure.repo.TilbakekrevingDbJson
import java.util.UUID

private data class IverksattHendelseDbJson(
    override val behandlingsId: UUID,
    override val utførtAv: String,
    val vedtakId: UUID,
) : TilbakekrevingDbJson

internal fun PersistertHendelse.mapToTilIverksattHendelse(): IverksattHendelse {
    val deserialized = deserialize<IverksattHendelseDbJson>(data)

    return IverksattHendelse(
        hendelseId = hendelseId,
        sakId = sakId!!,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        utførtAv = NavIdentBruker.Attestant(deserialized.utførtAv),
        tidligereHendelseId = tidligereHendelseId!!,
        vedtakId = deserialized.vedtakId,
        fritekstTilBrev = "",
    )
}

internal fun IverksattHendelse.toJson(): String {
    return IverksattHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        vedtakId = this.vedtakId,
    ).let {
        serialize(it)
    }
}
