package tilbakekreving.infrastructure.repo.kravgrunnlag

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagDbJson.Companion.toDbJson
import tilbakekreving.infrastructure.repo.vurdering.GrunnlagsmånedDb
import tilbakekreving.infrastructure.repo.vurdering.GrunnlagsmånedDb.Companion.toDbJson

/**
 * Databasejsontype for [tilbakekreving.domain.kravgrunnlag.Kravgrunnlag]
 */
internal data class KravgrunnlagDbJson(
    val eksternKravgrunnlagId: String,
    val eksternVedtakId: String,
    val eksternKontrollfelt: String,
    val status: String,
    val behandler: String,
    val utbetalingId: String,
    val saksnummer: String,
    val grunnlagsmåneder: List<GrunnlagsmånedDb>,
    val eksternTidspunkt: Tidspunkt,
    val hendelseId: String,
) {
    fun toDomain(): Kravgrunnlag {
        return Kravgrunnlag(
            eksternKravgrunnlagId = this.eksternKravgrunnlagId,
            eksternVedtakId = this.eksternVedtakId,
            eksternKontrollfelt = this.eksternKontrollfelt,
            status = this.status.toKravgrunnlagStatus(),
            behandler = this.behandler,
            utbetalingId = UUID30.fromString(this.utbetalingId),
            grunnlagsmåneder = this.grunnlagsmåneder.map { it.toDomain() },
            saksnummer = Saksnummer.parse(this.saksnummer),
            eksternTidspunkt = eksternTidspunkt,
            hendelseId = HendelseId.fromString(hendelseId),
        )
    }

    companion object {
        fun Kravgrunnlag.toDbJson(): KravgrunnlagDbJson {
            return KravgrunnlagDbJson(
                eksternKravgrunnlagId = this.eksternKravgrunnlagId,
                eksternVedtakId = this.eksternVedtakId,
                eksternKontrollfelt = this.eksternKontrollfelt,
                status = this.status.toDbString(),
                behandler = this.behandler,
                utbetalingId = this.utbetalingId.value,
                saksnummer = this.saksnummer.toString(),
                grunnlagsmåneder = this.grunnlagsmåneder.map { it.toDbJson() },
                eksternTidspunkt = eksternTidspunkt,
                hendelseId = this.hendelseId.toString(),
            )
        }
    }
}

/**
 * [Deprecated] - denne brukes av den gamle kravgrunnlag under revurdering rutinen og kan slettes sammen med den.
 */
fun mapDbJsonToKravgrunnlag(value: String): Either<Throwable, Kravgrunnlag> {
    return Either.catch {
        deserialize<KravgrunnlagDbJson>(value).toDomain()
    }
}
fun mapKravgrunnlagToDbJson(kravgrunnlag: Kravgrunnlag): String {
    return serialize(kravgrunnlag.toDbJson())
}
