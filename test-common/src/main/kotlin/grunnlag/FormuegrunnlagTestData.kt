package no.nav.su.se.bakover.test.grunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.Verdier
import java.util.UUID

fun formueGrunnlagUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Formuegrunnlag {
    return Formuegrunnlag.create(
        id = UUID.randomUUID(),
        opprettet = opprettet,
        periode = periode,
        epsFormue = null,
        søkersFormue = Verdier.empty(),
        behandlingsPeriode = periode,
    )
}

fun formueGrunnlagMedEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Formuegrunnlag {
    return Formuegrunnlag.create(
        id = UUID.randomUUID(),
        opprettet = opprettet,
        periode = periode,
        epsFormue = Verdier.empty(),
        søkersFormue = Verdier.empty(),
        behandlingsPeriode = periode,
    )
}

fun formueGrunnlagUtenEpsAvslått(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Formuegrunnlag {
    return Formuegrunnlag.create(
        id = id,
        opprettet = opprettet,
        periode = periode,
        epsFormue = null,
        søkersFormue = Verdier.create(
            verdiIkkePrimærbolig = 0,
            verdiEiendommer = 0,
            verdiKjøretøy = 0,
            innskudd = 1000000,
            verdipapir = 0,
            pengerSkyldt = 0,
            kontanter = 0,
            depositumskonto = 0,
        ),
        behandlingsPeriode = periode,
    )
}
