package no.nav.su.se.bakover.domain.regulering

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode

private val log: Logger = LoggerFactory.getLogger("Regulering")

fun utledReguleringstypeOgOppdaterFradrag(
    fradrag: List<Fradragsgrunnlag>,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    omregningsfaktor: BigDecimal,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    return fradrag.map {
        utledPerFradragstypeOgTilhørende(it, eksterntRegulerteBeløp, omregningsfaktor)
    }.let {
        val reguleringstype = if (it.any { it.first is Reguleringstype.MANUELL }) {
            Reguleringstype.MANUELL(
                problemer = it.map { it.first }.filterIsInstance<Reguleringstype.MANUELL>()
                    .flatMap { it.problemer }.toSet(),
            )
        } else {
            Reguleringstype.AUTOMATISK
        }
        reguleringstype to it.map { it.second }
            .sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed })
    }
}

private fun utledPerFradragstypeOgTilhørende(
    orginaltFradrag: Fradragsgrunnlag,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    omregningsfaktor: BigDecimal,
): Pair<Reguleringstype, Fradragsgrunnlag> {
    val fradragstype = orginaltFradrag.fradragstype
    val fradragTilhører = orginaltFradrag.fradrag.tilhører

    if (!fradragstype.måJusteresVedGEndring) {
        return Reguleringstype.AUTOMATISK to orginaltFradrag
    }
    if (!fradragstype.kanJusteresAutomatisk) {
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
            ),
        ) to orginaltFradrag
    }

    val nyttFradrag = when (fradragTilhører) {
        FradragTilhører.BRUKER -> eksterntRegulerteBeløp.beløpBruker
        FradragTilhører.EPS -> eksterntRegulerteBeløp.beløpEps
    }.singleOrNull { it.fradragstype == fradragstype }
        ?: return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Vi fant ikke nøyaktig ett eksternt beløp for $fradragstype til $fradragTilhører.",
            ),
        ) to orginaltFradrag

    val manuellPåGrunnAvFeilMedEksterneBeløp = manuellPåGrunnAvDifferanseMedEksterneBeløp(
        nyttFradrag = nyttFradrag,
        fradragstype = fradragstype,
        orginaltFradrag = orginaltFradrag,
        fradragTilhører = fradragTilhører,
        omregningsfaktor = omregningsfaktor,
    )
    if (manuellPåGrunnAvFeilMedEksterneBeløp != null) {
        return manuellPåGrunnAvFeilMedEksterneBeløp to orginaltFradrag
    }

    return Reguleringstype.AUTOMATISK to
        orginaltFradrag.oppdaterBeløpMedEksternRegulering(nyttFradrag.etterRegulering)
}

private fun manuellPåGrunnAvDifferanseMedEksterneBeløp(
    nyttFradrag: RegulertBeløp,
    fradragstype: Fradragstype,
    orginaltFradrag: Fradragsgrunnlag,
    fradragTilhører: FradragTilhører,
    omregningsfaktor: BigDecimal,
): Reguleringstype.MANUELL? {
    require(orginaltFradrag.fradragstype == fradragstype)
    require(orginaltFradrag.fradrag.tilhører == fradragTilhører)

    val vårtBeløpFørRegulering = BigDecimal(orginaltFradrag.fradrag.månedsbeløp).setScale(2)
    val eksterntBeløpFørRegulering = nyttFradrag.førRegulering
    val diffFørRegulering = eksterntBeløpFørRegulering.subtract(vårtBeløpFørRegulering).abs()
    if (diffFørRegulering > BigDecimal.ZERO) {
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                vårtBeløpFørRegulering = vårtBeløpFørRegulering,
                eksternBruttoBeløpFørRegulering = BigDecimal.ZERO,
                eksternNettoBeløpFørRegulering = eksterntBeløpFørRegulering,
                begrunnelse = "Vi forventet at beløpet skulle være $vårtBeløpFørRegulering før regulering, men det var $eksterntBeløpFørRegulering. Vi aksepterer ikke en differanse, men differansen var $diffFørRegulering",
            ),
        )
    }

    val eksterntBeløpEtterRegulering = nyttFradrag.etterRegulering
    if (fradragstype == Fradragstype.Arbeidsavklaringspenger) {
        return null
    }

    val forventetBeløpBasertPåGverdi = (vårtBeløpFørRegulering * omregningsfaktor).setScale(2, RoundingMode.HALF_UP)
    val differanseSupplementOgForventet = eksterntBeløpEtterRegulering.subtract(forventetBeløpBasertPåGverdi).abs()
    val akseptertDifferanseEtterRegulering = BigDecimal.TEN

    if (differanseSupplementOgForventet > akseptertDifferanseEtterRegulering) {
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                forventetBeløpEtterRegulering = forventetBeløpBasertPåGverdi,
                eksternBruttoBeløpEtterRegulering = BigDecimal.ZERO,
                eksternNettoBeløpEtterRegulering = eksterntBeløpEtterRegulering,
                vårtBeløpFørRegulering = vårtBeløpFørRegulering,
                begrunnelse = "Vi forventet at beløpet skulle være $forventetBeløpBasertPåGverdi etter regulering, men det var $eksterntBeløpEtterRegulering. Vi aksepterer en differanse på $akseptertDifferanseEtterRegulering, men den var $differanseSupplementOgForventet",
            ),
        )
    }
    return null
}
