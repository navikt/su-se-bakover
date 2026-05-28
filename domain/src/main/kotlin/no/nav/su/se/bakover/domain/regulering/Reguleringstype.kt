package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.time.LocalDate
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
    val utledetReguleringstypePerFradrag = fradrag.filter { it.periode.fraOgMed.month == Month.MAY }.map {
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
                if (fradrag.any { it.periode.fraOgMed.month > Month.MAY }) {
                    Reguleringstype.MANUELL(
                        problemer = setOf(ÅrsakTilManuellRegulering.EtAutomatiskFradragHarFremtidigPeriode()),
                    )
                } else {
                    Reguleringstype.AUTOMATISK
                }
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

    val eksterntBeløp = when (fradragTilhører) {
        FradragTilhører.BRUKER -> eksterntRegulerteBeløp.beløpBruker.finn(fradragstype)
        FradragTilhører.EPS -> eksterntRegulerteBeløp.beløpEps.finn(fradragstype)
    }

    return when (
        sammenlignVårtBeløpMedEksternt(
            vårtBeløp = BigDecimal(originaltFradrag.fradrag.månedsbeløp).setScale(2),
            vårtBeløpOpprettet = originaltFradrag.opprettet.toLocalDate(zoneIdOslo),
            eksterntBeløp = eksterntBeløp,
        )
    ) {
        // Ekstern kilde har kun én periode (ingen førRegulering). Vårt fradrag er allerede registrert
        // med samme beløp som etterRegulering. Reguleres automatisk uten å endre fradragsbeløpet.
        EksterntRegulertSammenligningResultat.FørstegangsinnvilgelseEksternt ->
            (Reguleringstype.AUTOMATISK to originaltFradrag).right()
        // Saksbehandler har allerede oppdatert fradraget hos oss med Pesys sine nye etter-beløp før
        // SU-regulering. Saken må fortsatt reguleres (nytt SU-vedtak) med ny g, men selve fradragsbeløpet skal ikke endres her.
        EksterntRegulertSammenligningResultat.BeløpAlleredeOppdatert ->
            (Reguleringstype.AUTOMATISK to originaltFradrag).right()
        // Vårt beløp matcher Pesys førRegulering. Oppdater fradraget til etterRegulering og reguler automatisk.
        EksterntRegulertSammenligningResultat.NormalRegulering ->
            (Reguleringstype.AUTOMATISK to originaltFradrag.oppdaterBeløpMedEksternRegulering(eksterntBeløp.etterRegulering)).right()
        // Vårt beløp matcher hverken før- eller etter-beløp fra Pesys. Saken må håndteres manuelt.
        EksterntRegulertSammenligningResultat.Differanse -> ÅrsakRevurdering.BeløperMedDiff.Fradrag(
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
 */
internal fun sammenlignVårtBeløpMedEksternt(
    vårtBeløp: BigDecimal,
    vårtBeløpOpprettet: LocalDate,
    eksterntBeløp: RegulertBeløp,
): EksterntRegulertSammenligningResultat {
    val eksterntFør = eksterntBeløp.førRegulering
    val matcherEtter = vårtBeløp.compareTo(eksterntBeløp.etterRegulering) == 0

    if (eksterntFør == null && matcherEtter) {
        // Pesys har kun én periode (typisk førstegangsinnvilgelse eksternt) og vårt beløp stemmer
        // allerede med dette beløpet. Vi trenger bare én periode fra Pesys her, ikke to.
        return EksterntRegulertSammenligningResultat.FørstegangsinnvilgelseEksternt
    }
    if (eksterntFør != null &&
        matcherEtter &&
        eksterntBeløp.etterReguleringFraOgMed != null &&
        !vårtBeløpOpprettet.isBefore(eksterntBeløp.etterReguleringFraOgMed)
    ) {
        /* SU er innvilget/revurdert hos oss ETTER at Pesys-reguleringen trådte i kraft, men FØR
         SU-reguleringsjobben kjørte. Saksbehandler har da allerede lagt inn Pesys' nye beløp hos
         oss. Beløpet er korrekt og skal ikke endres, men saken må fortsatt reguleres (nytt
         SU-vedtak fattes automatisk).

         Datosjekken hindrer feilaktig treff der vårt beløp tilfeldigvis matcher etter-beløpet,
         men er opprettet før reguleringen — det skal i stedet falle gjennom til Differanse/manuell.
         */
        return EksterntRegulertSammenligningResultat.BeløpAlleredeOppdatert
    }
    if (eksterntFør != null && vårtBeløp.compareTo(eksterntFør) == 0) {
        // Vanlig case: vårt beløp = Pesys' før-beløp. Vi oppdaterer til etter-beløpet.
        return EksterntRegulertSammenligningResultat.NormalRegulering
    }
    // Vårt beløp matcher hverken før eller etter — rapporter differanse for manuell håndtering.
    return EksterntRegulertSammenligningResultat.Differanse
}

/**
 * Resultat av å sammenligne vårt SU-beløp mot eksternt regulert beløp (Pesys eller AAP).
 *
 * - [FørstegangsinnvilgelseEksternt]: ekstern kilde har kun én periode (ingen før-periode)
 *   og vårt beløp matcher etterRegulering. Bruker er nylig innvilget hos kilden og hos oss.
 *   Reguleres automatisk uten endring av beløpet.
 * - [BeløpAlleredeOppdatert]: ekstern kilde har en før-periode, vårt beløp er opprettet
 *   etter at den nye perioden trådte i kraft, og beløp matcher etterRegulering. SU er
 *   innvilget/revurdert etter ekstern regulering men før SU-regulering — saksbehandler har allerede
 *   lagt inn nytt beløp. Saken må fortsatt reguleres, men beløpet endres ikke.
 * - [NormalRegulering]: vårt beløp = førRegulering, oppdater til etterRegulering.
 * - [Differanse]: vårt beløp matcher hverken før eller etter. Kallstedet konstruerer differansen
 *   med egne metadata (fradragstype, tilhører) og rapporterer manuell håndtering.
 */
internal enum class EksterntRegulertSammenligningResultat {
    FørstegangsinnvilgelseEksternt,
    BeløpAlleredeOppdatert,
    NormalRegulering,
    Differanse,
}
