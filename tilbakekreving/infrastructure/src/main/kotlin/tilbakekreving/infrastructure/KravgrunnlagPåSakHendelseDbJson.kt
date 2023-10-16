package tilbakekreving.infrastructure

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
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
    return Either.catch {
        deserialize<KravgrunnlagPåSakDbJson>(this.data).let { json ->
            KravgrunnlagPåSakHendelse.fraPersistert(
                hendelseId = this.hendelseId,
                hendelsestidspunkt = this.hendelsestidspunkt,
                hendelseMetadata = this.defaultHendelseMetadata(),
                forrigeVersjon = this.versjon,
                entitetId = this.entitetId,
                sakId = this.sakId!!,
                tidligereHendelseId = this.tidligereHendelseId!!,
                kravgrunnlag = json.kravgrunnlag.toDomain(),
                revurderingId = json.revurderingId?.let { UUID.fromString(it) },
            )
        }
    }.getOrElse {
        sikkerLogg.error("Kunne ikke deserialisere KravgrunnlagPåSakHendelse for hendelse $this", it)
        throw IllegalStateException("Kunne ikke deserialisere KravgrunnlagPåSakHendelse for hendelse ${this.hendelseId.value} og sakId ${this.sakId}. Se sikkerlogg for data og stacktrace")
    }
}

/**
 * @see [tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse]
 */
private data class KravgrunnlagPåSakDbJson(
    val kravgrunnlag: KravgrunnlagDbJson,
    val revurderingId: String?,
)
