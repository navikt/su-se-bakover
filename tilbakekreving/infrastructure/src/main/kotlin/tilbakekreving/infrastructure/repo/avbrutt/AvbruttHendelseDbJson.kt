package tilbakekreving.infrastructure.repo.avbrutt

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.AvbruttHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

private data class AvbruttHendelseDbJsonHendelseDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val begrunnelse: String,
)

internal fun mapToTilAvbruttHendelse(
    data: String,
    hendelseId: HendelseId,
    sakId: UUID,
    hendelsestidspunkt: Tidspunkt,
    versjon: Hendelsesversjon,
    tidligereHendelseId: HendelseId,
): AvbruttHendelse {
    val deserialized = deserialize<AvbruttHendelseDbJsonHendelseDbJson>(data)

    return AvbruttHendelse(
        hendelseId = hendelseId,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        utførtAv = NavIdentBruker.Saksbehandler(deserialized.utførtAv),
        tidligereHendelseId = tidligereHendelseId,
        begrunnelse = deserialized.begrunnelse,
    )
}

internal fun AvbruttHendelse.toJson(): String {
    return AvbruttHendelseDbJsonHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        begrunnelse = this.begrunnelse,
    ).let { serialize(it) }
}
