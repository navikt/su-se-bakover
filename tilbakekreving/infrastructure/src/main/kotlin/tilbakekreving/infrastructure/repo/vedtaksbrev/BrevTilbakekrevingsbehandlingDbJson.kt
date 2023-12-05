package tilbakekreving.infrastructure.repo.vedtaksbrev

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.BrevvalgDbJson
import no.nav.su.se.bakover.dokument.infrastructure.BrevvalgDbJson.Companion.toJson
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

internal fun mapToBrevTilbakekrevingsbehandlingHendelse(
    data: String,
    hendelseId: HendelseId,
    tidligereHendelsesId: HendelseId,
    sakId: UUID,
    hendelsestidspunkt: Tidspunkt,
    versjon: Hendelsesversjon,
): BrevTilbakekrevingsbehandlingHendelse {
    val deserialized = deserialize<BrevTilbakekrevingsbehandlingDbJson>(data)

    return BrevTilbakekrevingsbehandlingHendelse(
        hendelseId = hendelseId,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        tidligereHendelseId = tidligereHendelsesId,
        utførtAv = NavIdentBruker.Saksbehandler(navIdent = deserialized.utførtAv),
        brevvalg = deserialized.brevvalg.toDomain() as Brevvalg.SaksbehandlersValg,
    )
}

private data class BrevTilbakekrevingsbehandlingDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val brevvalg: BrevvalgDbJson,
)

internal fun BrevTilbakekrevingsbehandlingHendelse.toJson(): String = BrevTilbakekrevingsbehandlingDbJson(
    behandlingsId = this.id.value,
    utførtAv = this.utførtAv.navIdent,
    brevvalg = this.brevvalg.toJson(),
).let { serialize(it) }
