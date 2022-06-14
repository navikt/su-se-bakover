package no.nav.su.se.bakover.test.vilkår

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.periode
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagMedEps0Innvilget
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagUtenEps0Innvilget
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagUtenEpsAvslått
import java.util.UUID

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
                formuegrenserFactory = formuegrenserFactoryTestPåDato(opprettet),
            ).also {
                assert(it.resultat == Resultat.Innvilget)
                assert(it.periode == periode)
                assert(it.opprettet == opprettet)
            },
        ),
    )
}

fun formuevilkårMedEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer>,
): Vilkår.Formue.Vurdert {
    val bosituasjonsperiode = bosituasjon.toList().periode()
    require(bosituasjonsperiode == periode) {
        "Bosituasjonsperiode: $bosituasjonsperiode må være lik formuevilkåret sin periode: $periode"
    }
    return Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Formue.tryCreateFromGrunnlag(
                grunnlag = formueGrunnlagMedEps0Innvilget(opprettet, periode, bosituasjon),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(opprettet),
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
                formuegrenserFactory = formuegrenserFactoryTestPåDato(opprettet),
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
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
            ).also {
                assert(it.resultat == Resultat.Avslag)
                assert(it.periode == periode)
                assert(it.opprettet == opprettet)
            },
        ),
    )
}

fun avslåttFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
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

fun innvilgetFormueVilkårMedEnsligBosituasjon(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
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
