package tilbakekreving.infrastructure.repo.iverksatt

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

private data class IverksattHendelseDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
)

internal fun mapToTilIverksattHendelse(
    data: String,
    hendelseId: HendelseId,
    sakId: UUID,
    hendelsestidspunkt: Tidspunkt,
    versjon: Hendelsesversjon,
    tidligereHendelseId: HendelseId,
): IverksattHendelse {
    val deserialized = deserialize<IverksattHendelseDbJson>(data)

    return IverksattHendelse(
        hendelseId = hendelseId,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        utførtAv = NavIdentBruker.Attestant(deserialized.utførtAv),
        tidligereHendelseId = tidligereHendelseId,
    )
}

internal fun IverksattHendelse.toJson(): String {
    return IverksattHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
    ).let {
        serialize(it)
    }
}
