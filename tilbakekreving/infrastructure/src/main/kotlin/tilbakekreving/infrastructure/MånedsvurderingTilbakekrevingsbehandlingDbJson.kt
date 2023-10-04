package tilbakekreving.infrastructure

import arrow.core.Nel
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.MånedsvurderingerTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.vurdert.Månedsvurdering
import tilbakekreving.domain.vurdert.Månedsvurderinger
import tilbakekreving.domain.vurdert.Vurdering
import tilbakekreving.infrastructure.MånedsvurderingerDbJson.Companion.toDomain
import java.lang.IllegalArgumentException
import java.time.YearMonth
import java.util.UUID

internal fun mapToMånedsvurderingerTilbakekrevingsbehandlingHendelse(
    data: String,
    hendelseId: HendelseId,
    tidligereHendelsesId: HendelseId,
    sakId: UUID,
    hendelsestidspunkt: Tidspunkt,
    versjon: Hendelsesversjon,
    meta: DefaultHendelseMetadata,
): MånedsvurderingerTilbakekrevingsbehandlingHendelse {
    val deserialized = deserialize<MånedsvurderingTilbakekrevingsbehandlingDbJson>(data)

    return MånedsvurderingerTilbakekrevingsbehandlingHendelse(
        hendelseId = hendelseId,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        meta = meta,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        tidligereHendelseId = tidligereHendelsesId,
        utførtAv = NavIdentBruker.Saksbehandler(navIdent = deserialized.utførtAv),
        vurderinger = deserialized.vurderinger.toDomain(),
    )
}

private data class MånedsvurderingTilbakekrevingsbehandlingDbJson(
    val behandlingsId: UUID,
    val utførtAv: String,
    val vurderinger: List<MånedsvurderingerDbJson>,
)

internal fun MånedsvurderingerTilbakekrevingsbehandlingHendelse.toJson(): String {
    return MånedsvurderingTilbakekrevingsbehandlingDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        vurderinger = this.vurderinger.toJson(),
    ).let {
        serialize(it)
    }
}

private data class MånedsvurderingerDbJson(
    val måned: String,
    val vurdering: String,
) {
    fun toDomain(): Månedsvurdering = Månedsvurdering(
        måned = Måned.fra(YearMonth.parse(måned)),
        vurdering = when (vurdering) {
            "SkalIkkeTilbakekreve" -> Vurdering.SkalIkkeTilbakekreve
            "SkalTilbakekreve" -> Vurdering.SkalTilbakekreve
            else -> throw IllegalArgumentException("Ukjent vurderingstype")
        },
    )

    companion object {
        fun List<MånedsvurderingerDbJson>.toDomain(): Månedsvurderinger = Månedsvurderinger(this.map { it.toDomain() }.toNonEmptyList())
    }
}

private fun Månedsvurderinger.toJson(): Nel<MånedsvurderingerDbJson> = this.vurderinger.map {
    MånedsvurderingerDbJson(
        måned = it.måned.toString(),
        vurdering = when (it.vurdering) {
            Vurdering.SkalIkkeTilbakekreve -> "SkalIkkeTilbakekreve"
            Vurdering.SkalTilbakekreve -> "SkalTilbakekreve"
        },
    )
}
