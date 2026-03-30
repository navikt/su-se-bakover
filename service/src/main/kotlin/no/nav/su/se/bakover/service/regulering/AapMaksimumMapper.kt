package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.AapGrunnlag
import no.nav.su.se.bakover.domain.regulering.BeregnAap.AapBeregning
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import vilkår.inntekt.domain.grunnlag.Fradragstype

fun tilRegulertAapBeløp(
    fnr: Fnr,
    førRegulering: AapBeregning,
    etterRegulering: AapBeregning,
): RegulertBeløp {
    return RegulertBeløp(
        fnr = fnr,
        fradragstype = Fradragstype.Arbeidsavklaringspenger,
        førRegulering = førRegulering.sats,
        etterRegulering = etterRegulering.sats,
        grunnlagAap = AapGrunnlag(
            førRegulering,
            etterRegulering,
        ),
    )
}
