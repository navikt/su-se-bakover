package no.nav.su.se.bakover.domain.regulering

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.bosituasjon.domain.grunnlag.merEnn1Eps
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.absoluteValue

private val log: Logger = LoggerFactory.getLogger("Regulering")

// TODO AUTO-REG-26 gammel utledning hele filen skal slettes!

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

/**
 * Siden parameterene er gruppert på fradragstype, kan de tilhøre både ektefelle og bruker.
 *
 * @throws IllegalStateException Dersom typene til fradragsgrunnlagene ikke er den samme som [fradragstype]
 */
fun utledReguleringstypeOgFradrag(
    eksternSupplementRegulering: EksternSupplementRegulering,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Nel<Fradragsgrunnlag>,
    merEnn1Eps: Boolean,
    omregningsfaktor: BigDecimal,
    saksnummer: Saksnummer,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })

    return originaleFradragsgrunnlag.groupBy { it.fradrag.tilhører }.map { (fradragtilhører, fradragsgrunnlag) ->
        utledReguleringstypeOgFradragIndre(
            eksternSupplementRegulering,
            fradragstype,
            fradragsgrunnlag.toNonEmptyList(),
            fradragtilhører,
            merEnn1Eps,
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
        reguleringstype to it.flatMap { it.second }.sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed })
    }
}

/**
 * Siden parameterene er gruppert på fradragstype, kan de tilhøre både ektefelle og bruker.
 *
 * @throws IllegalStateException Dersom typene til fradragsgrunnlagene ikke er den samme som [fradragstype]
 * @throws IllegalStateException Dersom fradragsgrunnlagene ikke matcher med [fradragTilhører]
 */
fun utledReguleringstypeOgFradragIndre(
    eksternSupplementRegulering: EksternSupplementRegulering,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Nel<Fradragsgrunnlag>,
    fradragTilhører: FradragTilhører,
    merEnn1Eps: Boolean,
    omregningsfaktor: BigDecimal,
    saksnummer: Saksnummer,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })
    require(originaleFradragsgrunnlag.all { it.fradrag.tilhører == fradragTilhører })

    if (!fradragstype.måJusteresManueltVedGEndring) {
        return Reguleringstype.AUTOMATISK to originaleFradragsgrunnlag
    }

    // TODO bjg - Vil ikke dette være håndterbart???
    if (originaleFradragsgrunnlag.size > 1) {
        log.error("Regulering, utled type og fradrag: Vi oppdaget et fradrag som må reguleres som også finnes i Pesys-datasettet. Siden fradragsgrunnlaget vårt var delt opp i flere perioder, setter vi denne til manuelt. Saksnummer: $saksnummer")
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} er delt opp i flere perioder. Disse går foreløpig til manuell regulering.",
            ),
        ) to originaleFradragsgrunnlag
    }

    return utledReguleringstypeOgFradragForEttFradragsgrunnlag(
        eksternSupplementRegulering,
        fradragstype,
        originaleFradragsgrunnlag.first(),
        fradragTilhører,
        merEnn1Eps,
        omregningsfaktor,
        saksnummer,
    ).let {
        it.first to nonEmptyListOf(it.second)
    }
}

/**
 * Siden parameterene er gruppert på fradragstype, kan de tilhøre både ektefelle og bruker.
 *
 * @throws IllegalStateException Dersom typene til fradragsgrunnlagene ikke er den samme som [fradragstype]
 * @throws IllegalStateException Dersom fradragsgrunnlagene ikke matcher med [fradragTilhører]
 */
private fun utledReguleringstypeOgFradragForEttFradragsgrunnlag(
    supplement: EksternSupplementRegulering,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Fradragsgrunnlag,
    fradragTilhører: FradragTilhører,
    merEnn1Eps: Boolean,
    omregningsfaktor: BigDecimal,
    saksnummer: Saksnummer,
): Pair<Reguleringstype, Fradragsgrunnlag> {
    require(originaleFradragsgrunnlag.fradragstype == fradragstype)
    require(originaleFradragsgrunnlag.fradrag.tilhører == fradragTilhører)

    if (originaleFradragsgrunnlag.utenlandskInntekt != null) {
        // TODO bjg er det noensinne utlandsinntekt som reguleres?
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Fradraget er utenlandsinntekt og går til manuell regulering",
            ),
        ) to originaleFradragsgrunnlag
    }

    if (
        (supplement.bruker == null && fradragTilhører == FradragTilhører.BRUKER) ||
        (supplement.eps.isEmpty() && fradragTilhører == FradragTilhører.EPS)
    ) {
        // Dette er den vanligste casen for manuell regulering, vi trenger ikke logge disse tilfellene.
        val regtype =
            if (fradragstype.måJusteresManueltVedGEndring) {
                manuellReg(
                    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement(
                        fradragskategori = fradragstype.kategori,
                        fradragTilhører = fradragTilhører,
                        begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} påvirkes av samme sats/G-verdi endring som SU. Vi mangler supplement for dette fradraget og derfor går det til manuell regulering.",
                    ),
                )
            } else {
                Reguleringstype.AUTOMATISK
            }
        return regtype to originaleFradragsgrunnlag
    }

    val supplementFor: ReguleringssupplementFor = when (fradragTilhører) {
        FradragTilhører.BRUKER -> {
            supplement.bruker ?: return manuellReg(
                ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement(
                    fradragskategori = fradragstype.kategori,
                    fradragTilhører = fradragTilhører,
                    begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} påvirkes av samme sats/G-verdi endring som SU. Vi mangler supplement for dette fradraget og derfor går det til manuell regulering.",
                ),
            ) to originaleFradragsgrunnlag
        }

        FradragTilhører.EPS -> {
            if (supplement.eps.isEmpty()) {
                return manuellReg(
                    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement(
                        fradragskategori = fradragstype.kategori,
                        fradragTilhører = fradragTilhører,
                        begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} påvirkes av samme sats/G-verdi endring som SU. Vi mangler supplement for dette fradraget og derfor går det til manuell regulering.",
                    ),
                ) to originaleFradragsgrunnlag
            }

            if (merEnn1Eps || supplement.eps.size > 1) {
                log.info("Automatisk regulering med supplement: Fant mer enn 1 eps. Mer enn 1 i bosituasjon: $merEnn1Eps, antall eps fra supplement: ${supplement.eps.size}, saksnummer: $saksnummer")
                return manuellReg(
                    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.MerEnn1Eps(
                        fradragskategori = fradragstype.kategori,
                        fradragTilhører = fradragTilhører,
                        begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} påvirkes av samme sats/G-verdi endring som SU. Dersom en regulering involverer med enn én EPS, må den tas manuelt.",
                    ),
                ) to originaleFradragsgrunnlag
            }
            supplement.eps.single()
        }
    }

    val supplementForType: ReguleringssupplementFor.PerType =
        // TODO bjg - bør feile tidligere??
        supplementFor.getForType(fradragstype) ?: return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Vi fant et supplement for $fradragTilhører, men ikke for ${fradragstype.kategori}.",
            ),
        ) to originaleFradragsgrunnlag
    val antallEksterneReguleringsvedtak = supplementForType.reguleringsvedtak.size
    if (antallEksterneReguleringsvedtak > 1) {
        // TODO bjg - Ikke lenger relevant da vi vil uansett hente bare 1?
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                eksterneReguleringsvedtakperioder = supplementForType.reguleringsvedtak.map { it.periode },
                begrunnelse = "Vi fant et supplement for $fradragTilhører og denne ${fradragstype.kategori}, men siden vi fant mer enn en vedtaksperiode ($antallEksterneReguleringsvedtak), går den til manuell.",
            ),
        ) to originaleFradragsgrunnlag
    }
    val vårtBeløpFørRegulering = BigDecimal(originaleFradragsgrunnlag.fradrag.månedsbeløp).setScale(2)
    val endringsvedtak = supplementForType.endringsvedtak ?: return manuellReg(
        // TODO bjg - bør feile tidligere??
        ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FantIkkeVedtakForApril(
            fradragskategori = fradragstype.kategori,
            fradragTilhører = fradragTilhører,
            begrunnelse = "Vi fant et supplement for $fradragTilhører og denne ${fradragstype.kategori} - men vi fant ikke et eksternt endringsvedtak for april for å regne ut reguleringen.",
        ),
    ) to originaleFradragsgrunnlag
    val eksterntBeløpFørRegulering = endringsvedtak.beløp
    val diffFørRegulering = (eksterntBeløpFørRegulering - vårtBeløpFørRegulering.intValueExact()).absoluteValue
    // vi skal ikke akseptere differanse fra eksterne kilde og vårt beløp
    if (diffFørRegulering > 0) {
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                vårtBeløpFørRegulering = vårtBeløpFørRegulering,
                eksternBruttoBeløpFørRegulering = endringsvedtak.bruttoBeløpFraMetadata().toBigDecimal(),
                eksternNettoBeløpFørRegulering = eksterntBeløpFørRegulering.toBigDecimal(),
                begrunnelse = "Vi forventet at beløpet skulle være $vårtBeløpFørRegulering før regulering, men det var $eksterntBeløpFørRegulering. Vi aksepterer ikke en differanse, men differansen var $diffFørRegulering",
            ),
        ) to originaleFradragsgrunnlag
    }
    val eksterntBeløpEtterRegulering = supplementForType.reguleringsvedtak.single().beløp.toBigDecimal()

    val forventetBeløpBasertPåGverdi = (vårtBeløpFørRegulering * omregningsfaktor).setScale(2, RoundingMode.HALF_UP)

    val differanseSupplementOgForventet = eksterntBeløpEtterRegulering.subtract(forventetBeløpBasertPåGverdi).abs()

    // TODO hva skjeeer her egt????
    // TODO skal vi anta hva deres regulerte beløp skal være? Eller bare en "kontroll"/sanitycheck??
    val akseptertDifferanseEtterRegulering = BigDecimal.TEN
    if (differanseSupplementOgForventet > akseptertDifferanseEtterRegulering) {
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                forventetBeløpEtterRegulering = forventetBeløpBasertPåGverdi,
                eksternBruttoBeløpEtterRegulering = supplementForType.reguleringsvedtak.single()
                    .bruttoBeløpFraMetadata().toBigDecimal(),
                eksternNettoBeløpEtterRegulering = eksterntBeløpEtterRegulering,
                vårtBeløpFørRegulering = vårtBeløpFørRegulering,
                begrunnelse = "Vi forventet at beløpet skulle være $forventetBeløpBasertPåGverdi etter regulering, men det var $eksterntBeløpEtterRegulering. Vi aksepterer en differanse på $akseptertDifferanseEtterRegulering, men den var $differanseSupplementOgForventet",

            ),
        ) to originaleFradragsgrunnlag
    }

    val oppdatertBeløpFraSupplement = originaleFradragsgrunnlag.oppdaterBeløpFraSupplement(eksterntBeløpEtterRegulering)
    return Reguleringstype.AUTOMATISK to oppdatertBeløpFraSupplement
}

private fun manuellReg(årsak: ÅrsakTilManuellRegulering): Reguleringstype.MANUELL =
    Reguleringstype.MANUELL(setOf(årsak))
