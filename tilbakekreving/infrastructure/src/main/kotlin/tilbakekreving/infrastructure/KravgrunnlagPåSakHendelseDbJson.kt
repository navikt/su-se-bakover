package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import tilbakekreving.infrastructure.KravgrunnlagDbJson.Companion.toDbJson
import java.util.UUID

fun KravgrunnlagPåSakHendelse.toJson(): String {
    return KravgrunnlagPåSakDbJson(
        kravgrunnlag = kravgrunnlag.toDbJson(),
        revurderingId = revurderingId?.toString(),
    ).let {
        serialize(it)
    }
}

fun PersistertHendelse.toKravgrunnlagPåSakHendelse(): KravgrunnlagPåSakHendelse {
    return deserialize<KravgrunnlagPåSakDbJson>(this.data).let { json ->
        KravgrunnlagPåSakHendelse.fraPersistert(
            hendelseId = this.hendelseId,
            hendelsestidspunkt = this.hendelsestidspunkt,
            hendelseMetadata = this.hendelseMetadata,
            forrigeVersjon = this.versjon,
            entitetId = this.entitetId,
            sakId = this.sakId!!,
            tidligereHendelseId = this.tidligereHendelseId!!,
            kravgrunnlag = json.kravgrunnlag.toDomain(),
            revurderingId = json.revurderingId?.let { UUID.fromString(it) },
        )
    }
}

/**
 * @see [tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse]
 */
private data class KravgrunnlagPåSakDbJson(
    val kravgrunnlag: KravgrunnlagDbJson,
    val revurderingId: String?,
)

/**
 * @see [tilbakekreving.domain.kravgrunnlag.Grunnlagsmåned]
 */
private data class GrunnlagsmånedDbJson(
    val måned: String,
    val betaltSkattForYtelsesgruppen: String,
    val grunnlagsbeløp: List<GrunnlagsbeløpDbJson>,
)

/**
 * @see [tilbakekreving.domain.kravgrunnlag.Kravgrunnlag.Grunnlagsmåned.Grunnlagsbeløp]
 */
private data class GrunnlagsbeløpDbJson(
    val kode: String,
    val type: String,
    val beløpTidligereUtbetaling: String,
    val beløpNyUtbetaling: String,
    val beløpSkalTilbakekreves: String,
    val beløpSkalIkkeTilbakekreves: String,
    val skatteProsent: String,
)
