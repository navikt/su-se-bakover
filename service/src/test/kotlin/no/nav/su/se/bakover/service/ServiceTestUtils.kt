package no.nav.su.se.bakover.service

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.domain.grunnlag.Bosituasjon
import java.util.UUID

internal fun formuegrunnlag(
    periode: Periode,
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    epsFormue: Formuegrunnlag.Verdier? = null,
) = Formuegrunnlag.create(
    id = id,
    opprettet = opprettet,
    periode = periode,
    epsFormue = epsFormue,
    søkersFormue = Formuegrunnlag.Verdier.empty(),
    bosituasjon = Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
    ),
    behandlingsPeriode = periode,
)

internal fun formueVilkår(periode: Periode) = FormueVilkår.Vurdert.createFromGrunnlag(
    grunnlag = nonEmptyListOf(formuegrunnlag(periode)),
)
