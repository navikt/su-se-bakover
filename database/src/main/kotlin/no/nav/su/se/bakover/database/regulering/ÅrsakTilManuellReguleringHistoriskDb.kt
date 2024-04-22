package no.nav.su.se.bakover.database.regulering

import arrow.core.getOrElse
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.tid.periode.Perioder
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.FradragMåHåndteresManuelt::class,
        name = "FradragMåHåndteresManuelt",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.UtbetalingFeilet::class,
        name = "UtbetalingFeilet",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.BrukerManglerSupplement::class,
        name = "BrukerManglerSupplement",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.SupplementInneholderIkkeFradraget::class,
        name = "SupplementInneholderIkkeFradraget",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.FinnesFlerePerioderAvFradrag::class,
        name = "FinnesFlerePerioderAvFradrag",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.FradragErUtenlandsinntekt::class,
        name = "FradragErUtenlandsinntekt",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.SupplementHarFlereVedtaksperioderForFradrag::class,
        name = "SupplementHarFlereVedtaksperioderForFradrag",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.MismatchMellomBeløpFraSupplementOgFradrag::class,
        name = "MismatchMellomBeløpFraSupplementOgFradrag",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.BeløpErStørreEnForventet::class,
        name = "BeløpErStørreEnForventet",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.YtelseErMidlertidigStanset::class,
        name = "YtelseErMidlertidigStanset",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.ForventetInntektErStørreEnn0::class,
        name = "ForventetInntektErStørreEnn0",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.AutomatiskSendingTilUtbetalingFeilet::class,
        name = "AutomatiskSendingTilUtbetalingFeilet",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.VedtakstidslinjeErIkkeSammenhengende::class,
        name = "VedtakstidslinjeErIkkeSammenhengende",
    ),
    JsonSubTypes.Type(
        value = ÅrsakTilManuellReguleringJson.DelvisOpphør::class,
        name = "DelvisOpphør",
    ),
)
internal sealed interface ÅrsakTilManuellReguleringJson {
    // TODO test alle disse
    fun toDomain(): ÅrsakTilManuellRegulering

    data object FradragMåHåndteresManuelt : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt
    }

    data object UtbetalingFeilet : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering = ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet
    }

    data class BrukerManglerSupplement(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement(
                fradragstype = Fradragstype.tryParse(
                    value = fradragstype,
                    // Fradragstypen 'Annet' har en beskrivelse. 'Annet' skal ikke justeres manuelt, og skal derfor ikke
                    // være et fradrag som vi trenger å håndtere manuelt. Det vil si at vi ikke får den her
                    beskrivelse = null,
                ).getOrElse {
                    throw IllegalStateException("Kunne ikke parse fradragstype $it ved henting av regulering")
                },
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class SupplementInneholderIkkeFradraget(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
                fradragstype = Fradragstype.tryParse(
                    value = fradragstype,
                    // Fradragstypen 'Annet' har en beskrivelse. 'Annet' skal ikke justeres manuelt, og skal derfor ikke
                    // være et fradrag som vi trenger å håndtere manuelt. Det vil si at vi ikke får den her
                    beskrivelse = null,
                ).getOrElse {
                    throw IllegalStateException("Kunne ikke parse fradragstype $it ved henting av regulering")
                },
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class FinnesFlerePerioderAvFradrag(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag(
                fradragstype = Fradragstype.tryParse(
                    value = fradragstype,
                    // Fradragstypen 'Annet' har en beskrivelse. 'Annet' skal ikke justeres manuelt, og skal derfor ikke
                    // være et fradrag som vi trenger å håndtere manuelt. Det vil si at vi ikke får den her
                    beskrivelse = null,
                ).getOrElse {
                    throw IllegalStateException("Kunne ikke parse fradragstype $it ved henting av regulering")
                },
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class FradragErUtenlandsinntekt(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt(
                fradragstype = Fradragstype.tryParse(
                    value = fradragstype,
                    // Fradragstypen 'Annet' har en beskrivelse. 'Annet' skal ikke justeres manuelt, og skal derfor ikke
                    // være et fradrag som vi trenger å håndtere manuelt. Det vil si at vi ikke får den her
                    beskrivelse = null,
                ).getOrElse {
                    throw IllegalStateException("Kunne ikke parse fradragstype $it ved henting av regulering")
                },
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
            )
    }

    data class SupplementHarFlereVedtaksperioderForFradrag(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterneReguleringsvedtakperioder: List<PeriodeMedOptionalTilOgMedJson>,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag(
                fradragstype = Fradragstype.tryParse(
                    value = fradragstype,
                    // Fradragstypen 'Annet' har en beskrivelse. 'Annet' skal ikke justeres manuelt, og skal derfor ikke
                    // være et fradrag som vi trenger å håndtere manuelt. Det vil si at vi ikke får den her
                    beskrivelse = null,
                ).getOrElse {
                    throw IllegalStateException("Kunne ikke parse fradragstype $it ved henting av regulering")
                },
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
                eksterneReguleringsvedtakperioder = eksterneReguleringsvedtakperioder.map { it.toDomain() },
            )
    }

    data class MismatchMellomBeløpFraSupplementOgFradrag(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterntBeløpFørRegulering: String,
        val vårtBeløpFørRegulering: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.MismatchMellomBeløpFraSupplementOgFradrag(
                fradragstype = Fradragstype.tryParse(
                    value = fradragstype,
                    // Fradragstypen 'Annet' har en beskrivelse. 'Annet' skal ikke justeres manuelt, og skal derfor ikke
                    // være et fradrag som vi trenger å håndtere manuelt. Det vil si at vi ikke får den her
                    beskrivelse = null,
                ).getOrElse {
                    throw IllegalStateException("Kunne ikke parse fradragstype $it ved henting av regulering")
                },
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
                eksterntBeløpFørRegulering = eksterntBeløpFørRegulering.toBigDecimal(),
                vårtBeløpFørRegulering = vårtBeløpFørRegulering.toBigDecimal(),
            )
    }

    data class BeløpErStørreEnForventet(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterntBeløpEtterRegulering: String,
        val forventetBeløpEtterRegulering: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BeløpErStørreEnForventet(
                fradragstype = Fradragstype.tryParse(
                    value = fradragstype,
                    // Fradragstypen 'Annet' har en beskrivelse. 'Annet' skal ikke justeres manuelt, og skal derfor ikke
                    // være et fradrag som vi trenger å håndtere manuelt. Det vil si at vi ikke får den her
                    beskrivelse = null,
                ).getOrElse {
                    throw IllegalStateException("Kunne ikke parse fradragstype $it ved henting av regulering")
                },
                fradragTilhører = FradragTilhører.valueOf(fradragTilhører),
                begrunnelse = begrunnelse,
                eksterntBeløpEtterRegulering = eksterntBeløpEtterRegulering.toBigDecimal(),
                forventetBeløpEtterRegulering = forventetBeløpEtterRegulering.toBigDecimal(),
            )
    }

    data class YtelseErMidlertidigStanset(
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering = if (begrunnelse == null) {
            ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset
        } else {
            ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset(begrunnelse = begrunnelse)
        }
    }

    data class ForventetInntektErStørreEnn0(
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering = if (begrunnelse == null) {
            ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0
        } else {
            ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0(begrunnelse)
        }
    }

    data class AutomatiskSendingTilUtbetalingFeilet(
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet(begrunnelse)
    }

    data class VedtakstidslinjeErIkkeSammenhengende(
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende(begrunnelse)
    }

    data class DelvisOpphør(
        val opphørsperioder: List<PeriodeJson>,
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson {
        override fun toDomain(): ÅrsakTilManuellRegulering =
            ÅrsakTilManuellRegulering.DelvisOpphør(
                opphørsperioder.map { it.toPeriode() }.let { Perioder.create(it) },
                begrunnelse,
            )
    }

    companion object {
        fun toDomain(json: String): Set<ÅrsakTilManuellRegulering> =
            deserialize<ÅrsakerTilManuellRegulering>(json).toDomain()
    }
}

internal fun Reguleringstype.årsakerTilManuellReguleringJson(): String =
    when (this) {
        Reguleringstype.AUTOMATISK -> serialize(ÅrsakerTilManuellRegulering.empty())
        is Reguleringstype.MANUELL -> this.problemer.toDbJson()
    }

internal fun Set<ÅrsakTilManuellRegulering>.toDbJson(): String =
    serialize(ÅrsakerTilManuellRegulering(this.map { it.toDbJson() }))

internal data class ÅrsakerTilManuellRegulering(
    val årsaker: List<String>,
) {
    fun toDomain(): Set<ÅrsakTilManuellRegulering> =
        årsaker.map { deserialize<ÅrsakTilManuellReguleringJson>(it).toDomain() }.toSet()

    companion object {
        fun empty(): ÅrsakerTilManuellRegulering = ÅrsakerTilManuellRegulering(emptyList())
    }
}

internal fun ÅrsakTilManuellRegulering.toDbJson(): String = when (this) {
    is ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet -> ÅrsakTilManuellReguleringJson.AutomatiskSendingTilUtbetalingFeilet(
        begrunnelse = this.begrunnelse,
    )

    is ÅrsakTilManuellRegulering.DelvisOpphør -> ÅrsakTilManuellReguleringJson.DelvisOpphør(
        opphørsperioder = this.opphørsperioder.map { it.toJson() },
        begrunnelse = this.begrunnelse,
    )

    is ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0 -> ÅrsakTilManuellReguleringJson.ForventetInntektErStørreEnn0(
        begrunnelse = this.begrunnelse,
    )

    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BeløpErStørreEnForventet -> ÅrsakTilManuellReguleringJson.BeløpErStørreEnForventet(
        begrunnelse = this.begrunnelse,
        fradragstype = this.fradragstype.kategori.toString(),
        fradragTilhører = this.fradragTilhører.toString(),
        eksterntBeløpEtterRegulering = this.eksterntBeløpEtterRegulering.toString(),
        forventetBeløpEtterRegulering = this.forventetBeløpEtterRegulering.toString(),
    )

    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement -> ÅrsakTilManuellReguleringJson.BrukerManglerSupplement(
        begrunnelse = this.begrunnelse,
        fradragstype = this.fradragstype.kategori.toString(),
        fradragTilhører = this.fradragTilhører.toString(),
    )

    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag -> ÅrsakTilManuellReguleringJson.FinnesFlerePerioderAvFradrag(
        begrunnelse = this.begrunnelse,
        fradragstype = this.fradragstype.toString(),
        fradragTilhører = this.fradragTilhører.toString(),
    )

    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt -> ÅrsakTilManuellReguleringJson.FradragErUtenlandsinntekt(
        begrunnelse = this.begrunnelse,
        fradragstype = this.fradragstype.toString(),
        fradragTilhører = this.fradragTilhører.toString(),
    )

    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.MismatchMellomBeløpFraSupplementOgFradrag -> ÅrsakTilManuellReguleringJson.MismatchMellomBeløpFraSupplementOgFradrag(
        begrunnelse = this.begrunnelse,
        fradragstype = this.fradragstype.toString(),
        fradragTilhører = this.fradragTilhører.toString(),
        eksterntBeløpFørRegulering = this.eksterntBeløpFørRegulering.toString(),
        vårtBeløpFørRegulering = this.vårtBeløpFørRegulering.toString(),

    )

    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag -> ÅrsakTilManuellReguleringJson.SupplementHarFlereVedtaksperioderForFradrag(
        begrunnelse = this.begrunnelse,
        fradragstype = this.fradragstype.toString(),
        fradragTilhører = this.fradragTilhører.toString(),
        eksterneReguleringsvedtakperioder = this.eksterneReguleringsvedtakperioder.map { it.toJson() },
    )

    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget -> ÅrsakTilManuellReguleringJson.SupplementInneholderIkkeFradraget(
        begrunnelse = this.begrunnelse,
        fradragstype = this.fradragstype.toString(),
        fradragTilhører = this.fradragTilhører.toString(),
    )

    is ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0 -> ÅrsakTilManuellReguleringJson.ForventetInntektErStørreEnn0(
        begrunnelse = this.begrunnelse,
    )

    is ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt -> ÅrsakTilManuellReguleringJson.FradragMåHåndteresManuelt

    is ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet -> ÅrsakTilManuellReguleringJson.UtbetalingFeilet

    is ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende -> ÅrsakTilManuellReguleringJson.VedtakstidslinjeErIkkeSammenhengende(
        begrunnelse = this.begrunnelse,
    )

    is ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset -> ÅrsakTilManuellReguleringJson.YtelseErMidlertidigStanset(
        begrunnelse = this.begrunnelse,
    )

    is ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset -> ÅrsakTilManuellReguleringJson.YtelseErMidlertidigStanset(
        begrunnelse = this.begrunnelse,
    )
}.let {
    serialize(it)
}
