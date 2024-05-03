package no.nav.su.se.bakover.domain.regulering

import arrow.core.NonEmptyList
import grunnbeløp.domain.Grunnbeløpsendring
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import satser.domain.supplerendestønad.garantipensjonsendringerHøy
import satser.domain.supplerendestønad.garantipensjonsendringerOrdinær
import satser.domain.supplerendestønad.grunnbeløpsendringer
import java.math.BigDecimal
import java.time.LocalDate

/**
 * @param virkningstidspunkt For grunnbeløp og garantipensjon er dette ofte 1. mai (loven vedtas litt senere, men får som regel tilbakevirkende kraft)
 * @param ikrafttredelse Hvilken dato loven er gyldig fra, typisk den dagen kongen i statsråd vedtar loven. Har de siste årene vært 20./21./26. mai.
 */
data class DryRunNyttGrunnbeløp(
    val virkningstidspunkt: LocalDate,
    val ikrafttredelse: LocalDate = virkningstidspunkt,
    val omregningsfaktor: BigDecimal,
    val grunnbeløp: Int,
)

/**
 * @param startDatoRegulering Måned som skal reguleres fra og med.
 * @param dryRunNyttGrunnbeløp Hvis null, brukes eksisterende [grunnbeløpsendringer]. Hvis satt, brukes denne i tillegg til eksisterende [grunnbeløpsendringer].
 * @param overrideableGrunnbeløpsendringer Brukes for å overstyre grunnbeløpsendringer. Brukes kun i tester.
 */
data class StartAutomatiskReguleringForInnsynCommand(
    val gjeldendeSatsFra: LocalDate,
    val startDatoRegulering: Måned,
    val dryRunNyttGrunnbeløp: DryRunNyttGrunnbeløp?,
    val supplement: Reguleringssupplement,
    val overrideableGrunnbeløpsendringer: NonEmptyList<Grunnbeløpsendring> = grunnbeløpsendringer,
) {
    val satsFactory: SatsFactoryForSupplerendeStønad by lazy {
        SatsFactoryForSupplerendeStønad(
            grunnbeløpsendringer = if (this.dryRunNyttGrunnbeløp == null) {
                overrideableGrunnbeløpsendringer
            } else {
                overrideableGrunnbeløpsendringer + Grunnbeløpsendring(
                    virkningstidspunkt = this.dryRunNyttGrunnbeløp.virkningstidspunkt,
                    ikrafttredelse = this.dryRunNyttGrunnbeløp.ikrafttredelse,
                    verdi = this.dryRunNyttGrunnbeløp.grunnbeløp,
                    omregningsfaktor = this.dryRunNyttGrunnbeløp.omregningsfaktor,
                )
            },
            garantipensjonsendringerOrdinær = garantipensjonsendringerOrdinær,
            garantipensjonsendringerHøy = garantipensjonsendringerHøy,
        )
    }
}
