package no.nav.su.se.bakover.domain

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.empty
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal val fixedClock: Clock =
    Clock.fixed(1.januar(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
internal val fixedTidspunkt: Tidspunkt = Tidspunkt.now(fixedClock)

internal fun formuegrunnlag(
    periode: Periode,
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    epsFormue: Formuegrunnlag.Verdier? = null,
    søkersFormue: Formuegrunnlag.Verdier = Formuegrunnlag.Verdier.empty(),
) = Formuegrunnlag.create(
    id = id,
    opprettet = opprettet,
    periode = periode,
    epsFormue = epsFormue,
    søkersFormue = søkersFormue,
    begrunnelse = null,
    bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
        begrunnelse = null,
    ),
    behandlingsPeriode = periode,
)

internal fun innvilgetFormueVilkår(periode: Periode) = Vilkår.Formue.Vurdert.createFromGrunnlag(
    grunnlag = nonEmptyListOf(formuegrunnlag(periode)),
)

internal fun avslåttFormueVilkår(periode: Periode) = Vilkår.Formue.Vurdert.createFromGrunnlag(
    grunnlag = nonEmptyListOf(
        formuegrunnlag(
            periode = periode,
            søkersFormue = Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 300000),
        ),
    ),
)
