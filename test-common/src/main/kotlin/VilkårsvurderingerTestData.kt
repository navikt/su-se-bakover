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

/**
 * 100% uføregrad
 * 0 forventet inntekt
 * */
fun uføregrunnlagForventetInntekt0(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): Grunnlag.Uføregrunnlag {
    return uføregrunnlagForventetInntekt(
        opprettet = opprettet,
        periode = periode,
        forventetInntekt = 0,
    )
}

/**
 * 100% uføregrad
 * 12000 forventet inntekt per år / 1000 per måned
 * */
fun uføregrunnlagForventetInntekt12000(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): Grunnlag.Uføregrunnlag {
    return uføregrunnlagForventetInntekt(
        opprettet = opprettet,
        periode = periode,
        forventetInntekt = 12000,
    )
}

/** 100% uføregrad */
fun uføregrunnlagForventetInntekt(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
    forventetInntekt: Int,
): Grunnlag.Uføregrunnlag {
    return Grunnlag.Uføregrunnlag(
        id = uføregrunnlagId,
        opprettet = opprettet,
        periode = periode,
        uføregrad = Uføregrad.parse(100),
        forventetInntekt = forventetInntekt,
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

fun innvilgetUførevilkårForventetInntekt12000(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = uførevilkårId,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlagForventetInntekt12000(opprettet, periode),
                periode = periode,
                begrunnelse = "innvilgetUførevilkårForventetInntekt12000",
            ),
        ),
    )
}

fun avslåttUførevilkårUtenGrunnlag(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = uførevilkårId,
                opprettet = opprettet,
                resultat = Resultat.Avslag,
                grunnlag = null,
                periode = periode,
                begrunnelse = "avslåttUførevilkårUtenGrunnlag",
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

fun formueGrunnlagUtenEpsAvslått(
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
            innskudd = 1000000,
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

fun formuevilkårAvslåttPgrBrukersformue(
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
                resultat = Resultat.Avslag,
                grunnlag = formueGrunnlagUtenEpsAvslått(opprettet, periode, bosituasjon),
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

/**
 * Defaults:
 * periode: 2021
 * bosituasjon: enslig
 *
 * Predefined:
 * uføre: avslag
 * formue: innvilget
 */
@Suppress("unused")
fun vilkårsvurderingerAvslåttAlle(
    periode: Periode = periode2021,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode),
): Vilkårsvurderinger {
    return Vilkårsvurderinger(
        uføre = avslåttUførevilkårUtenGrunnlag(
            periode = periode,
        ),
        formue = formuevilkårAvslåttPgrBrukersformue(
            periode = periode,
            bosituasjon = bosituasjon,
        ),
    )
}

/**
 * Defaults:
 * periode: 2021
 * bosituasjon: enslig
 *
 * Predefined:
 * uføre: avslag
 * formue: innvilget
 */
fun vilkårsvurderingerAvslåttUføre(
    periode: Periode = periode2021,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode),
): Vilkårsvurderinger {
    return Vilkårsvurderinger(
        uføre = avslåttUførevilkårUtenGrunnlag(
            periode = periode,
        ),
        formue = formuevilkårUtenEps0Innvilget(
            periode = periode,
            bosituasjon = bosituasjon,
        ),
    )
}
