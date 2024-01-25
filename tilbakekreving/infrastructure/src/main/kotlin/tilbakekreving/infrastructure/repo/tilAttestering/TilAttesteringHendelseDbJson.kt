package tilbakekreving.infrastructure.repo.tilAttestering

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.TilAttesteringHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.infrastructure.repo.TilbakekrevingDbJson
import java.util.UUID

private data class TilAttesteringHendelseDbJson(
    override val behandlingsId: UUID,
    override val utførtAv: String,
) : TilbakekrevingDbJson

internal fun mapToTilAttesteringHendelse(
    data: String,
    hendelseId: HendelseId,
    sakId: UUID,
    hendelsestidspunkt: Tidspunkt,
    versjon: Hendelsesversjon,
    tidligereHendelseId: HendelseId,
): TilAttesteringHendelse {
    val deserialized = deserialize<TilAttesteringHendelseDbJson>(data)

    return TilAttesteringHendelse(
        hendelseId = hendelseId,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        utførtAv = NavIdentBruker.Saksbehandler(deserialized.utførtAv),
        tidligereHendelseId = tidligereHendelseId,
    )
}

internal fun TilAttesteringHendelse.toJson(): String {
    return TilAttesteringHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
    ).let {
        serialize(it)
    }
}
