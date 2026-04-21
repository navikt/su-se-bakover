package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.domain.Sak
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

/**
 * Utleder reguleringstype (automatisk/manuell) basert på fradrag brukt fra vedtaksdata og oppdaterer
 * dem med regulerte beløper hentet fra ekstern kilde.
 *
 * @param fradrag Liste med fradragsgrunnlag fra vedtaksdata
 * @param eksterntRegulerteBeløp inneholder beløp før og etter regulering
 *          for både bruker og ektefelle/samboer (EPS)
 *
 * @return Par som inneholder:
 *         - Først: Reguleringstype (AUTOMATISK hvis alle fradrag kan behandles automatisk,
 *           eller MANUELL med et sett av årsaker hvis manuell behandling er nødvendig)
 *         - Andre: Liste med fradragsgrunnlag, oppdatert med nye beløp der ekstern regulering
 *           var tilgjengelig og gyldig, sortert etter periode
 *          Eller feiltype [Sak.KanIkkeRegulere.MåRevurdere] hvis det er differanse mellom vårt og eksternt beløp
 *
 * ## Reguleringslogikk:
 * - **Automatisk regulering** skjer når:
 *   - Eksternt regulerte beløp stemmer overens med våre beløp før regulering
 *   - Alle fradrag har tilhørende eksterne data
 *
 * - **Manuell regulering** er påkrevd når:
 *   - En fradragstype ikke kan justeres automatisk fordi vi ikke har en automatisk kilde/integrasjon (f.eks. Kvalifiseringsstønad)
 *   - Det er en differanse i beløp før regulering
 *   - Eksterne reguleringsdata mangler for et fradrag
 *
 * @see Reguleringstype
 * @see ÅrsakTilManuellRegulering
 * @see måRevurderePåGrunnAvDifferanseMedEksterneBeløp
 */
fun utledReguleringstypeOgOppdaterFradrag(
    fradrag: List<Fradragsgrunnlag>,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
): Either<Sak.KanIkkeRegulere.MåRevurdere, Pair<Reguleringstype, List<Fradragsgrunnlag>>> {
    val utledetReguleringstypePerFradrag = fradrag.map {
        utledPerFradragstypeOgTilhørende(it, eksterntRegulerteBeløp)
    }
    if (utledetReguleringstypePerFradrag.any { it.isLeft() }) {
        return Sak.KanIkkeRegulere.MåRevurdere(
            årsak = Sak.KanIkkeRegulere.MåRevurdere.Årsak.DIFFERANSE_MED_EKSTERNE_BELØP,
            diffBeløp = utledetReguleringstypePerFradrag.filterLefts(),
        ).left()
    }
    return utledetReguleringstypePerFradrag.filterRights().let {
        val reguleringstype = if (it.any { it.first is Reguleringstype.MANUELL }) {
            Reguleringstype.MANUELL(
                problemer = it.map { it.first }.filterIsInstance<Reguleringstype.MANUELL>()
                    .flatMap { it.problemer }.toSet(),
            )
        } else {
            Reguleringstype.AUTOMATISK
        }
        val reguleringstypeOgOppdatertFradrag = reguleringstype to it.map { it.second }
            .sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed })
        reguleringstypeOgOppdatertFradrag.right()
    }
}

private fun utledPerFradragstypeOgTilhørende(
    originaltFradrag: Fradragsgrunnlag,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
): Either<Sak.KanIkkeRegulere.MåRevurdere.BeløperMedDiff, Pair<Reguleringstype, Fradragsgrunnlag>> {
    val fradragstype = originaltFradrag.fradragstype
    val fradragTilhører = originaltFradrag.fradrag.tilhører

    val fradragHarIkkeGrunnbeløp = !fradragstype.måJusteresVedGEndring
    val erUtenlandskInntekt = originaltFradrag.utenlandskInntekt != null
    if (fradragHarIkkeGrunnbeløp || erUtenlandskInntekt) {
        return (Reguleringstype.AUTOMATISK to originaltFradrag).right()
    }
    if (!fradragstype.kanJusteresAutomatisk) {
        return (
            Reguleringstype.MANUELL(
                ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag(
                    fradragskategori = fradragstype.kategori,
                    fradragTilhører = fradragTilhører,
                ),
            ) to originaltFradrag
            ).right()
    }

    if (fradragstype.kategori == Fradragstype.Kategori.SupplerendeStønad) {
        return (Reguleringstype.AUTOMATISK to originaltFradrag).right()
    }

    val eksterntBeløp = when (fradragTilhører) {
        FradragTilhører.BRUKER -> eksterntRegulerteBeløp.beløpBruker.finn(fradragstype)
        FradragTilhører.EPS -> eksterntRegulerteBeløp.beløpEps.finn(fradragstype)
    }

    måRevurderePåGrunnAvDifferanseMedEksterneBeløp(eksterntBeløp, originaltFradrag)?.let {
        return it.left()
    }

    return (Reguleringstype.AUTOMATISK to originaltFradrag.oppdaterBeløpMedEksternRegulering(eksterntBeløp.etterRegulering)).right()
}

/**
 * Hvilken beløp fra Pesys som er hentet vil være basert på den samme listen med fradrag som mottas i disse metodene
 * (se [ReguleringerFraPesysService]). Det vil derfor ikke forekomme avvik
 **/
private fun List<RegulertBeløp>.finn(fradragstype: Fradragstype) =
    singleOrNull { it.fradragstype == EksterntBeløpSomFradragstype.from(fradragstype) }
        ?: throw IllegalStateException("Fant ingen fradragstype $fradragstype for bruker")

/**
 * Utleder reguleringstype basert på sammenligning av våre beløp og eksterne beløp.
 * Sjekker differanse før og etter regulering mot aksepterte grenser.
 *
 * @param eksterntBeløp Regulert beløp fra eksternt system
 * @param originaltFradrag Eksisterende fradragsgrunnlag
 * @return Sak.KanIkkeRegulere.MåRevurdere.DiffBeløp eller null
 */
private fun måRevurderePåGrunnAvDifferanseMedEksterneBeløp(
    eksterntBeløp: RegulertBeløp,
    originaltFradrag: Fradragsgrunnlag,
): Sak.KanIkkeRegulere.MåRevurdere.BeløperMedDiff? {
    val vårtBeløpFørRegulering = BigDecimal(originaltFradrag.fradrag.månedsbeløp).setScale(2)
    val eksterntBeløpFørRegulering = eksterntBeløp.førRegulering
    val diffFørRegulering = (eksterntBeløpFørRegulering - vårtBeløpFørRegulering).abs()

    if (diffFørRegulering > BigDecimal.ZERO) {
        return Sak.KanIkkeRegulere.MåRevurdere.BeløperMedDiff.Fradrag(
            fradragstype = originaltFradrag.fradragstype,
            tilhører = originaltFradrag.tilhører,
            eksisterendeBeløp = vårtBeløpFørRegulering,
            nyttBeløp = eksterntBeløpFørRegulering,
        )
    }

    return null
}
