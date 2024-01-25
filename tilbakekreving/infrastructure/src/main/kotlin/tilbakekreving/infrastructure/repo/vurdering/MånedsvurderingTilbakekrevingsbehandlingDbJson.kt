package tilbakekreving.infrastructure.repo.vurdering

import arrow.core.Nel
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.VurdertTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.vurdering.PeriodevurderingMedKrav
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import tilbakekreving.infrastructure.repo.TilbakekrevingDbJson
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal fun PersistertHendelse.mapToVurdertTilbakekrevingsbehandlingHendelse(): VurdertTilbakekrevingsbehandlingHendelse {
    val deserialized = deserialize<MånedsvurderingTilbakekrevingsbehandlingDbJson>(data)

    return VurdertTilbakekrevingsbehandlingHendelse(
        hendelseId = hendelseId,
        sakId = sakId!!,
        hendelsestidspunkt = hendelsestidspunkt,
        versjon = versjon,
        id = TilbakekrevingsbehandlingId(deserialized.behandlingsId),
        tidligereHendelseId = tidligereHendelseId!!,
        utførtAv = NavIdentBruker.Saksbehandler(navIdent = deserialized.utførtAv),
        vurderingerMedKrav = deserialized.toDomain(),
    )
}

private data class MånedsvurderingTilbakekrevingsbehandlingDbJson(
    override val behandlingsId: UUID,
    override val utførtAv: String,
    val vurderinger: List<PeriodevurderingMedKravDbJson>,
    val saksnummer: String,
    val eksternKravgrunnlagId: String,
    val eksternVedtakId: String,
    val eksternKontrollfelt: String,
) : TilbakekrevingDbJson {
    fun toDomain(): VurderingerMedKrav {
        return VurderingerMedKrav.fraPersistert(
            perioder = vurderinger.map { it.toDomain() }.toNonEmptyList(),
            saksnummer = Saksnummer.parse(saksnummer),
            eksternKravgrunnlagId = eksternKravgrunnlagId,
            eksternVedtakId = eksternVedtakId,
            eksternKontrollfelt = eksternKontrollfelt,
        )
    }
}

internal fun VurdertTilbakekrevingsbehandlingHendelse.toJson(): String {
    return MånedsvurderingTilbakekrevingsbehandlingDbJson(
        behandlingsId = this.id.value,
        utførtAv = this.utførtAv.navIdent,
        vurderinger = this.vurderingerMedKrav.toJson(),
        saksnummer = this.vurderingerMedKrav.saksnummer.toString(),
        eksternKravgrunnlagId = this.vurderingerMedKrav.eksternKravgrunnlagId,
        eksternVedtakId = this.vurderingerMedKrav.eksternVedtakId,
        eksternKontrollfelt = this.vurderingerMedKrav.eksternKontrollfelt,
    ).let {
        serialize(it)
    }
}

private data class PeriodevurderingMedKravDbJson(
    val fraOgMed: String,
    val tilOgMed: String,
    val vurdering: String,
    val betaltSkattForYtelsesgruppen: Int,
    val bruttoTidligereUtbetalt: Int,
    val bruttoNyUtbetaling: Int,
    val bruttoSkalIkkeTilbakekreve: Int,
    val bruttoSkalTilbakekreve: Int,
    val nettoSkalTilbakekreve: Int,
    val skattSomGårTilReduksjon: Int,
    val skatteProsent: String,
) {
    fun toDomain(): PeriodevurderingMedKrav {
        val periode = DatoIntervall(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
        val skatteProsent = BigDecimal(this.skatteProsent)
        return when (vurdering) {
            "SkalIkkeTilbakekreve" -> {
                PeriodevurderingMedKrav.SkalIkkeTilbakekreve(
                    periode = periode,
                    betaltSkattForYtelsesgruppen = this.betaltSkattForYtelsesgruppen,
                    bruttoTidligereUtbetalt = this.bruttoTidligereUtbetalt,
                    bruttoNyUtbetaling = this.bruttoNyUtbetaling,
                    bruttoSkalIkkeTilbakekreve = this.bruttoSkalIkkeTilbakekreve,
                    skatteProsent = skatteProsent,
                )
            }

            "SkalTilbakekreve" -> PeriodevurderingMedKrav.SkalTilbakekreve(
                periode = periode,
                betaltSkattForYtelsesgruppen = this.betaltSkattForYtelsesgruppen,
                bruttoTidligereUtbetalt = this.bruttoTidligereUtbetalt,
                bruttoNyUtbetaling = this.bruttoNyUtbetaling,
                bruttoSkalTilbakekreve = this.bruttoSkalTilbakekreve,
                nettoSkalTilbakekreve = this.nettoSkalTilbakekreve,
                skatteProsent = skatteProsent,
                skattSomGårTilReduksjon = this.skattSomGårTilReduksjon,
            )

            else -> throw IllegalArgumentException("Ukjent vurderingstype")
        }
    }
}

private fun VurderingerMedKrav.toJson(): Nel<PeriodevurderingMedKravDbJson> = this.perioder.map {
    PeriodevurderingMedKravDbJson(
        fraOgMed = it.periode.fraOgMed.toString(),
        tilOgMed = it.periode.tilOgMed.toString(),
        vurdering = when (it) {
            is PeriodevurderingMedKrav.SkalIkkeTilbakekreve -> "SkalIkkeTilbakekreve"
            is PeriodevurderingMedKrav.SkalTilbakekreve -> "SkalTilbakekreve"
        },
        betaltSkattForYtelsesgruppen = it.betaltSkattForYtelsesgruppen,
        bruttoTidligereUtbetalt = it.bruttoTidligereUtbetalt,
        bruttoNyUtbetaling = it.bruttoNyUtbetaling,
        bruttoSkalIkkeTilbakekreve = it.bruttoSkalIkkeTilbakekreve,
        bruttoSkalTilbakekreve = it.bruttoSkalTilbakekreve,
        nettoSkalTilbakekreve = it.nettoSkalTilbakekreve,
        skatteProsent = it.skatteProsent.toString(),
        skattSomGårTilReduksjon = it.skattSomGårTilReduksjon,
    )
}
