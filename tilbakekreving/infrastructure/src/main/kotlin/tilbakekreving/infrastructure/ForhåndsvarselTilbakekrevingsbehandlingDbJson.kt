package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.ForhåndsvarsleTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

internal data class ForhåndsvarselTilbakekrevingsbehandlingDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val fritekst: String,
) {
    companion object {
        fun toDomain(
            data: String,
            hendelseId: HendelseId,
            sakId: UUID,
            tidligereHendelsesId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
        ): ForhåndsvarsleTilbakekrevingsbehandlingHendelse {
            val deserialized = deserialize<ForhåndsvarselTilbakekrevingsbehandlingDbJson>(data)

            return ForhåndsvarsleTilbakekrevingsbehandlingHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = versjon,
                meta = meta,
                id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
                tidligereHendelseId = tidligereHendelsesId,
                utførtAv = NavIdentBruker.Saksbehandler(navIdent = deserialized.utførtAv),
                fritekst = deserialized.fritekst,
            )
        }
    }
}

internal fun ForhåndsvarsleTilbakekrevingsbehandlingHendelse.toJson(): String =
    ForhåndsvarselTilbakekrevingsbehandlingDbJson(
        fritekst = this.fritekst,
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
    ).let { serialize(it) }
