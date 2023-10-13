package tilbakekreving.infrastructure

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.infrastructure.FeilutbetalingDb.Companion.toDbJson
import tilbakekreving.infrastructure.GrunnlagsmånedDb.Companion.toDbJson
import tilbakekreving.infrastructure.KravgrunnlagDbJson.Companion.toDbJson
import tilbakekreving.infrastructure.YtelseDb.Companion.toDbJson

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
) {
    fun toDomain(): Kravgrunnlag {
        return Kravgrunnlag(
            eksternKravgrunnlagId = this.eksternKravgrunnlagId,
            eksternVedtakId = this.eksternVedtakId,
            eksternKontrollfelt = this.eksternKontrollfelt,
            status = when (this.status) {
                "Annulert" -> Kravgrunnlag.KravgrunnlagStatus.Annulert
                "AnnulertVedOmg" -> Kravgrunnlag.KravgrunnlagStatus.AnnulertVedOmg
                "Avsluttet" -> Kravgrunnlag.KravgrunnlagStatus.Avsluttet
                "Ferdigbehandlet" -> Kravgrunnlag.KravgrunnlagStatus.Ferdigbehandlet
                "Endret" -> Kravgrunnlag.KravgrunnlagStatus.Endret
                "Feil" -> Kravgrunnlag.KravgrunnlagStatus.Feil
                "Manuell" -> Kravgrunnlag.KravgrunnlagStatus.Manuell
                "Nytt" -> Kravgrunnlag.KravgrunnlagStatus.Nytt
                "Sperret" -> Kravgrunnlag.KravgrunnlagStatus.Sperret
                else -> throw IllegalStateException("Ukjent persistert kravgrunnlagsstatus på KravgrunnlagPåSakHendelse: ${this.status}")
            },
            behandler = this.behandler,
            utbetalingId = UUID30.fromString(this.utbetalingId),
            grunnlagsmåneder = this.grunnlagsmåneder.map { it.toDomain() },
            saksnummer = Saksnummer.parse(this.saksnummer),
            eksternTidspunkt = eksternTidspunkt,
        )
    }

    companion object {
        fun Kravgrunnlag.toDbJson(): KravgrunnlagDbJson {
            return KravgrunnlagDbJson(
                eksternKravgrunnlagId = this.eksternKravgrunnlagId,
                eksternVedtakId = this.eksternVedtakId,
                eksternKontrollfelt = this.eksternKontrollfelt,
                status = when (this.status) {
                    Kravgrunnlag.KravgrunnlagStatus.Annulert -> "Annullert"
                    Kravgrunnlag.KravgrunnlagStatus.AnnulertVedOmg -> "AnnulertVedOmg"
                    Kravgrunnlag.KravgrunnlagStatus.Avsluttet -> "Avsluttet"
                    Kravgrunnlag.KravgrunnlagStatus.Ferdigbehandlet -> "Ferdigbehandlet"
                    Kravgrunnlag.KravgrunnlagStatus.Endret -> "Endret"
                    Kravgrunnlag.KravgrunnlagStatus.Feil -> "Feil"
                    Kravgrunnlag.KravgrunnlagStatus.Manuell -> "Manuell"
                    Kravgrunnlag.KravgrunnlagStatus.Nytt -> "Nytt"
                    Kravgrunnlag.KravgrunnlagStatus.Sperret -> "Sperret"
                },
                behandler = this.behandler,
                utbetalingId = this.utbetalingId.value,
                saksnummer = this.saksnummer.toString(),
                grunnlagsmåneder = this.grunnlagsmåneder.map { it.toDbJson() },
                eksternTidspunkt = eksternTidspunkt,
            )
        }
    }
}
fun mapDbJsonToKravgrunnlag(value: String): Either<Throwable, Kravgrunnlag> {
    return Either.catch {
        deserialize<KravgrunnlagDbJson>(value).toDomain()
    }
}
fun mapKravgrunnlagToDbJson(kravgrunnlag: Kravgrunnlag): String {
    return serialize(kravgrunnlag.toDbJson())
}
