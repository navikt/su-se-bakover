package tilbakekreving.infrastructure.repo.notat

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.NonBlankString.Companion.toNonBlankString
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.NotatTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.infrastructure.repo.TilbakekrevingDbJson
import java.util.UUID

private data class NotatHendelseDbJson(
    override val behandlingsId: UUID,
    override val utførtAv: String,
    val notat: String?,
) : TilbakekrevingDbJson

internal fun mapTilNotatHendelse(
    data: String,
    hendelseId: HendelseId,
    tidligereHendelsesId: HendelseId,
    sakId: UUID,
    hendelsestidspunkt: Tidspunkt,
    versjon: Hendelsesversjon,
): NotatTilbakekrevingsbehandlingHendelse {
    val deserialized = deserialize<NotatHendelseDbJson>(data)

    return NotatTilbakekrevingsbehandlingHendelse(
        hendelseId = hendelseId,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        tidligereHendelseId = tidligereHendelsesId,
        utførtAv = NavIdentBruker.Saksbehandler(navIdent = deserialized.utførtAv),
        notat = deserialized.notat?.toNonBlankString(),
    )
}

internal fun NotatTilbakekrevingsbehandlingHendelse.toJson(): String = NotatHendelseDbJson(
    behandlingsId = this.id.value,
    utførtAv = this.utførtAv.navIdent,
    notat = this.notat?.value,
).let { serialize(it) }
