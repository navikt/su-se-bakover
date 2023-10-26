package tilbakekreving.infrastructure.repo.underkjenn

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.UnderkjentHendelse
import tilbakekreving.domain.underkjent.UnderkjennAttesteringsgrunnTilbakekreving
import java.util.UUID

private data class UnderkjennHendelseDbJsonHendelseDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val kommentar: String,
    val grunn: String,
)

internal fun mapToTilUnderkjentHendelse(
    data: String,
    hendelseId: HendelseId,
    sakId: UUID,
    hendelsestidspunkt: Tidspunkt,
    versjon: Hendelsesversjon,
    meta: DefaultHendelseMetadata,
    tidligereHendelseId: HendelseId,
): UnderkjentHendelse {
    val deserialized = deserialize<UnderkjennHendelseDbJsonHendelseDbJson>(data)

    return UnderkjentHendelse(
        hendelseId = hendelseId,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        meta = meta,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        utførtAv = NavIdentBruker.Attestant(deserialized.utførtAv),
        tidligereHendelseId = tidligereHendelseId,
        grunn = UnderkjennAttesteringsgrunnTilbakekreving.valueOf(deserialized.grunn),
        begrunnelse = deserialized.kommentar,
    )
}

internal fun UnderkjentHendelse.toJson(): String {
    return UnderkjennHendelseDbJsonHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        kommentar = this.begrunnelse,
        grunn = this.grunn.toString(),
    ).let {
        serialize(it)
    }
}
