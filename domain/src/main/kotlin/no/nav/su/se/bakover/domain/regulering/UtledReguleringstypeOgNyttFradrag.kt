package no.nav.su.se.bakover.domain.regulering

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.absoluteValue

private val log: Logger = LoggerFactory.getLogger("Regulering")

// TODO doc
// TODO forenkle trekke ut maps for manuell som repeteres på alle nivå?
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
        FradragTilhører.BRUKER -> eksterntRegulerteBeløp.beløpBruker.finn(fradragstype)
        FradragTilhører.EPS -> eksterntRegulerteBeløp.beløpEps.finn(fradragstype)
    }

    val manuellPåGrunnAvFeilMedEksterneBeløp = manuellPåGrunnAvFeilMedEksterneBeløp(
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
        orginaltFradrag.oppdaterBeløpMedEksternRegulering(nyttFradrag.etterRegulering.toBigDecimal())
}

/**
 * Hvilken regulerte beløp som finnes vil være basert samme gjeldende vedtaksdata som fradragene disse metodene
 * benytter (se [ReguleringerFraPesysService]).
 *  Det vil derfor ikke forekomme avvik
 **/
private fun List<RegulertBeløp>.finn(fradragstype: Fradragstype) = singleOrNull { it.fradragstype == fradragstype }
    ?: throw IllegalStateException("Fant ingen fradragstype $fradragstype for bruker")

/**
 * Utleder reguleringstype basert på sammenligning av våre beløp og eksterne beløp.
 * Sjekker differanse før og etter regulering mot aksepterte grenser.
 *
 * @param nyttFradrag Regulert beløp fra eksternt system
 * @param fradragstype Type fradrag som skal sjekkes
 * @param orginaltFradrag Eksisterende fradragsgrunnlag
 * @param fradragTilhører Hvem fradraget tilhører (bruker eller EPS)
 * @param omregningsfaktor Faktor for omregning basert på G-verdi endring
 * @return Reguleringstype (AUTOMATISK eller MANUELL med årsak)
 */
private fun manuellPåGrunnAvFeilMedEksterneBeløp(
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
    val diffFørRegulering = (eksterntBeløpFørRegulering - vårtBeløpFørRegulering.intValueExact()).absoluteValue

    // Vi skal ikke akseptere differanse fra eksterne kilde og vårt beløp
    if (diffFørRegulering > 0) {
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                vårtBeløpFørRegulering = vårtBeløpFørRegulering,
                eksternBruttoBeløpFørRegulering = BigDecimal.ZERO,
                eksternNettoBeløpFørRegulering = eksterntBeløpFørRegulering.toBigDecimal(),
                begrunnelse = "Vi forventet at beløpet skulle være $vårtBeløpFørRegulering før regulering, men det var $eksterntBeløpFørRegulering. Vi aksepterer ikke en differanse, men differansen var $diffFørRegulering",
            ),
        )
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
                eksternBruttoBeløpEtterRegulering = BigDecimal.ZERO,
                eksternNettoBeløpEtterRegulering = eksterntBeløpEtterRegulering,
                vårtBeløpFørRegulering = vårtBeløpFørRegulering,
                begrunnelse = "Vi forventet at beløpet skulle være $forventetBeløpBasertPåGverdi etter regulering, men det var $eksterntBeløpEtterRegulering. Vi aksepterer en differanse på $akseptertDifferanseEtterRegulering, men den var $differanseSupplementOgForventet",
            ),
        )
    }
    return null
}
