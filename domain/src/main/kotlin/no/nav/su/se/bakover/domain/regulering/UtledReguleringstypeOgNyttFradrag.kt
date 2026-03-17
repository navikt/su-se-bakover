package no.nav.su.se.bakover.domain.regulering

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.domain.Saksnummer
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

// TODO metode som tar i mot alle fradrag og looper?
// TODO hvor og hvordan blir eps sin SU inn her?
fun utledReguleringstypeOgFradrag(
    fradrag: List<Fradragsgrunnlag>,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    omregningsfaktor: BigDecimal,
    saksnummer: Saksnummer,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    return fradrag
        .groupBy { it.fradragstype }
        .map { (fradragstype, fradragsgrunnlag) ->
            val fradragEtterSupplementSjekk = utledReguleringstypeOgFradrag(
                // eksternSupplementRegulering = eksternSupplementRegulering,
                eksterntRegulerteBeløp = eksterntRegulerteBeløp,
                fradragstype = fradragstype,
                originaleFradragsgrunnlag = fradragsgrunnlag.toNonEmptyList(),
                // merEnn1Eps = bosituasjon.merEnn1Eps(), // TODO forsikre om at dette blir ivaretatt ved bygging av EksterntRegulerteBeløp
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

fun utledReguleringstypeOgFradrag(
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Nel<Fradragsgrunnlag>,
    omregningsfaktor: BigDecimal,
    saksnummer: Saksnummer,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    // TODO AUTO-REG-26 - Vil denne ødelegge for automatiseringsgraden?
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })
    return originaleFradragsgrunnlag.groupBy { it.fradrag.tilhører }.map { (fradragtilhører, fradragsgrunnlag) ->
        utledReguleringstypeOgFradrag(
            eksterntRegulerteBeløp,
            fradragstype,
            fradragsgrunnlag.toNonEmptyList(),
            fradragtilhører,
            omregningsfaktor,
            saksnummer,
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
    saksnummer: Saksnummer,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })
    require(originaleFradragsgrunnlag.all { it.fradrag.tilhører == fradragTilhører })

    if (!fradragstype.måJusteresVedGEndring) {
        return Reguleringstype.AUTOMATISK to originaleFradragsgrunnlag
    }
    if (!fradragstype.kanJusteresAutomatisk) {
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} kan ikke hentes automatisk",
            ),
        ) to originaleFradragsgrunnlag
    }

    // TODO hvordan vil dette skje? Ingen tilfeller i 2025, et tilfelle i 2024
    if (originaleFradragsgrunnlag.size > 1) {
        log.error("Regulering, utled type og fradrag: Vi oppdaget et fradrag som må reguleres som også finnes i Pesys-datasettet. Siden fradragsgrunnlaget vårt var delt opp i flere perioder, setter vi denne til manuelt. Saksnummer: $saksnummer")
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} er delt opp i flere perioder. Disse går foreløpig til manuell regulering.",
            ),
        ) to originaleFradragsgrunnlag
    }

    if (originaleFradragsgrunnlag.first().utenlandskInntekt != null) {
        // TODO AUTO-REG-26 er det noensinne utlandsinntekt som reguleres?
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Fradraget er utenlandsinntekt og går til manuell regulering",
            ),
        ) to originaleFradragsgrunnlag
    }

    // TODO bjg fortsatt nødvendig??
    if (fradragTilhører == FradragTilhører.EPS && eksterntRegulerteBeløp.beløpEps.size > 1) {
        log.info("Automatisk regulering med supplement: Fant mer enn 1 eps. Mer enn 1 i bosituasjon: ${eksterntRegulerteBeløp.beløpEps.size}, saksnummer: $saksnummer")
        return Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.MerEnn1Eps(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} påvirkes av samme sats/G-verdi endring som SU. Dersom en regulering involverer med enn én EPS, må den tas manuelt.",
            ),
        ) to originaleFradragsgrunnlag
    }

    val nyttFradrag = when (fradragTilhører) {
        FradragTilhører.BRUKER -> eksterntRegulerteBeløp.beløpBruker.single()
        FradragTilhører.EPS -> eksterntRegulerteBeløp.beløpEps.single() // TODO AUTO-REG-26 må filtreres på type her..
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

// TODO bjg del i to...
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

    val oppdatertBeløpFraSupplement = originaleFradragsgrunnlag.oppdaterBeløpFraSupplement(eksterntBeløpEtterRegulering)
    return Reguleringstype.AUTOMATISK to oppdatertBeløpFraSupplement
}
