package no.nav.su.se.bakover.domain.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteÅrsak
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class PersonligOppmøteVilkårTest {

    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        PersonligOppmøteVilkår.Vurdert(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodePersonligOppmøte(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    grunnlag = PersonligOppmøteGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = år(2021),
                        årsak = PersonligOppmøteÅrsak.MøttPersonlig,
                    ),
                    periode = år(2021),
                ),
            ),
        ).oppdaterStønadsperiode(Stønadsperiode.create(juli(2021))).erLik(
            PersonligOppmøteVilkår.Vurdert(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodePersonligOppmøte(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        grunnlag = PersonligOppmøteGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = juli(2021),
                            årsak = PersonligOppmøteÅrsak.MøttPersonlig,
                        ),
                        periode = juli(2021),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `godtar ikke overlappende vurderingsperioder`() {
        assertThrows<IllegalArgumentException> {
            PersonligOppmøteVilkår.Vurdert(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodePersonligOppmøte(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        grunnlag = PersonligOppmøteGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = år(2021),
                            årsak = PersonligOppmøteÅrsak.MøttPersonlig,
                        ),
                        periode = år(2021),
                    ),
                    VurderingsperiodePersonligOppmøte(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        grunnlag = PersonligOppmøteGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = år(2021),
                            årsak = PersonligOppmøteÅrsak.MøttPersonlig,
                        ),
                        periode = år(2021),
                    ),
                ),
            )
        }
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodePersonligOppmøte(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            grunnlag = PersonligOppmøteGrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = mai(2021),
                årsak = PersonligOppmøteÅrsak.IkkeMøttMenKortvarigSykMedLegeerklæring,
            ),
            periode = mai(2021),
        )

        val v2 = VurderingsperiodePersonligOppmøte(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            grunnlag = PersonligOppmøteGrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = juni(2021),
                årsak = PersonligOppmøteÅrsak.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
            ),
            periode = juni(2021),
        )

        PersonligOppmøteVilkår.Vurdert(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        )
            .lagTidslinje(mai(2021))
            .erLik(PersonligOppmøteVilkår.Vurdert(vurderingsperioder = nonEmptyListOf(v1)))

        PersonligOppmøteVilkår.Vurdert(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        )
            .lagTidslinje(juni(2021))
            .erLik(PersonligOppmøteVilkår.Vurdert(vurderingsperioder = nonEmptyListOf(v2)))

        PersonligOppmøteVilkår.Vurdert(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        )
            .lagTidslinje(år(2021))
            .erLik(PersonligOppmøteVilkår.Vurdert(vurderingsperioder = nonEmptyListOf(v1, v2)))
    }
}
