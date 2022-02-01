package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeJuli2021
import no.nav.su.se.bakover.test.periodeJuni2021
import no.nav.su.se.bakover.test.periodeMai2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtenlandsoppholdVilkårTest {

    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = periode2021,
                    ),
                    vurderingsperiode = periode2021,
                    begrunnelse = "jabadoo",
                ).getOrFail(),
            ),
        ).getOrFail().oppdaterStønadsperiode(Stønadsperiode.create(periodeJuli2021, "")).erLik(
            UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Innvilget,
                        grunnlag = Utenlandsoppholdgrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = periodeJuli2021,
                        ),
                        vurderingsperiode = periodeJuli2021,
                        begrunnelse = "jabadoo",
                    ).getOrFail(),
                ),
            ).getOrFail(),
        )
    }

    @Test
    fun `godtar ikke overlappende vurderingsperioder`() {
        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = periode2021,
                    ),
                    vurderingsperiode = periode2021,
                    begrunnelse = "jabadoo",
                ).getOrFail(),
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = periode2021,
                    ),
                    vurderingsperiode = periode2021,
                    begrunnelse = "jabadoo",
                ).getOrFail(),
            ),
        ) shouldBe UtenlandsoppholdVilkår.Vurdert.UgyldigUtenlandsoppholdVilkår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodeUtenlandsopphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = periodeMai2021,
            ),
            vurderingsperiode = periodeMai2021,
            begrunnelse = "jabadoo",
        ).getOrFail()

        val v2 = VurderingsperiodeUtenlandsopphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = periodeJuni2021,
            ),
            vurderingsperiode = periodeJuni2021,
            begrunnelse = null,
        ).getOrFail()

        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(periodeMai2021)
            .erLik(UtenlandsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1)).getOrFail())

        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(periodeJuni2021)
            .erLik(UtenlandsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v2)).getOrFail())

        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(periode2021)
            .erLik(UtenlandsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1, v2)).getOrFail())
    }
}
