package no.nav.su.se.bakover.service

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.empty
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
internal val fixedTidspunkt: Tidspunkt = Tidspunkt.now(fixedClock)

internal fun formuegrunnlag(
    periode: Periode,
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = Tidspunkt.now(),
    epsFormue: Formuegrunnlag.Verdier? = null,
) = Formuegrunnlag.create(
    id = id,
    opprettet = opprettet,
    periode = periode,
    epsFormue = epsFormue,
    søkersFormue = Formuegrunnlag.Verdier.empty(),
    begrunnelse = null,
    bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        periode = periode,
        begrunnelse = null,
    ),
    behandlingsPeriode = periode,
)

internal fun formueVilkår(periode: Periode) = Vilkår.Formue.Vurdert.createFromGrunnlag(
    grunnlag = nonEmptyListOf(formuegrunnlag(periode)),
)
