package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOppholdIUtlandet
import java.util.UUID

val uføregrunnlagId: UUID = UUID.randomUUID()

/**
 * 100% uføregrad
 * 0 forventet inntekt
 * */
fun uføregrunnlagForventetInntekt0(
    id: UUID = uføregrunnlagId,
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): Grunnlag.Uføregrunnlag {
    return uføregrunnlagForventetInntekt(
        id = id,
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
    id: UUID = uføregrunnlagId,
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): Grunnlag.Uføregrunnlag {
    return uføregrunnlagForventetInntekt(
        id = id,
        opprettet = opprettet,
        periode = periode,
        forventetInntekt = 12000,
    )
}

/** 100% uføregrad */
fun uføregrunnlagForventetInntekt(
    id: UUID = uføregrunnlagId,
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
    forventetInntekt: Int,
): Grunnlag.Uføregrunnlag {
    return Grunnlag.Uføregrunnlag(
        id = id,
        opprettet = opprettet,
        periode = periode,
        uføregrad = Uføregrad.parse(100),
        forventetInntekt = forventetInntekt,
    )
}

fun uføregrunnlag(
    id: UUID = uføregrunnlagId,
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
    forventetInntekt: Int = 0,
    uføregrad: Uføregrad = Uføregrad.parse(100),
): Grunnlag.Uføregrunnlag {
    return Grunnlag.Uføregrunnlag(
        id = id,
        opprettet = opprettet,
        periode = periode,
        uføregrad = uføregrad,
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
                grunnlag = uføregrunnlagForventetInntekt0(opprettet = opprettet, periode = periode),
                periode = periode,
                begrunnelse = "innvilgetUførevilkårForventetInntekt0",
            ),
        ),
    )
}

fun utlandsoppholdInnvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): OppholdIUtlandetVilkår {
    return OppholdIUtlandetVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOppholdIUtlandet.create(
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = null,
                periode = periode,
                begrunnelse = "begrunnelse",
            )
        )
    ).getOrFail()
}

fun utlandsoppholdAvslag(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
): OppholdIUtlandetVilkår {
    return OppholdIUtlandetVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOppholdIUtlandet.create(
                opprettet = opprettet,
                resultat = Resultat.Avslag,
                grunnlag = null,
                periode = periode,
                begrunnelse = "begrunnelse",
            )
        )
    ).getOrFail()
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
                grunnlag = uføregrunnlagForventetInntekt12000(opprettet = opprettet, periode = periode),
                periode = periode,
                begrunnelse = "innvilgetUførevilkårForventetInntekt12000",
            ),
        ),
    )
}

fun innvilgetUførevilkår(
    vurderingsperiodeId: UUID = uførevilkårId,
    grunnlagsId: UUID = uføregrunnlagId,
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = periode2021,
    begrunnelse: String? = "innvilgetUførevilkårForventetInntekt0",
    forventetInntekt: Int = 0,
    uføregrad: Uføregrad = Uføregrad.parse(100),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = vurderingsperiodeId,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlag(
                    id = grunnlagsId,
                    opprettet = opprettet,
                    periode = periode,
                    forventetInntekt = forventetInntekt,
                    uføregrad = uføregrad,
                ),
                periode = periode,
                begrunnelse = begrunnelse,
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
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    oppholdIUtlandet: OppholdIUtlandetVilkår = utlandsoppholdInnvilget(periode = periode)
): Vilkårsvurderinger.Søknadsbehandling {
    return Vilkårsvurderinger.Søknadsbehandling(
        uføre = uføre,
        oppholdIUtlandet = oppholdIUtlandet
    ).oppdater(
        stønadsperiode = Stønadsperiode.create(periode = periode, begrunnelse = ""),
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = Grunnlagsdata.tryCreate(
            fradragsgrunnlag = emptyList(),
            bosituasjon = listOf(bosituasjon),
        ).getOrFail(),
        clock = fixedClock,
    )
}

fun vilkårsvurderingerInnvilgetRevurdering(
    periode: Periode = periode2021,
    uføre: Vilkår.Uførhet = innvilgetUførevilkårForventetInntekt0(periode = periode),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode),
    formue: Vilkår.Formue = formuevilkårUtenEps0Innvilget(periode = periode, bosituasjon = bosituasjon),
    utlandsopphold: OppholdIUtlandetVilkår = utlandsoppholdInnvilget(periode = periode)
): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = uføre,
        formue = formue,
        oppholdIUtlandet = utlandsopphold,
    )
}

fun vilkårsvurderingerAvslåttAlleRevurdering(
    periode: Periode = periode2021,
    uføre: Vilkår.Uførhet = avslåttUførevilkårUtenGrunnlag(periode = periode),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode),
    formue: Vilkår.Formue = formuevilkårAvslåttPgrBrukersformue(periode = periode, bosituasjon = bosituasjon),
    oppholdIUtlandet: OppholdIUtlandetVilkår = utlandsoppholdAvslag(periode = periode)
): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = uføre,
        formue = formue,
        oppholdIUtlandet = oppholdIUtlandet,
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
): Vilkårsvurderinger.Søknadsbehandling {
    return Vilkårsvurderinger.Søknadsbehandling(
        uføre = avslåttUførevilkårUtenGrunnlag(
            periode = periode,
        ),
        oppholdIUtlandet = utlandsoppholdAvslag(periode = periode),
    ).oppdater(
        stønadsperiode = Stønadsperiode.create(
            periode = periode,
            begrunnelse = "",
        ),
        behandlingsinformasjon = behandlingsinformasjonAlleVilkårAvslått,
        grunnlagsdata = Grunnlagsdata.tryCreate(
            fradragsgrunnlag = listOf(),
            bosituasjon = listOf(bosituasjon),
        ).getOrFail(),
        clock = fixedClock,
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
fun vilkårsvurderingerAvslåttUføreOgAndreInnvilget(
    periode: Periode = periode2021,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode),
): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = avslåttUførevilkårUtenGrunnlag(
            periode = periode,
        ),
        formue = formuevilkårUtenEps0Innvilget(
            periode = periode,
            bosituasjon = bosituasjon,
        ),
        oppholdIUtlandet = utlandsoppholdInnvilget(
            periode = periode,
        )
    )
}
