package no.nav.su.se.bakover.test

import arrow.core.Nel
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.util.UUID

fun Vilkår.Uførhet.Vurdert.Companion.create(
    vurderingsperioder: Nel<Vurderingsperiode.Uføre>,
): Vilkår.Uførhet.Vurdert {
    return tryCreate(vurderingsperioder)
        .getOrHandle { throw IllegalArgumentException(it.toString()) }
}

fun Vilkår.Formue.Vurdert.Companion.createFromGrunnlag(
    grunnlag: Nel<Formuegrunnlag>,
): Vilkår.Formue.Vurdert =
    tryCreateFromGrunnlag(grunnlag).getOrHandle { throw IllegalArgumentException(it.toString()) }

fun Formuegrunnlag.Companion.create(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = Tidspunkt.now(),
    periode: Periode,
    epsFormue: Formuegrunnlag.Verdier?,
    søkersFormue: Formuegrunnlag.Verdier,
    begrunnelse: String?,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
    behandlingsPeriode: Periode,
): Formuegrunnlag = tryCreate(
    id,
    opprettet,
    periode,
    epsFormue,
    søkersFormue,
    begrunnelse,
    bosituasjon,
    behandlingsPeriode,
).getOrHandle { throw IllegalArgumentException("Kunne ikke instansiere Formuegrunnlag. Underliggende grunn: $it") }

fun Formuegrunnlag.Verdier.Companion.empty() = Formuegrunnlag.Verdier(
    verdiIkkePrimærbolig = 0,
    verdiEiendommer = 0,
    verdiKjøretøy = 0,
    innskudd = 0,
    verdipapir = 0,
    pengerSkyldt = 0,
    kontanter = 0,
    depositumskonto = 0,
)
