package no.nav.su.se.bakover.service

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.formue.domain.FormueVilkår
import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.Formueverdier
import java.util.UUID

internal fun formuegrunnlag(
    periode: Periode,
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    epsFormue: Formueverdier? = null,
) = Formuegrunnlag.create(
    id = id,
    opprettet = opprettet,
    periode = periode,
    epsFormue = epsFormue,
    søkersFormue = Formueverdier.empty(),
    behandlingsPeriode = periode,
)

internal fun formueVilkår(periode: Periode) = FormueVilkår.Vurdert.createFromGrunnlag(
    grunnlag = nonEmptyListOf(formuegrunnlag(periode)),
)
