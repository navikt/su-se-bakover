package tilbakekreving.infrastructure.repo.kravgrunnlag

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagStatusendringPåSakHendelse

fun KravgrunnlagStatusendringPåSakHendelse.toJson(): String {
    return KravgrunnlagStatusendringPåSakDbJson(
        status = this.status.toDbString(),
        eksternVedtakId = this.eksternVedtakId,
        saksnummer = this.saksnummer.toString(),
        eksternTidspunkt = this.eksternTidspunkt.toString(),
    ).let {
        serialize(it)
    }
}

fun PersistertHendelse.toKravgrunnlagStatusendringPåSakHendelse(): KravgrunnlagStatusendringPåSakHendelse {
    return Either.catch {
        deserialize<KravgrunnlagStatusendringPåSakDbJson>(this.data).let { json ->
            KravgrunnlagStatusendringPåSakHendelse.fraPersistert(
                hendelseId = this.hendelseId,
                hendelsestidspunkt = this.hendelsestidspunkt,
                forrigeVersjon = this.versjon,
                entitetId = this.entitetId,
                sakId = this.sakId!!,
                tidligereHendelseId = this.tidligereHendelseId!!,
                status = json.status.toKravgrunnlagStatus(),
                eksternVedtakId = json.eksternVedtakId,
                saksnummer = Saksnummer.parse(json.saksnummer),
                eksternTidspunkt = Tidspunkt.parse(json.eksternTidspunkt),
            )
        }
    }.getOrElse {
        sikkerLogg.error("Kunne ikke deserialisere KravgrunnlagPåSakHendelse for hendelse $this", it)
        throw IllegalStateException("Kunne ikke deserialisere KravgrunnlagPåSakHendelse for hendelse ${this.hendelseId.value} og sakId ${this.sakId}. Se sikkerlogg for data og stacktrace")
    }
}

/**
 * @see [tilbakekreving.domain.kravgrunnlag.KravgrunnlagStatusendringPåSakHendelse]
 */
private data class KravgrunnlagStatusendringPåSakDbJson(
    val status: String,
    val eksternVedtakId: String,
    val saksnummer: String,
    val eksternTidspunkt: String,
)
