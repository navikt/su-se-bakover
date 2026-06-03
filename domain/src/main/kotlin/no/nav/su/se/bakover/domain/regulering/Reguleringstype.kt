package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.avrund
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Month

sealed interface Reguleringstype {
    data object AUTOMATISK : Reguleringstype

    data class MANUELL(val problemer: Set<ÅrsakTilManuellRegulering>) : Reguleringstype {
        constructor(årsak: ÅrsakTilManuellRegulering) : this(setOf(årsak))
    }

    companion object {
        fun utledReguleringsTypeFrom(
            reguleringstype1: Reguleringstype,
            reguleringstype2: Reguleringstype,
        ): Reguleringstype {
            if (reguleringstype1 is MANUELL || reguleringstype2 is MANUELL) {
                return MANUELL(
                    ((reguleringstype1 as? MANUELL)?.problemer ?: emptySet()) +
                        ((reguleringstype2 as? MANUELL)?.problemer ?: emptySet()),
                )
            }
            return AUTOMATISK
        }
    }
}

fun GjeldendeVedtaksdata.utledReguleringstype(): Reguleringstype {
    val problemer = mutableSetOf<ÅrsakTilManuellRegulering>()
    if (this.harStans()) {
        problemer.add(
            ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset(
                begrunnelse = "Saken er midlertidig stanset",
            ),
        )
    }

    val delerAvPeriodenErOpphør = this.delerAvPeriodenErOpphør()
    val tidslinjeForVedtakErIkkeSammenhengende = !this.tidslinjeForVedtakErSammenhengende()
    if (delerAvPeriodenErOpphør || tidslinjeForVedtakErIkkeSammenhengende) {
        problemer.add(
            ÅrsakTilManuellRegulering.UgyldigePerioderForAutomatiskRegulering(
                begrunnelse = if (delerAvPeriodenErOpphør) {
                    "Saken inneholder opphørte perioder ${this.opphørtePerioderSlåttSammen()}. Disse er innenfor reguleringsperioden"
                } else {
                    "Reguleringsperioden inneholder hull. Vi støtter ikke hull i vedtakene p.t."
                },
            ),
        )
    }

    return if (problemer.isNotEmpty()) Reguleringstype.MANUELL(problemer) else Reguleringstype.AUTOMATISK
}

/**
 * Utleder reguleringstype (automatisk/manuell) basert på fradrag brukt fra vedtaksdata og oppdaterer
 * dem med regulerte beløper hentet fra ekstern kilde.
 *
 * @param fradrag Liste med fradragsgrunnlag fra vedtaksdata (krever at det er vedtaksdata fra og med 1. mai)
 * @param eksterntRegulerteBeløp inneholder beløp før og etter regulering
 *          for både bruker og ektefelle/samboer (EPS)
 *
 * @return Par som inneholder:
 *         - Først: Reguleringstype (AUTOMATISK hvis alle fradrag kan behandles automatisk,
 *           eller MANUELL med et sett av årsaker hvis manuell behandling er nødvendig)
 *         - Andre: Liste med fradragsgrunnlag, oppdatert med nye beløp der ekstern regulering
 *           var tilgjengelig og gyldig, sortert etter periode
 *          Eller feiltype [ÅrsakRevurdering] hvis det er differanse mellom vårt og eksternt beløp
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
): Either<ÅrsakRevurdering, Pair<Reguleringstype, List<Fradragsgrunnlag>>> {
    if (fradrag.any { it.periode.fraOgMed.month < Month.MAY }) {
        throw IllegalArgumentException("Regulering skal ikke kjøres med vedtaksdata før mai året reguleringen kjører i")
    }
    val utledetReguleringstypePerFradrag = fradrag.filter { it.periode.fraOgMed.month >= Month.MAY }.map {
        utledPerFradragstypeOgTilhørende(it, eksterntRegulerteBeløp)
    }
    if (utledetReguleringstypePerFradrag.any { it.isLeft() }) {
        return ÅrsakRevurdering(
            årsak = ÅrsakRevurdering.Årsak.DIFFERANSE_MED_EKSTERNE_BELØP,
            diffBeløp = utledetReguleringstypePerFradrag.filterLefts(),
        ).left()
    }
    return utledetReguleringstypePerFradrag.filterRights().let {
        val utledetReguleringstypeForAlleFradrag = it.map { it.first }
        val reguleringstype =
            if (utledetReguleringstypeForAlleFradrag.any { it is Reguleringstype.MANUELL }) {
                Reguleringstype.MANUELL(
                    problemer = (
                        utledetReguleringstypeForAlleFradrag
                            .filterIsInstance<Reguleringstype.MANUELL>()
                        )
                        .flatMap { it.problemer }.toSet(),
                )
            } else {
                Reguleringstype.AUTOMATISK
            }

        val oppdaterteFradrag = it.map { it.second }
            .sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed })
        (reguleringstype to oppdaterteFradrag).right()
    }
}

private fun utledPerFradragstypeOgTilhørende(
    originaltFradrag: Fradragsgrunnlag,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
): Either<ÅrsakRevurdering.BeløperMedDiff, Pair<Reguleringstype, Fradragsgrunnlag>> {
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
        // Hvis eps har supplerende stønad vil samlet fradrag for eps alltid være under fribeløp og vi trenger ikke håndtere det manuelt
        return (Reguleringstype.AUTOMATISK to originaltFradrag).right()
    }

    // Vi henter ikke fremtidige fradrag eksternt per nå så disse må tas manuelt
    if (originaltFradrag.periode.fraOgMed.month > Month.MAY) {
        return (
            Reguleringstype.MANUELL(
                problemer = setOf(ÅrsakTilManuellRegulering.EtAutomatiskFradragHarFremtidigPeriode()),
            ) to originaltFradrag
            ).right()
    }

    val eksterntBeløp = when (fradragTilhører) {
        FradragTilhører.BRUKER -> eksterntRegulerteBeløp.beløpBruker.finn(fradragstype)
        FradragTilhører.EPS -> eksterntRegulerteBeløp.beløpEps.finn(fradragstype)
    }

    return when (
        sammenlignVårtBeløpMedEksternt(
            vårtBeløp = BigDecimal(originaltFradrag.fradrag.månedsbeløp).setScale(2),
            eksterntBeløp = eksterntBeløp,
        )
    ) {
        // Vårt beløp er allerede regulert (matcher etterRegulering). Saken kjøres som ny
        // SU-regulering, men selve fradragsbeløpet endres ikke.
        EksterntRegulertSammenligningResultat.HarGRegulertFradragEksternt ->
            (Reguleringstype.AUTOMATISK to originaltFradrag).right()
        // Vårt beløp matcher Pesys førRegulering. Oppdater fradraget til etterRegulering og reguler automatisk.
        EksterntRegulertSammenligningResultat.NormalRegulering ->
            (Reguleringstype.AUTOMATISK to originaltFradrag.oppdaterBeløpMedEksternRegulering(eksterntBeløp.etterRegulering)).right()
        // Vårt beløp matcher hverken før- eller etter-beløp fra Pesys. Saken må håndteres manuelt.
        EksterntRegulertSammenligningResultat.Differanse,
        -> ÅrsakRevurdering.BeløperMedDiff.Fradrag(
            fradragstype = originaltFradrag.fradragstype,
            tilhører = originaltFradrag.tilhører,
            eksisterendeBeløp = BigDecimal(originaltFradrag.fradrag.månedsbeløp).setScale(2),
            nyttBeløp = eksterntBeløp.førRegulering ?: eksterntBeløp.etterRegulering,
        ).left()
    }
}

/**
 * Hvilken beløp fra Pesys som er hentet vil være basert på den samme listen med fradrag som mottas i disse metodene
 * (se [ReguleringerFraPesysService]). Det vil derfor ikke forekomme avvik
 **/
private fun List<RegulertBeløp>.finn(fradragstype: Fradragstype) =
    singleOrNull { it.fradragstype == EksterntBeløpSomFradragstype.from(fradragstype) }
        ?: throw IllegalStateException("Fant ingen fradragstype $fradragstype for bruker")

/**
 * Sammenligner vårt eksisterende månedsbeløp mot et eksternt regulert beløp (fra Pesys eller AAP).
 * Skiller eksplisitt mellom de forskjellige use casene en sak kan være i.
 *
 * Brukes både for fradragsgrunnlag (i [utledPerFradragstypeOgTilhørende]) og for inntekt-etter-uføre
 * (IEU)-vilkåret. Hver caller har sine egne metadata (fradragstype, tilhører) som settes på
 * eventuell [EksterntRegulertSammenligningResultat.Differanse] av kallstedet.
 *
 * Vi kan stole på at [RegulertBeløp.etterRegulering] alltid er beregnet med nytt grunnbeløp —
 * dette valideres ved konstruksjon (se `finnRegulertPesysVedtak` for Pesys og tilsvarende for AAP).
 */
internal fun sammenlignVårtBeløpMedEksternt(
    vårtBeløp: BigDecimal,
    eksterntBeløp: RegulertBeløp,
): EksterntRegulertSammenligningResultat {
    val eksterntFør = eksterntBeløp.førRegulering

    if (vårtBeløp.avrund().compareTo(eksterntBeløp.etterRegulering.avrund()) == 0) {
        // Vårt beløp er allerede beregnet med nytt G (matcher etterRegulering). Dekker både
        // ekstern førstegangsinnvilgelse (eksterntFør == null) og tilfeller der saksbehandler
        // allerede har lagt inn nytt beløp. I begge tilfeller skal beløpet beholdes.
        return EksterntRegulertSammenligningResultat.HarGRegulertFradragEksternt
    }

    if (eksterntFør != null) {
        // Vårt beløp er beregnet med gammelt G — normal regulering. Oppdater til etter-beløpet.
        when (eksterntBeløp.fradragstype) {
            EksterntBeløpSomFradragstype.Arbeidsavklaringspenger ->
                if (vårtBeløp.avrund().compareTo(eksterntBeløp.førRegulering.avrund()) == 0) {
                    return EksterntRegulertSammenligningResultat.NormalRegulering
                }

            EksterntBeløpSomFradragstype.Alderspensjon,
            EksterntBeløpSomFradragstype.Uføretrygd,
            EksterntBeløpSomFradragstype.ForventetInntekt,
            ->
                // Vi tolerer en diff på under 10 kroner fordi pesys gjør avrundinger som ikke hensynstas i det som hentes fra api
                if (vårtBeløp.setScale(0, RoundingMode.HALF_UP)
                        .minus(eksterntBeløp.førRegulering.setScale(0, RoundingMode.HALF_UP))
                        .abs() <= BigDecimal.TEN
                ) {
                    return EksterntRegulertSammenligningResultat.NormalRegulering
                }
        }
    }

    return if (eksterntFør == null) {
        // Pesys svarer med kun etter-periode er NY G som er ulikt vårt registrerte fradragsbeløp og kan da bli automatisk fordi det er en periode og det "etter" periode.
        EksterntRegulertSammenligningResultat.NormalRegulering
    } else {
        // Vårt beløp matcher hverken før eller etter — rapporter differanse for manuell håndtering.
        EksterntRegulertSammenligningResultat.Differanse
    }
}

/**
 * Resultat av å sammenligne vårt SU-beløp mot eksternt regulert beløp (Pesys eller AAP).
 *
 * Vi bryr oss kun om hvilket grunnbeløp vårt beløp er beregnet med:
 * - [HarGRegulertFradragEksternt]: vårt beløp = etterRegulering (allerede ny G oppdatert fradrag hos oss). Reguleres
 *   automatisk uten endring av beløpet. Dekker både ekstern førstegangsinnvilgelse og tilfeller
 *   der saksbehandler allerede har lagt inn nytt beløp.
 * - [NormalRegulering]: vårt beløp = førRegulering (gammelt G), oppdater til etterRegulering.
 * - [Differanse]: vårt beløp matcher hverken før eller etter. Kallstedet konstruerer differansen
 *   med egne metadata (fradragstype, tilhører) og rapporterer manuell håndtering.
 */
internal enum class EksterntRegulertSammenligningResultat {
    HarGRegulertFradragEksternt,
    NormalRegulering,
    Differanse,
}
