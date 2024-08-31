package no.nav.su.se.bakover.test.grunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.Formueverdier
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
        søkersFormue = Formueverdier.empty(),
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
        epsFormue = Formueverdier.empty(),
        søkersFormue = Formueverdier.empty(),
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
        søkersFormue = formueverdier(
            innskudd = 1000000,
        ),
        behandlingsPeriode = periode,
    )
}

/**
 * Defaulter til 0 for alle felter.
 */
fun formueverdier(
    verdiIkkePrimærbolig: Int = 0,
    verdiEiendommer: Int = 0,
    verdiKjøretøy: Int = 0,
    innskudd: Int = 0,
    verdipapir: Int = 0,
    pengerSkyldt: Int = 0,
    kontanter: Int = 0,
    depositumskonto: Int = 0,
): Formueverdier {
    return Formueverdier.create(
        verdiIkkePrimærbolig = verdiIkkePrimærbolig,
        verdiEiendommer = verdiEiendommer,
        verdiKjøretøy = verdiKjøretøy,
        innskudd = innskudd,
        verdipapir = verdipapir,
        pengerSkyldt = pengerSkyldt,
        kontanter = kontanter,
        depositumskonto = depositumskonto,
    )
}
