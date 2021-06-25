package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.util.UUID

val uføregrunnlagId: UUID = UUID.randomUUID()

fun uføregrunnlagForventetInntekt0(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): Grunnlag.Uføregrunnlag {
    return Grunnlag.Uføregrunnlag(
        id = uføregrunnlagId,
        opprettet = opprettet,
        periode = periode,
        uføregrad = Uføregrad.parse(100),
        forventetInntekt = 0,
    )
}

fun uføregrunnlagForventetInntekt12000(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): Grunnlag.Uføregrunnlag {
    return uføregrunnlagForventetInntekt0(
        opprettet,
        periode,
    ).copy(
        forventetInntekt = 12000,
    )
}

val uførevilkårId: UUID = UUID.randomUUID()

fun innvilgetUførevilkårForventetInntekt0(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = uførevilkårId,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlagForventetInntekt0(opprettet, periode),
                periode = periode,
                begrunnelse = "innvilgetUførevilkårForventetInntekt0",
            ),
        ),
    )
}

val formuegrunnlagId: UUID = UUID.randomUUID()

fun formueGrunnlagUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
): Formuegrunnlag {
    return Formuegrunnlag.create(
        id = formuegrunnlagId,
        opprettet = opprettet,
        periode = periode,
        epsFormue = null,
        søkersFormue = Formuegrunnlag.Verdier.create(
            verdiIkkePrimærbolig = 0,
            verdiEiendommer = 0,
            verdiKjøretøy = 0,
            innskudd = 0,
            verdipapir = 0,
            pengerSkyldt = 0,
            kontanter = 0,
            depositumskonto = 0,
        ),
        begrunnelse = null,
        bosituasjon = bosituasjon,
        behandlingsPeriode = periode,
    )
}

val formuevurderingId: UUID = UUID.randomUUID()

fun formuevilkårUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
): Vilkår.Formue {
    return Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Formue.create(
                id = formuevurderingId,
                opprettet = opprettet,
                periode = periode,
                resultat = Resultat.Innvilget,
                grunnlag = formueGrunnlagUtenEps0Innvilget(opprettet, periode, bosituasjon),
            ),
        ),
    )
}

/**
 * uføre: innvilget med forventet inntekt 0
 * bosituasjon: enslig
 * formue: ingen eps, sum 0
 */
fun vilkårsvurderingerInnvilget(
    periode: Periode = periode2021,
    uføre: Vilkår.Uførhet = innvilgetUførevilkårForventetInntekt0(periode = periode),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode),
    formue: Vilkår.Formue = formuevilkårUtenEps0Innvilget(periode = periode, bosituasjon = bosituasjon),
): Vilkårsvurderinger {
    return Vilkårsvurderinger(
        uføre = uføre,
        formue = formue,
    )
}
