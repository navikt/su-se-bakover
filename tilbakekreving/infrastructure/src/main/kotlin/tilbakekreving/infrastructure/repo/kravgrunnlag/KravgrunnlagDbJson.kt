package tilbakekreving.infrastructure.repo.kravgrunnlag

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagDbJson.Companion.toDbJson
import tilbakekreving.infrastructure.repo.vurdering.GrunnlagsperiodeDbJson
import tilbakekreving.infrastructure.repo.vurdering.GrunnlagsperiodeDbJson.Companion.toDbJson

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
    val grunnlagsperioder: List<GrunnlagsperiodeDbJson>,
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
            grunnlagsperioder = this.grunnlagsperioder.map { it.toDomain() },
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
                grunnlagsperioder = this.grunnlagsperioder.map { it.toDbJson() },
                eksternTidspunkt = eksternTidspunkt,
                hendelseId = this.hendelseId.toString(),
            )
        }
    }
}

/**
 * Historisk.
 * Da vi hadde tilbakekreving under revurdering, startet vi med å kun lagre kravgrunnlagsXMLen vi mottok fra Oppdrag i databasetabellen 'revurdering_tilbakekreving'.
 * På et tidspunkt, mens vi laget en egen behandling for dette støttet vi fremdeles den gamle løsninga.
 * Da endret vi til å lagre [tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlagHendelse], slik at vi i 'revurdering_tilbakekreving' kun trengte en serialisert versjon av [KravgrunnlagDbJson].
 */
fun mapDbJsonToKravgrunnlag(value: String): Either<Throwable, Kravgrunnlag> {
    return Either.catch {
        deserialize<KravgrunnlagDbJson>(value).toDomain()
    }
}
