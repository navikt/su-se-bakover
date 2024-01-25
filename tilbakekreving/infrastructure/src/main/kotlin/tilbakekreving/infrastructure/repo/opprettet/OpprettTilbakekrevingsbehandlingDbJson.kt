package tilbakekreving.infrastructure.repo.opprettet

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.infrastructure.repo.TilbakekrevingDbJson
import java.util.UUID

internal data class OpprettTilbakekrevingsbehandlingHendelseDbJson(
    override val behandlingsId: UUID,
    override val utførtAv: String,
    val kravgrunnlagPåSakHendelseId: String,
) : TilbakekrevingDbJson

internal fun PersistertHendelse.mapToOpprettetTilbakekrevingsbehandlingHendelse(): OpprettetTilbakekrevingsbehandlingHendelse {
    val deserialized = deserialize<OpprettTilbakekrevingsbehandlingHendelseDbJson>(data)

    return OpprettetTilbakekrevingsbehandlingHendelse(
        hendelseId = hendelseId,
        sakId = sakId!!,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        opprettetAv = NavIdentBruker.Saksbehandler(deserialized.utførtAv),
        kravgrunnlagPåSakHendelseId = HendelseId.fromString(deserialized.kravgrunnlagPåSakHendelseId),
    )
}

internal fun OpprettetTilbakekrevingsbehandlingHendelse.toJson(): String {
    return OpprettTilbakekrevingsbehandlingHendelseDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.opprettetAv.navIdent,
        kravgrunnlagPåSakHendelseId = this.kravgrunnlagPåSakHendelseId.toString(),
    ).let {
        serialize(it)
    }
}
