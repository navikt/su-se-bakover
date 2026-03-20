package no.nav.su.se.bakover.domain.regulering

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.absoluteValue

private val log: Logger = LoggerFactory.getLogger("Regulering")

fun utledReguleringstypeOgFradrag(
    fradrag: List<Fradragsgrunnlag>,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    omregningsfaktor: BigDecimal,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    return fradrag
        .groupBy { it.fradragstype }
        .map { (fradragstype, fradragsgrunnlag) ->
            val fradragEtterSupplementSjekk = utledReguleringstypeOgFradrag(
                eksterntRegulerteBeløp = eksterntRegulerteBeløp,
                fradragstype = fradragstype,
                originaleFradragsgrunnlag = fradragsgrunnlag.toNonEmptyList(),
                omregningsfaktor = omregningsfaktor,
            )
            fradragEtterSupplementSjekk
        }.let {
            val reguleringstype = if (it.any { it.first is Reguleringstype.MANUELL }) {
                Reguleringstype.MANUELL(
                    problemer = it.map { it.first }.filterIsInstance<Reguleringstype.MANUELL>()
                        .flatMap { it.problemer }.toSet(),
                )
            } else {
                Reguleringstype.AUTOMATISK
            }
            reguleringstype to it.flatMap { it.second }
                .sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed })
        }
}

fun utledReguleringstypeOgFradrag(
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Nel<Fradragsgrunnlag>,
    omregningsfaktor: BigDecimal,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })
    return originaleFradragsgrunnlag.groupBy { it.fradrag.tilhører }.map { (fradragtilhører, fradragsgrunnlag) ->
        utledReguleringstypeOgFradrag(
            eksterntRegulerteBeløp,
            fradragstype,
            fradragsgrunnlag.toNonEmptyList(),
            fradragtilhører,
            omregningsfaktor,
        )
    }.let {
        val reguleringstype = if (it.any { it.first is Reguleringstype.MANUELL }) {
            Reguleringstype.MANUELL(
                problemer = it.map { it.first }.filterIsInstance<Reguleringstype.MANUELL>().flatMap { it.problemer }
                    .toSet(),
            )
        } else {
            Reguleringstype.AUTOMATISK
        }
        reguleringstype to it.flatMap { it.second }
            .sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed })
    }
}

// For enkelt fradrag
fun utledReguleringstypeOgFradrag(
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Nel<Fradragsgrunnlag>,
    fradragTilhører: FradragTilhører,
    omregningsfaktor: BigDecimal,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })
    require(originaleFradragsgrunnlag.all { it.fradrag.tilhører == fradragTilhører })
    require(originaleFradragsgrunnlag.size == 1)

    if (!fradragstype.måJusteresVedGEndring) {
        return Reguleringstype.AUTOMATISK to originaleFradragsgrunnlag
    }
    if (!fradragstype.kanJusteresAutomatisk) {
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
            ),
        ) to originaleFradragsgrunnlag
    }

    val nyttFradrag = when (fradragTilhører) {
        FradragTilhører.BRUKER -> eksterntRegulerteBeløp.beløpBruker.finn(fradragstype)
        FradragTilhører.EPS -> eksterntRegulerteBeløp.beløpEps.finn(fradragstype)
    }
    return sjekkOmDifferenseForBeløper(
        nyttFradrag,
        fradragstype,
        originaleFradragsgrunnlag.first(),
        fradragTilhører,
        omregningsfaktor,
    ).let {
        it.first to nonEmptyListOf(it.second)
    }
}

/**
* Hvilken regulerte beløp som finnes vil være basert samme gjeldende vedtaksdata som fradragene disse metodene
* benytter (se [ReguleringerFraPesysService]).
*  Det vil derfor ikke forekomme avvik
**/
private fun List<RegulertBeløp>.finn(fradragstype: Fradragstype) = singleOrNull { it.fradragstype == fradragstype }
    ?: throw IllegalStateException("Fant ingen fradragstype $fradragstype for bruker")

// TODO bjg del i to...
// TODO Kan vi gjøre disse sjekkene for AAP? Omregningsfaktor gir like mye mening da?
private fun sjekkOmDifferenseForBeløper(
    nyttFradrag: RegulertBeløp,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Fradragsgrunnlag,
    fradragTilhører: FradragTilhører,
    omregningsfaktor: BigDecimal,
): Pair<Reguleringstype, Fradragsgrunnlag> {
    require(originaleFradragsgrunnlag.fradragstype == fradragstype)
    require(originaleFradragsgrunnlag.fradrag.tilhører == fradragTilhører)

    val vårtBeløpFørRegulering = BigDecimal(originaleFradragsgrunnlag.fradrag.månedsbeløp).setScale(2)
    val eksterntBeløpFørRegulering = nyttFradrag.førRegulering
    val diffFørRegulering = (eksterntBeløpFørRegulering - vårtBeløpFørRegulering.intValueExact()).absoluteValue
    // vi skal ikke akseptere differanse fra eksterne kilde og vårt beløp
    if (diffFørRegulering > 0) {
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                vårtBeløpFørRegulering = vårtBeløpFørRegulering,
                eksternBruttoBeløpFørRegulering = BigDecimal.ZERO, // TODO bjg - skal utgå
                eksternNettoBeløpFørRegulering = eksterntBeløpFørRegulering.toBigDecimal(),
                begrunnelse = "Vi forventet at beløpet skulle være $vårtBeløpFørRegulering før regulering, men det var $eksterntBeløpFørRegulering. Vi aksepterer ikke en differanse, men differansen var $diffFørRegulering",
            ),
        ) to originaleFradragsgrunnlag
    }
    val eksterntBeløpEtterRegulering = nyttFradrag.etterRegulering.toBigDecimal()

    val forventetBeløpBasertPåGverdi = (vårtBeløpFørRegulering * omregningsfaktor).setScale(2, RoundingMode.HALF_UP)

    val differanseSupplementOgForventet = eksterntBeløpEtterRegulering.subtract(forventetBeløpBasertPåGverdi).abs()

    val akseptertDifferanseEtterRegulering = BigDecimal.TEN
    if (differanseSupplementOgForventet > akseptertDifferanseEtterRegulering) {
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                forventetBeløpEtterRegulering = forventetBeløpBasertPåGverdi,
                eksternBruttoBeløpEtterRegulering = BigDecimal.ZERO, // TODO bjg - skal utgå
                eksternNettoBeløpEtterRegulering = eksterntBeløpEtterRegulering,
                vårtBeløpFørRegulering = vårtBeløpFørRegulering,
                begrunnelse = "Vi forventet at beløpet skulle være $forventetBeløpBasertPåGverdi etter regulering, men det var $eksterntBeløpEtterRegulering. Vi aksepterer en differanse på $akseptertDifferanseEtterRegulering, men den var $differanseSupplementOgForventet",
            ),
        ) to originaleFradragsgrunnlag
    }

    val oppdatertBeløpFraSupplement =
        originaleFradragsgrunnlag.oppdaterBeløpMedEksternRegulering(eksterntBeløpEtterRegulering)
    return Reguleringstype.AUTOMATISK to oppdatertBeløpFraSupplement
}
