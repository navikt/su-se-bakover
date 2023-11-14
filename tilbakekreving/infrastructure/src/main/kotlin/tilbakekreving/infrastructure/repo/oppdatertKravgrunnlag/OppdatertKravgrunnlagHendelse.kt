package tilbakekreving.infrastructure.repo.oppdatertKravgrunnlag

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.OppdatertKravgrunnlagPåTilbakekrevingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

private data class OppdatertKravgrunnlagHendelseDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val kravgrunnlagPåSakHendelseId: String,
)

fun mapTilOppdatertKravgrunnlagPåTilbakekrevingHendelse(
    data: String,
    hendelseId: HendelseId,
    sakId: UUID,
    tidligereHendelsesId: HendelseId,
    hendelsestidspunkt: Tidspunkt,
    versjon: Hendelsesversjon,
    meta: DefaultHendelseMetadata,
): OppdatertKravgrunnlagPåTilbakekrevingHendelse {
    val deserialized = deserialize<OppdatertKravgrunnlagHendelseDbJson>(data)

    return OppdatertKravgrunnlagPåTilbakekrevingHendelse(
        hendelseId = hendelseId,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        meta = meta,
        tidligereHendelseId = tidligereHendelsesId,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        utførtAv = NavIdentBruker.Saksbehandler(navIdent = deserialized.utførtAv),
        kravgrunnlagPåSakHendelseId = HendelseId.fromString(deserialized.kravgrunnlagPåSakHendelseId),
    )
}

fun OppdatertKravgrunnlagPåTilbakekrevingHendelse.toJson(): String =
    OppdatertKravgrunnlagHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        kravgrunnlagPåSakHendelseId = this.kravgrunnlagPåSakHendelseId.toString(),
    ).let { serialize(it) }
