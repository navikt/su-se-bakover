package no.nav.su.se.bakover.database.historisk

import behandling.revurdering.domain.Opphørsgrunn
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdAttestering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdForhåndsvarsel
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskVedtakSomRevurderes
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskeVedtakSomRevurderesMånedsvis
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

internal data class HistoriskeVedtakSomRevurderesMånedsvisDbJson(
    val måneder: List<MånedDbJson>,
) {
    data class MånedDbJson(
        val måned: String,
        val type: String,
        val vedtakId: String,
    )

    fun toDomain(): HistoriskeVedtakSomRevurderesMånedsvis =
        måneder.associateTo(linkedMapOf()) {
            val måned = Måned.fra(YearMonth.parse(it.måned))
            val vedtak = when (it.type) {
                "ORIGINALT_INFOTRYGD_VEDTAK" ->
                    HistoriskVedtakSomRevurderes.OriginaltInfotrygdVedtak(HistoriskVedtakId(it.vedtakId.toLong()))
                "HISTORISK_REVURDERINGSVEDTAK" ->
                    HistoriskVedtakSomRevurderes.HistoriskRevurderingsvedtak(
                        HistoriskInfotrygdRevurderingsvedtakId(UUID.fromString(it.vedtakId)),
                    )
                else -> error("Ukjent type historisk vedtak som revurderes: ${it.type}")
            }
            måned to vedtak
        }.let(::HistoriskeVedtakSomRevurderesMånedsvis)

    fun serialize(): String = serialize(this)

    companion object {
        fun fromDomain(value: HistoriskeVedtakSomRevurderesMånedsvis) =
            HistoriskeVedtakSomRevurderesMånedsvisDbJson(
                måneder = value.map { (måned, vedtak) ->
                    when (vedtak) {
                        is HistoriskVedtakSomRevurderes.OriginaltInfotrygdVedtak ->
                            MånedDbJson(måned.toString(), "ORIGINALT_INFOTRYGD_VEDTAK", vedtak.vedtakId.value.toString())
                        is HistoriskVedtakSomRevurderes.HistoriskRevurderingsvedtak ->
                            MånedDbJson(måned.toString(), "HISTORISK_REVURDERINGSVEDTAK", vedtak.vedtakId.value.toString())
                    }
                },
            )

        fun deserialize(value: String): HistoriskeVedtakSomRevurderesMånedsvis =
            deserialize<HistoriskeVedtakSomRevurderesMånedsvisDbJson>(value).toDomain()
    }
}

internal data class FradragForMånedDbJson(
    val kategori: String,
    val beskrivelse: String?,
    val månedsbeløp: Double,
    val tilhører: String,
    val utenlandskInntekt: UtenlandskInntektDbJson?,
) {
    fun toDomain(måned: Måned): FradragForMåned = FradragForMåned(
        fradragstype = Fradragstype.from(Fradragstype.Kategori.valueOf(kategori), beskrivelse),
        månedsbeløp = månedsbeløp,
        måned = måned,
        utenlandskInntekt = utenlandskInntekt?.toDomain(),
        tilhører = FradragTilhører.valueOf(tilhører),
    )
}

private fun FradragForMåned.toDbJson() = FradragForMånedDbJson(
    kategori = fradragstype.kategori.name,
    beskrivelse = (fradragstype as? Fradragstype.Annet)?.beskrivelse,
    månedsbeløp = månedsbeløp,
    tilhører = tilhører.name,
    utenlandskInntekt = utenlandskInntekt?.let {
        UtenlandskInntektDbJson(it.beløpIUtenlandskValuta, it.valuta, it.kurs)
    },
)

internal data class UtenlandskInntektDbJson(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double,
) {
    fun toDomain(): UtenlandskInntekt = UtenlandskInntekt.create(beløpIUtenlandskValuta, valuta, kurs)
}

internal data class HistoriskInfotrygdBeregningDbJson(
    val månedsresultater: List<HistoriskInfotrygdRevurdertMånedsresultatDbJson>,
) {
    fun toDomain(): HistoriskInfotrygdBeregning = HistoriskInfotrygdBeregning(
        månedsresultater = månedsresultater.associate { resultat ->
            val domain = resultat.toDomain()
            domain.måned to domain
        },
    )

    fun serialize(): String = serialize(this)

    companion object {
        fun fromDomain(value: HistoriskInfotrygdBeregning): HistoriskInfotrygdBeregningDbJson =
            HistoriskInfotrygdBeregningDbJson(
                månedsresultater = value.månedsresultater.values.map { it.toDbJson() },
            )

        fun deserialize(value: String): HistoriskInfotrygdBeregning =
            deserialize<HistoriskInfotrygdBeregningDbJson>(value).toDomain()
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class HistoriskInfotrygdRevurdertMånedsresultatDbJson(
    val type: String,
    val måned: String,
    val opprinneligStønadId: Long,
    val opprinneligVedtakId: Long,
    val oppdragId: String?,
    val bosituasjon: String?,
    val sats: BigDecimal?,
    val fradrag: List<FradragForMånedDbJson>?,
    val benyttetRegel: Regelspesifisering,
    val opphørsgrunn: String? = null,
    val manueltOpphør: Boolean? = null,
) {
    fun toDomain(): HistoriskInfotrygdRevurdertMånedsresultat {
        val måned = Måned.fra(YearMonth.parse(måned))
        return when (type) {
            "YTELSE" -> HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                måned = måned,
                opprinneligStønadId = HistoriskStønadId(opprinneligStønadId),
                opprinneligVedtakId = HistoriskVedtakId(opprinneligVedtakId),
                oppdragId = oppdragId,
                bosituasjon = HistoriskBosituasjon.valueOf(requireNotNull(bosituasjon)),
                sats = requireNotNull(sats),
                fradrag = requireNotNull(fradrag).map { it.toDomain(måned) },
                benyttetRegel = benyttetRegel,
            )
            "OPPHØR" -> HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                måned = måned,
                opprinneligStønadId = HistoriskStønadId(opprinneligStønadId),
                opprinneligVedtakId = HistoriskVedtakId(opprinneligVedtakId),
                oppdragId = oppdragId,
                bosituasjon = HistoriskBosituasjon.valueOf(requireNotNull(bosituasjon)),
                sats = requireNotNull(sats),
                fradrag = requireNotNull(fradrag).map { it.toDomain(måned) },
                opphørsgrunn = Opphørsgrunn.valueOf(requireNotNull(opphørsgrunn)),
                manueltOpphør = requireNotNull(manueltOpphør),
                benyttetRegel = benyttetRegel,
            )
            else -> error("Ukjent historisk beregningsresultat: $type")
        }
    }
}

private fun HistoriskInfotrygdRevurdertMånedsresultat.toDbJson() = when (this) {
    is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse ->
        HistoriskInfotrygdRevurdertMånedsresultatDbJson(
            type = "YTELSE",
            måned = måned.toString(),
            opprinneligStønadId = opprinneligStønadId.value,
            opprinneligVedtakId = opprinneligVedtakId.value,
            oppdragId = oppdragId,
            bosituasjon = bosituasjon.name,
            sats = sats,
            fradrag = fradrag.map(FradragForMåned::toDbJson),
            benyttetRegel = benyttetRegel,
        )
    is HistoriskInfotrygdRevurdertMånedsresultat.Opphør ->
        HistoriskInfotrygdRevurdertMånedsresultatDbJson(
            type = "OPPHØR",
            måned = måned.toString(),
            opprinneligStønadId = opprinneligStønadId.value,
            opprinneligVedtakId = opprinneligVedtakId.value,
            oppdragId = oppdragId,
            bosituasjon = bosituasjon.name,
            sats = sats,
            fradrag = fradrag.map(FradragForMåned::toDbJson),
            opphørsgrunn = opphørsgrunn.name,
            manueltOpphør = manueltOpphør,
            benyttetRegel = benyttetRegel,
        )
}

internal data class HistoriskInfotrygdAttesteringDbJson(
    val type: String,
    val attestant: String,
    val begrunnelse: String,
    val tidspunkt: String,
) {
    fun toDomain(): HistoriskInfotrygdAttestering = when (type) {
        "GODKJENT" -> HistoriskInfotrygdAttestering.Godkjent(
            attestant = NavIdentBruker.Attestant(attestant),
            tidspunkt = Tidspunkt.parse(tidspunkt),
        )
        "UNDERKJENT" -> HistoriskInfotrygdAttestering.Underkjent(
            attestant = NavIdentBruker.Attestant(attestant),
            begrunnelse = begrunnelse,
            tidspunkt = Tidspunkt.parse(tidspunkt),
        )
        else -> error("Ukjent historisk attestering: $type")
    }
}

internal fun List<HistoriskInfotrygdAttestering>.serializeAttesteringer(): String = map {
    when (it) {
        is HistoriskInfotrygdAttestering.Godkjent -> HistoriskInfotrygdAttesteringDbJson(
            type = "GODKJENT",
            attestant = it.attestant.navIdent,
            begrunnelse = "",
            tidspunkt = it.tidspunkt.toString(),
        )
        is HistoriskInfotrygdAttestering.Underkjent -> HistoriskInfotrygdAttesteringDbJson(
            type = "UNDERKJENT",
            attestant = it.attestant.navIdent,
            begrunnelse = it.begrunnelse,
            tidspunkt = it.tidspunkt.toString(),
        )
    }
}.let(::serialize)

internal fun String.deserializeAttesteringer(): List<HistoriskInfotrygdAttestering> =
    deserialize<List<HistoriskInfotrygdAttesteringDbJson>>(this).map { it.toDomain() }

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class HistoriskInfotrygdForhåndsvarselDbJson(
    val type: String,
    val fritekst: String? = null,
    val navIdent: String? = null,
    val tidspunkt: String? = null,
    val utdatert: Boolean = false,
) {
    fun toDomain(): HistoriskInfotrygdForhåndsvarsel = when (type) {
        "IKKE_VALGT" -> HistoriskInfotrygdForhåndsvarsel.IkkeValgt
        "IKKE_SENDT" -> HistoriskInfotrygdForhåndsvarsel.IkkeSendt(
            vurdertAv = NavIdentBruker.Saksbehandler(requireNotNull(navIdent)),
            vurdert = Tidspunkt.parse(requireNotNull(tidspunkt)),
            utdatert = utdatert,
        )
        "SENDT" -> HistoriskInfotrygdForhåndsvarsel.Sendt(
            fritekst = requireNotNull(fritekst),
            sendtAv = NavIdentBruker.Saksbehandler(requireNotNull(navIdent)),
            sendt = Tidspunkt.parse(requireNotNull(tidspunkt)),
            utdatert = utdatert,
        )
        else -> error("Ukjent historisk forhåndsvarsel: $type")
    }
}

internal fun HistoriskInfotrygdForhåndsvarsel.serializeForhåndsvarsel(): String = when (this) {
    HistoriskInfotrygdForhåndsvarsel.IkkeValgt ->
        HistoriskInfotrygdForhåndsvarselDbJson(type = "IKKE_VALGT")
    is HistoriskInfotrygdForhåndsvarsel.IkkeSendt ->
        HistoriskInfotrygdForhåndsvarselDbJson(
            type = "IKKE_SENDT",
            navIdent = vurdertAv.navIdent,
            tidspunkt = vurdert.toString(),
            utdatert = utdatert,
        )
    is HistoriskInfotrygdForhåndsvarsel.Sendt ->
        HistoriskInfotrygdForhåndsvarselDbJson(
            type = "SENDT",
            fritekst = fritekst,
            navIdent = sendtAv.navIdent,
            tidspunkt = sendt.toString(),
            utdatert = utdatert,
        )
}.let(::serialize)

internal fun String.deserializeForhåndsvarsel(): HistoriskInfotrygdForhåndsvarsel =
    deserialize<HistoriskInfotrygdForhåndsvarselDbJson>(this).toDomain()
