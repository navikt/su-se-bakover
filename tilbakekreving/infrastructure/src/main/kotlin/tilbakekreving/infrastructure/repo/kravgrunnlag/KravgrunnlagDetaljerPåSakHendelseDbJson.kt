package tilbakekreving.infrastructure.repo.kravgrunnlag

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagDbJson.Companion.toDbJson
import java.util.UUID

fun KravgrunnlagDetaljerPåSakHendelse.toJson(): String {
    return KravgrunnlagDetaljerPåSakDbJson(
        kravgrunnlag = kravgrunnlag.toDbJson(),
        revurderingId = revurderingId?.toString(),
    ).let {
        serialize(it)
    }
}

fun PersistertHendelse.toKravgrunnlagDetaljerPåSakHendelse(): KravgrunnlagDetaljerPåSakHendelse {
    return Either.catch {
        deserialize<KravgrunnlagDetaljerPåSakDbJson>(this.data).let { json ->
            KravgrunnlagDetaljerPåSakHendelse.fraPersistert(
                hendelseId = this.hendelseId,
                hendelsestidspunkt = this.hendelsestidspunkt,
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
 * @see [tilbakekreving.domain.kravgrunnlag.KravgrunnlagDetaljerPåSakHendelse]
 */
private data class KravgrunnlagDetaljerPåSakDbJson(
    val kravgrunnlag: KravgrunnlagDbJson,
    val revurderingId: String?,
)
