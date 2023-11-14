package tilbakekreving.infrastructure.repo.opprettet

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

internal data class OpprettTilbakekrevingsbehandlingHendelseDbJson(
    val behandlingsId: UUID,
    val opprettetAv: String,
    val kravgrunnlagPåSakHendelseId: String,
) {
    companion object {
        fun toDomain(
            data: String,
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
        ): OpprettetTilbakekrevingsbehandlingHendelse {
            val deserialized = deserialize<OpprettTilbakekrevingsbehandlingHendelseDbJson>(data)

            return OpprettetTilbakekrevingsbehandlingHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = versjon,
                meta = meta,
                id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
                opprettetAv = NavIdentBruker.Saksbehandler(deserialized.opprettetAv),
                kravgrunnlagPåSakHendelseId = HendelseId.fromString(deserialized.kravgrunnlagPåSakHendelseId),
            )
        }
    }
}

internal fun OpprettetTilbakekrevingsbehandlingHendelse.toJson(): String {
    return OpprettTilbakekrevingsbehandlingHendelseDbJson(
        behandlingsId = this.id.value,
        opprettetAv = this.opprettetAv.navIdent,
        kravgrunnlagPåSakHendelseId = this.kravgrunnlagPåSakHendelseId.toString(),
    ).let {
        serialize(it)
    }
}
