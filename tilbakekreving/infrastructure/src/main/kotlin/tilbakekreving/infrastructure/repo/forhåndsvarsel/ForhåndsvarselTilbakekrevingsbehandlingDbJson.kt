package tilbakekreving.infrastructure.repo.forhåndsvarsel

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.ForhåndsvarsletTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

internal data class ForhåndsvarselTilbakekrevingsbehandlingDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val fritekst: String,
    val dokumentId: UUID,
) {
    companion object {
        fun toDomain(
            data: String,
            hendelseId: HendelseId,
            sakId: UUID,
            tidligereHendelsesId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
        ): ForhåndsvarsletTilbakekrevingsbehandlingHendelse {
            val deserialized = deserialize<ForhåndsvarselTilbakekrevingsbehandlingDbJson>(data)

            return ForhåndsvarsletTilbakekrevingsbehandlingHendelse(
                hendelseId = hendelseId,
                sakId = sakId,
                hendelsestidspunkt = hendelsestidspunkt,
                versjon = versjon,
                id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
                tidligereHendelseId = tidligereHendelsesId,
                utførtAv = NavIdentBruker.Saksbehandler(navIdent = deserialized.utførtAv),
                fritekst = deserialized.fritekst,
                dokumentId = deserialized.dokumentId,
            )
        }
    }
}

internal fun ForhåndsvarsletTilbakekrevingsbehandlingHendelse.toJson(): String =
    ForhåndsvarselTilbakekrevingsbehandlingDbJson(
        fritekst = this.fritekst,
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        dokumentId = this.dokumentId,
    ).let { serialize(it) }
