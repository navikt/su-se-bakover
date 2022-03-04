package no.nav.su.se.bakover.domain

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedTidspunkt
import java.util.UUID

internal fun avslåttFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
        begrunnelse = null,
    ),
): Vilkår.Formue.Vurdert {
    val (søkerVerdi, epsVerdi) = when (bosituasjon.harEPS()) {
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
                begrunnelse = null,
                bosituasjon = bosituasjon,
                behandlingsPeriode = periode,
            ),
        ),
    )
}

internal fun innvilgetFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
        begrunnelse = null,
    ),
): Vilkår.Formue.Vurdert {
    val (søkerVerdi, epsVerdi) = when (bosituasjon.harEPS()) {
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
                begrunnelse = null,
                bosituasjon = bosituasjon,
                behandlingsPeriode = periode,
            ),
        ),
    )
}
