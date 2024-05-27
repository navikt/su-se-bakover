package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.bosituasjon.domain.grunnlag.merEnn1Eps
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import java.math.BigDecimal

fun utledReguleringstypeOgFradragVedHjelpAvSupplement(
    fradrag: List<Fradragsgrunnlag>,
    bosituasjon: List<Bosituasjon.Fullstendig>,
    eksternSupplementRegulering: EksternSupplementRegulering,
    omregningsfaktor: BigDecimal,
    saksnummer: Saksnummer,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    /**
     * TODO
     *  Perioden vi får inn per type vil potensielt være lenger enn våres periode, eller kortere, fordi at Pesys, legger på
     *  tilOgMed til siste dagen i året (kan være null) . Våres periode følger naturligvis stønadsperioden, som vil kunne gjelde over pesys sin tilOgMed
     *  vi kan få samme fradrag flere ganger. hull i perioden er en mulighet. kan prøve å slå sammen fradragene til 1.
     *  hvis ikke det lar seg gjøre, kan vi sette reguleringen til manuell.
     *  Eventuelt gjøre periodene om til måneder, oppdatere beløpene. Merk at samme problem stilling med perioder i pesys vs våres fortsatt gjelder.
     */
    return fradrag
        .groupBy { it.fradragstype }
        .map { (fradragstype, fradragsgrunnlag) ->
            val fradragEtterSupplementSjekk = utledReguleringstypeOgFradrag(
                eksternSupplementRegulering = eksternSupplementRegulering,
                fradragstype = fradragstype,
                originaleFradragsgrunnlag = fradragsgrunnlag.toNonEmptyList(),
                merEnn1Eps = bosituasjon.merEnn1Eps(),
                omregningsfaktor = omregningsfaktor,
                saksnummer = saksnummer,
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
            reguleringstype to it.flatMap { it.second }.sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed })
        }
}
