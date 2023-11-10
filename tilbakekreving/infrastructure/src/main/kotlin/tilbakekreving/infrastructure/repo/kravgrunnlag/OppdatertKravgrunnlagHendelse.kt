package tilbakekreving.infrastructure.repo.kravgrunnlag

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.OppdatertKravgrunnlagPåTilbakekrevingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagDbJson.Companion.toDbJson
import java.util.UUID

private data class OppdatertKravgrunnlagHendelseDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val kravgrunnlag: KravgrunnlagDbJson,
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
        oppdatertKravgrunnlag = deserialized.kravgrunnlag.toDomain(),
    )
}

fun OppdatertKravgrunnlagPåTilbakekrevingHendelse.toJson(): String =
    OppdatertKravgrunnlagHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        kravgrunnlag = this.oppdatertKravgrunnlag.toDbJson(),
    ).let { serialize(it) }
