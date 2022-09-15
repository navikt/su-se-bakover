package no.nav.su.se.bakover.test.grunnlag

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.periode
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import java.util.UUID

fun formueGrunnlagUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig>,
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

fun formueGrunnlagMedEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer>,
): Formuegrunnlag {
    val bosituasjonsperiode = bosituasjon.toList().periode()
    require(bosituasjonsperiode == periode) {
        "Bosituasjonsperiode: $bosituasjonsperiode må være lik formuevilkåret sin periode: $periode"
    }
    return Formuegrunnlag.create(
        id = UUID.randomUUID(),
        opprettet = opprettet,
        periode = periode,
        epsFormue = Formuegrunnlag.Verdier.create(
            verdiIkkePrimærbolig = 0,
            verdiEiendommer = 0,
            verdiKjøretøy = 0,
            innskudd = 0,
            verdipapir = 0,
            pengerSkyldt = 0,
            kontanter = 0,
            depositumskonto = 0,
        ),
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
