package no.nav.su.se.bakover.domain.regulering.beregn

import arrow.core.getOrElse
import behandling.regulering.domain.beregning.KunneIkkeBeregneRegulering
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.inneholderAvslag
import no.nav.su.se.bakover.domain.sak.hentGjeldendeUtbetaling
import satser.domain.SatsFactory
import java.time.Clock

/**
 * Finner ut om en re-beregning av reguleringen vil føre til endring i stønaden.
 * Dersom noen av vilkårene gir avslag, returnerer vi 'true',
 */
fun Sak.blirBeregningEndret(
    regulering: OpprettetRegulering,
    satsFactory: SatsFactory,
    clock: Clock,
): Boolean {
    if (regulering.inneholderAvslag()) return true

    val reguleringMedBeregning = regulering.beregn(
        satsFactory = satsFactory,
        begrunnelse = null,
        clock = clock,
    ).getOrElse {
        when (it) {
            is KunneIkkeBeregneRegulering.BeregningFeilet -> {
                throw RuntimeException("Regulering for saksnummer ${regulering.saksnummer}: Vi klarte ikke å beregne. Underliggende grunn ${it.feil}")
            }
        }
    }

    return !reguleringMedBeregning.beregning!!.getMånedsberegninger().all { månedsberegning ->
        this.hentGjeldendeUtbetaling(
            forDato = månedsberegning.periode.fraOgMed,
        ).fold(
            { false },
            { månedsberegning.getSumYtelse() == it.beløp },
        )
    }
}
