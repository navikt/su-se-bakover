package tilbakekreving.infrastructure.repo.oppdatertKravgrunnlag

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.OppdatertKravgrunnlagPåTilbakekrevingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

private data class OppdatertKravgrunnlagHendelseDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val kravgrunnlagPåSakHendelseId: String,
)

internal fun PersistertHendelse.mapTilOppdatertKravgrunnlagPåTilbakekrevingHendelse(): OppdatertKravgrunnlagPåTilbakekrevingHendelse {
    val deserialized = deserialize<OppdatertKravgrunnlagHendelseDbJson>(data)

    return OppdatertKravgrunnlagPåTilbakekrevingHendelse(
        hendelseId = hendelseId,
        sakId = sakId!!,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        tidligereHendelseId = tidligereHendelseId!!,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        utførtAv = NavIdentBruker.Saksbehandler(navIdent = deserialized.utførtAv),
        kravgrunnlagPåSakHendelseId = HendelseId.fromString(deserialized.kravgrunnlagPåSakHendelseId),
    )
}

internal fun OppdatertKravgrunnlagPåTilbakekrevingHendelse.toJson(): String =
    OppdatertKravgrunnlagHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        kravgrunnlagPåSakHendelseId = this.kravgrunnlagPåSakHendelseId.toString(),
    ).let { serialize(it) }
