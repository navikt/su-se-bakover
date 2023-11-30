package no.nav.su.se.bakover.test.vilkår

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Bosituasjon
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.periode
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFormue
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

fun formuevilkårIkkeVurdert(): FormueVilkår {
    return FormueVilkår.IkkeVurdert
}

fun formuevilkårUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(),
): FormueVilkår {
    return formuevilkårUtenEps0Innvilget(
        opprettet = opprettet,
        periode = periode,
        bosituasjon = nonEmptyListOf(bosituasjon),
    )
}

fun formuevilkårUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Bosituasjon.Fullstendig>,
): FormueVilkår.Vurdert {
    val bosituasjonsperiode = bosituasjon.periode()
    require(bosituasjonsperiode == periode) {
        "Bosituasjonsperiode: $bosituasjonsperiode må være lik formuevilkåret sin periode: $periode"
    }
    return FormueVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeFormue.tryCreateFromGrunnlag(
                grunnlag = formueGrunnlagUtenEps0Innvilget(opprettet, periode, bosituasjon),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(opprettet),
            ).also {
                require(it.vurdering == Vurdering.Innvilget)
                require(it.periode == periode)
                require(it.opprettet == opprettet)
            },
        ),
    )
}

fun formuevilkårMedEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Bosituasjon.Fullstendig.EktefellePartnerSamboer>,
): FormueVilkår.Vurdert {
    val bosituasjonsperiode = bosituasjon.periode()
    require(bosituasjonsperiode == periode) {
        "Bosituasjonsperiode: $bosituasjonsperiode må være lik formuevilkåret sin periode: $periode"
    }
    return FormueVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeFormue.tryCreateFromGrunnlag(
                grunnlag = formueGrunnlagMedEps0Innvilget(opprettet, periode, bosituasjon),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(opprettet),
            ).also {
                require(it.vurdering == Vurdering.Innvilget)
                require(it.periode == periode)
                require(it.opprettet == opprettet)
            },
        ),
    )
}

fun formuevilkårAvslåttPgaBrukersformue(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(
        periode = periode,
    ),
): FormueVilkår.Vurdert {
    return FormueVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeFormue.tryCreateFromGrunnlag(
                grunnlag = formueGrunnlagUtenEpsAvslått(
                    opprettet = opprettet,
                    periode = periode,
                    bosituasjon = bosituasjon,
                ),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(opprettet),
            ).also {
                require(it.vurdering == Vurdering.Avslag)
                require(it.periode == periode)
                require(it.opprettet == opprettet)
            },
        ),
    )
}

fun formuevilkårAvslåttPgaBrukersformue(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Bosituasjon.Fullstendig>,
): FormueVilkår {
    return FormueVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeFormue.tryCreateFromGrunnlag(
                grunnlag = formueGrunnlagUtenEpsAvslått(
                    opprettet = opprettet,
                    periode = periode,
                    bosituasjon = bosituasjon,
                ),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
            ).also {
                require(it.vurdering == Vurdering.Avslag)
                require(it.periode == periode)
                require(it.opprettet == opprettet)
            },
        ),
    )
}

fun avslåttFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Bosituasjon.Fullstendig> = nonEmptyListOf(
        Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
        ),
    ),
): FormueVilkår.Vurdert {
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
    return FormueVilkår.Vurdert.createFromGrunnlag(
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
    periode: Periode = år(2021),
    bosituasjon: Bosituasjon.Fullstendig = Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
    ),
): FormueVilkår.Vurdert {
    return innvilgetFormueVilkår(
        id = id,
        opprettet = opprettet,
        periode = periode,
        bosituasjon = nonEmptyListOf(bosituasjon),
    )
}

private fun innvilgetFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode,
    bosituasjon: NonEmptyList<Bosituasjon.Fullstendig>,
): FormueVilkår.Vurdert {
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
    return FormueVilkår.Vurdert.createFromGrunnlag(
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
