package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.periode
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import java.util.UUID

/**
 * 100% uføregrad
 * 0 forventet inntekt
 * */
fun uføregrunnlagForventetInntekt0(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
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
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
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
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
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
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
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

fun innvilgetUførevilkårForventetInntekt0(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    uføregrunnlag: Grunnlag.Uføregrunnlag = uføregrunnlagForventetInntekt0(
        id = UUID.randomUUID(),
        opprettet = opprettet,
        periode = periode,
    ),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = id,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlag,
                periode = periode,
            ),
        ),
    )
}

fun utenlandsoppholdInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    grunnlag: Utenlandsoppholdgrunnlag? = null,
): UtenlandsoppholdVilkår.Vurdert {
    return UtenlandsoppholdVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUtenlandsopphold.create(
                id = id,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = grunnlag,
                periode = periode,
            ),
        ),
    ).getOrFail()
}

fun tilstrekkeligDokumentert(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): OpplysningspliktVilkår.Vurdert {
    return OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOpplysningsplikt.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                grunnlag = Opplysningspliktgrunnlag(
                    id = id,
                    opprettet = opprettet,
                    periode = periode,
                    beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                ),
            ),
        ),
    )
}

fun utilstrekkeligDokumentert(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): OpplysningspliktVilkår.Vurdert {
    return OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOpplysningsplikt.create(
                id = id,
                opprettet = opprettet,
                grunnlag = Opplysningspliktgrunnlag(
                    id = id,
                    opprettet = opprettet,
                    periode = periode,
                    beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                ),
                periode = periode,
            ),
        ),
    )
}

fun utenlandsoppholdAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): UtenlandsoppholdVilkår.Vurdert {
    return UtenlandsoppholdVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUtenlandsopphold.create(
                id = id,
                opprettet = opprettet,
                resultat = Resultat.Avslag,
                grunnlag = null,
                periode = periode,
            ),
        ),
    ).getOrFail()
}

fun innvilgetUførevilkårForventetInntekt12000(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlagForventetInntekt12000(opprettet = opprettet, periode = periode),
                periode = periode,
            ),
        ),
    )
}

fun innvilgetUførevilkår(
    vurderingsperiodeId: UUID = UUID.randomUUID(),
    grunnlagsId: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
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
            ),
        ),
    )
}

fun avslåttUførevilkårUtenGrunnlag(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                resultat = Resultat.Avslag,
                grunnlag = null,
                periode = periode,
            ),
        ),
    )
}

fun formueGrunnlagUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig>
): Formuegrunnlag {
    val bosituasjonsperiode = bosituasjon.toList().periode()
    require(bosituasjonsperiode == periode) {
        "Bosituasjonsperiode: $bosituasjonsperiode må være lik formuevilkåret sin periode: $periode"
    }
    return Formuegrunnlag.create(
        id = UUID.randomUUID(),
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
        bosituasjon = bosituasjon.toList(),
        behandlingsPeriode = periode,
    )
}

fun formueGrunnlagUtenEpsAvslått(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(),
): Formuegrunnlag {
    return formueGrunnlagUtenEpsAvslått(
        id = id,
        opprettet = opprettet,
        periode = periode,
        bosituasjon = nonEmptyListOf(bosituasjon),
    )
}

fun formueGrunnlagUtenEpsAvslått(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig>,
): Formuegrunnlag {
    val bosituasjonsperiode = bosituasjon.toList().periode()
    require(bosituasjonsperiode == periode) {
        "Bosituasjonsperiode: $bosituasjonsperiode må være lik formuevilkåret sin periode: $periode"
    }
    return Formuegrunnlag.create(
        id = id,
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
        bosituasjon = bosituasjon,
        behandlingsPeriode = periode,
    )
}

fun vilkårsvurderingSøknadsbehandlingIkkeVurdert(): Vilkårsvurderinger.Søknadsbehandling {
    return Vilkårsvurderinger.Søknadsbehandling.ikkeVurdert()
}

fun vilkårsvurderingRevurderingIkkeVurdert(): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering.ikkeVurdert()
}

fun formuevilkårIkkeVurdert(): Vilkår.Formue {
    return Vilkår.Formue.IkkeVurdert
}

fun formuevilkårUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(),
): Vilkår.Formue {
    return formuevilkårUtenEps0Innvilget(
        opprettet = opprettet,
        periode = periode,
        bosituasjon = nonEmptyListOf(bosituasjon),
    )
}

fun formuevilkårUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig>,
): Vilkår.Formue.Vurdert {
    val bosituasjonsperiode = bosituasjon.toList().periode()
    require(bosituasjonsperiode == periode) {
        "Bosituasjonsperiode: $bosituasjonsperiode må være lik formuevilkåret sin periode: $periode"
    }
    return Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Formue.tryCreateFromGrunnlag(
                grunnlag = formueGrunnlagUtenEps0Innvilget(opprettet, periode, bosituasjon),
                formuegrenserFactory = formuegrenserFactoryTest,
            ).also {
                assert(it.resultat == Resultat.Innvilget)
                assert(it.periode == periode)
                assert(it.opprettet == opprettet)
            },
        ),
    )
}

fun formuevilkårAvslåttPgrBrukersformue(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(
        periode = periode,
    ),
): Vilkår.Formue.Vurdert {
    return Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Formue.tryCreateFromGrunnlag(
                grunnlag = formueGrunnlagUtenEpsAvslått(
                    opprettet = opprettet,
                    periode = periode,
                    bosituasjon = bosituasjon,
                ),
                formuegrenserFactory = formuegrenserFactoryTest,
            ).also {
                assert(it.resultat == Resultat.Avslag)
                assert(it.periode == periode)
                assert(it.opprettet == opprettet)
            },
        ),
    )
}

fun formuevilkårAvslåttPgrBrukersformue(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig>,
): Vilkår.Formue {
    return Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Formue.tryCreateFromGrunnlag(
                grunnlag = formueGrunnlagUtenEpsAvslått(
                    opprettet = opprettet,
                    periode = periode,
                    bosituasjon = bosituasjon,
                ),
                formuegrenserFactory = formuegrenserFactoryTest,
            ).also {
                assert(it.resultat == Resultat.Avslag)
                assert(it.periode == periode)
                assert(it.opprettet == opprettet)
            },
        ),
    )
}

/**
 * periode: 2021
 * uføre: innvilget med forventet inntekt 0
 * bosituasjon: enslig
 * formue (via behandlingsinformasjon): ingen eps, sum 0
 * utenlandsopphold: innvilget
 */
fun vilkårsvurderingerSøknadsbehandlingInnvilget(
    periode: Periode = år(2021),
    uføre: Vilkår.Uførhet = innvilgetUførevilkårForventetInntekt0(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(
        bosituasjongrunnlagEnslig(
            id = UUID.randomUUID(),
            periode = periode,
        ),
    ),
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    formue: Vilkår.Formue = formuevilkårUtenEps0Innvilget(periode = periode, bosituasjon = bosituasjon),
): Vilkårsvurderinger.Søknadsbehandling {
    return Vilkårsvurderinger.Søknadsbehandling(
        uføre = uføre,
        utenlandsopphold = utenlandsopphold,
        formue = formue,
        opplysningsplikt = opplysningsplikt,
    ).oppdater(
        stønadsperiode = Stønadsperiode.create(periode = periode),
        behandlingsinformasjon = behandlingsinformasjon,
        clock = fixedClock,
    )
}

fun vilkårsvurderingerRevurderingInnvilget(
    periode: Periode = år(2021),
    uføre: Vilkår.Uførhet = innvilgetUførevilkårForventetInntekt0(periode = periode),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
    formue: Vilkår.Formue = formuevilkårUtenEps0Innvilget(
        periode = periode,
        bosituasjon = bosituasjon,
    ),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(periode = periode),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(periode = periode),
): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = uføre,
        formue = formue,
        utenlandsopphold = utenlandsopphold,
        opplysningsplikt = opplysningsplikt,
    )
}

fun vilkårsvurderingerAvslåttAlleRevurdering(
    periode: Periode = år(2021),
    uføre: Vilkår.Uførhet = avslåttUførevilkårUtenGrunnlag(periode = periode),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
    formue: Vilkår.Formue = formuevilkårAvslåttPgrBrukersformue(
        periode = periode,
        bosituasjon = NonEmptyList.fromListUnsafe(bosituasjon.toList()),
    ),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdAvslag(periode = periode),
    opplysningsplikt: OpplysningspliktVilkår = utilstrekkeligDokumentert(periode = periode),
): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = uføre,
        formue = formue,
        utenlandsopphold = utenlandsopphold,
        opplysningsplikt = opplysningsplikt,
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
    periode: Periode = år(2021),
): Vilkårsvurderinger.Søknadsbehandling {
    return Vilkårsvurderinger.Søknadsbehandling(
        uføre = avslåttUførevilkårUtenGrunnlag(
            periode = periode,
        ),
        utenlandsopphold = utenlandsoppholdAvslag(periode = periode),
        formue = formuevilkårAvslåttPgrBrukersformue(
            periode = periode,
            bosituasjon = bosituasjongrunnlagEnslig(periode = periode),
        ),
        opplysningsplikt = utilstrekkeligDokumentert(periode = periode),
    ).oppdater(
        stønadsperiode = Stønadsperiode.create(periode = periode),
        behandlingsinformasjon = behandlingsinformasjonAlleVilkårAvslått,
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
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = avslåttUførevilkårUtenGrunnlag(periode = periode),
        formue = formuevilkårUtenEps0Innvilget(
            periode = periode,
            bosituasjon = bosituasjon,
        ),
        utenlandsopphold = utenlandsoppholdInnvilget(periode = periode),
        opplysningsplikt = tilstrekkeligDokumentert(periode = periode),
    )
}

fun avslåttFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode,
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(
        Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
        ),
    ),
): Vilkår.Formue.Vurdert {
    require(bosituasjon.all { it.harEPS() } || bosituasjon.none { it.harEPS() }) {
        "Test-dataene har ikke støtte for å dele opp formue i fler perioder enda."
    }
    val (søkerVerdi, epsVerdi) = when (bosituasjon.first().harEPS()) {
        true -> {
            Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 15_000) to
                Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 150_000)
        }
        false -> {
            Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 150_000) to
                null
        }
    }
    return Vilkår.Formue.Vurdert.createFromGrunnlag(
        nonEmptyListOf(
            Formuegrunnlag.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                epsFormue = epsVerdi,
                søkersFormue = søkerVerdi,
                bosituasjon = bosituasjon,
                behandlingsPeriode = periode,
            ),
        ),
    )
}

fun innvilgetFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
    ),
): Vilkår.Formue.Vurdert {
    return innvilgetFormueVilkår(
        id = id,
        opprettet = opprettet,
        periode = periode,
        bosituasjon = nonEmptyListOf(bosituasjon),
    )
}

fun innvilgetFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode,
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig>,
): Vilkår.Formue.Vurdert {
    require(bosituasjon.all { it.harEPS() } || bosituasjon.none { it.harEPS() }) {
        "Test-dataene har ikke støtte for å dele opp formue i fler perioder enda."
    }
    val (søkerVerdi, epsVerdi) = when (bosituasjon.first().harEPS()) {
        true -> {
            Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 15_000) to
                Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 15_000)
        }
        false -> {
            Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 15_000) to
                null
        }
    }
    return Vilkår.Formue.Vurdert.createFromGrunnlag(
        nonEmptyListOf(
            Formuegrunnlag.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                epsFormue = epsVerdi,
                søkersFormue = søkerVerdi,
                bosituasjon = bosituasjon,
                behandlingsPeriode = periode,
            ),
        ),
    )
}
