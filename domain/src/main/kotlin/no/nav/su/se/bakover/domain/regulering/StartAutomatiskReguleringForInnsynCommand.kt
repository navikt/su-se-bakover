package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.satser.garantipensjonsendringerHøy
import no.nav.su.se.bakover.domain.satser.garantipensjonsendringerOrdinær
import no.nav.su.se.bakover.domain.satser.grunnbeløpsendringer
import sats.domain.GarantipensjonFactory
import sats.domain.grunnbeløp.Grunnbeløpsendring
import java.time.LocalDate

/**
 * @param fraOgMedMåned Måned som skal reguleres fra og med.
 * @param virkningstidspunkt For grunnbeløp og garantipensjon er dette ofte 1. mai (loven vedtas litt senere, men får som regel tilbakevirkende kraft)
 * @param ikrafttredelse Hvilken dato loven er gyldig fra, typisk den dagen kongen i statsråd vedtar loven. Har de siste årene vært 20./21./26. mai.
 */
data class StartAutomatiskReguleringForInnsynCommand(
    val fraOgMedMåned: Måned,
    val virkningstidspunkt: LocalDate,
    val ikrafttredelse: LocalDate = virkningstidspunkt,
    val grunnbeløp: Int? = null,
    val garantipensjonOrdinær: Int? = null,
    val garantipensjonHøy: Int? = null,
) {

    val satsFactory: SatsFactoryForSupplerendeStønad by lazy {
        SatsFactoryForSupplerendeStønad(
            grunnbeløpsendringer = if (this.grunnbeløp == null) {
                grunnbeløpsendringer
            } else {
                grunnbeløpsendringer + Grunnbeløpsendring(
                    virkningstidspunkt = this.virkningstidspunkt,
                    ikrafttredelse = this.ikrafttredelse,
                    verdi = this.grunnbeløp,
                )
            },
            garantipensjonsendringerOrdinær = if (this.garantipensjonOrdinær == null) {
                garantipensjonsendringerOrdinær
            } else {
                garantipensjonsendringerOrdinær + GarantipensjonFactory.Garantipensjonsendring(
                    virkningstidspunkt = this.virkningstidspunkt,
                    ikrafttredelse = this.ikrafttredelse,
                    verdi = this.garantipensjonOrdinær,
                )
            },
            garantipensjonsendringerHøy = if (this.garantipensjonHøy == null) {
                garantipensjonsendringerHøy
            } else {
                garantipensjonsendringerHøy + GarantipensjonFactory.Garantipensjonsendring(
                    virkningstidspunkt = this.virkningstidspunkt,
                    ikrafttredelse = this.ikrafttredelse,
                    verdi = this.garantipensjonHøy,
                )
            },
        )
    }
}
