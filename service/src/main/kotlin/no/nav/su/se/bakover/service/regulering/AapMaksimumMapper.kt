package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.AapGrunnlag
import no.nav.su.se.bakover.domain.regulering.BeregnAap.AapBeregning
import no.nav.su.se.bakover.domain.regulering.EksterntBeløpSomFradragstype
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import java.time.LocalDate

fun tilRegulertAapBeløp(
    fnr: Fnr,
    førRegulering: AapBeregning,
    etterRegulering: AapBeregning,
    etterReguleringFraOgMed: LocalDate,
): RegulertBeløp {
    return RegulertBeløp(
        fnr = fnr,
        fradragstype = EksterntBeløpSomFradragstype.Arbeidsavklaringspenger,
        førRegulering = førRegulering.sats,
        etterRegulering = etterRegulering.sats,
        etterReguleringFraOgMed = etterReguleringFraOgMed,
        grunnlagAap = AapGrunnlag(
            førRegulering,
            etterRegulering,
        ),
    )
}
