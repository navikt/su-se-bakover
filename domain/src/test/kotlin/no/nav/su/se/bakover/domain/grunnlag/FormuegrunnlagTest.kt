package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.left
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.empty
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class FormuegrunnlagTest {

    @Nested
    inner class VerdierTest {
        @Test
        fun `Formue verdier kan ikke være negative for create`() {
            assertThrows<IllegalArgumentException> {
                Formuegrunnlag.Verdier.create(
                    verdiIkkePrimærbolig = -1,
                    verdiEiendommer = -2,
                    verdiKjøretøy = -3,
                    innskudd = -4,
                    verdipapir = -5,
                    pengerSkyldt = -6,
                    kontanter = -7,
                    depositumskonto = -8,
                )
            }
        }

        @Test
        fun `Formue verdier kan ikke være negative for try create`() {
            Formuegrunnlag.Verdier.tryCreate(
                verdiIkkePrimærbolig = -1,
                verdiEiendommer = -2,
                verdiKjøretøy = -3,
                innskudd = -4,
                verdipapir = -5,
                pengerSkyldt = -6,
                kontanter = -7,
                depositumskonto = -8,
            ) shouldBe KunneIkkeLageFormueVerdier.VerdierKanIkkeVæreNegativ.left()
        }

        @Test
        fun `alle verdier som 0 skal bli 0`() {
            Formuegrunnlag.Verdier.empty().sumVerdier() shouldBe 0
        }

        @Test
        fun `dersom depositum er høyere enn innskud, blir ikke sum negativ`() {
            Formuegrunnlag.Verdier.empty().copy(
                innskudd = 100,
                depositumskonto = 200,
            ).sumVerdier() shouldBe 0
        }

        @Test
        fun `Depositum blir trekket fra innskud`() {
            Formuegrunnlag.Verdier.empty().copy(
                innskudd = 500,
                depositumskonto = 200,
            ).sumVerdier() shouldBe 300
        }

        @Test
        fun `Innskudd blir ikke trekket fra dersom depositum er 0`() {
            Formuegrunnlag.Verdier.empty().copy(
                innskudd = 500,
                depositumskonto = 0,
            ).sumVerdier() shouldBe 500
        }
    }

    @Nested
    inner class FormuegrunnlagTest {

        val enslig = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            periode = Periode.create(1.januar(2021), 31.januar(2021)),
            begrunnelse = null,
        )

        private val formueUtenEPS = Formuegrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            periode = Periode.create(1.januar(2021), 31.januar(2021)),
            epsFormue = null,
            søkersFormue = Formuegrunnlag.Verdier.create(
                verdiIkkePrimærbolig = 1,
                verdiEiendommer = 1,
                verdiKjøretøy = 1,
                innskudd = 2,
                verdipapir = 1,
                pengerSkyldt = 1,
                kontanter = 1,
                depositumskonto = 1,
            ),
            begrunnelse = null,
            bosituasjon = enslig,
            behandlingsPeriode = Periode.create(1.januar(2021), 31.januar(2021)),
        )

        @Test
        fun `summerer formue for søker`() {
            formueUtenEPS.sumFormue() shouldBe 7
        }

        @Test
        fun `summerer formue for søker og eps`() {
            val formuegrunnlag = Formuegrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                periode = Periode.create(1.januar(2021), 31.januar(2021)),
                epsFormue = Formuegrunnlag.Verdier.create(
                    verdiIkkePrimærbolig = 1,
                    verdiEiendommer = 1,
                    verdiKjøretøy = 1,
                    innskudd = 2,
                    verdipapir = 1,
                    pengerSkyldt = 1,
                    kontanter = 1,
                    depositumskonto = 1,
                ),
                søkersFormue = Formuegrunnlag.Verdier.create(
                    verdiIkkePrimærbolig = 1,
                    verdiEiendommer = 1,
                    verdiKjøretøy = 1,
                    innskudd = 2,
                    verdipapir = 1,
                    pengerSkyldt = 1,
                    kontanter = 1,
                    depositumskonto = 1,
                ),
                begrunnelse = null,
                bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                    id = UUID.randomUUID(),
                    fnr = FnrGenerator.random(),
                    opprettet = Tidspunkt.EPOCH,
                    periode = Periode.create(1.januar(2021), 31.januar(2021)),
                    begrunnelse = null,
                ),
                behandlingsPeriode = Periode.create(1.januar(2021), 31.januar(2021)),
            )

            formuegrunnlag.sumFormue() shouldBe 14
        }

        @Test
        fun `oppdaterer periode`() {
            val nyPeriode = Periode.create(1.januar(2021), 31.mars(2021))
            val oppdatertPeriode = formueUtenEPS.oppdaterPeriode(nyPeriode)

            oppdatertPeriode.periode shouldBe nyPeriode
        }

        @Test
        fun `feiler når vi oppretter formuegrunnlag med periode som er utenfor behandlignsperioden`() {
            Formuegrunnlag.tryCreate(
                id = UUID.randomUUID(),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                opprettet = Tidspunkt.EPOCH, epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                begrunnelse = null,
                bosituasjon = enslig,
                behandlingsPeriode = Periode.create(1.januar(2021), 31.mars(2021)),
            ) shouldBeLeft KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden
        }

        @Test
        fun `Ok formuegrunnlag `() {
            val id = UUID.randomUUID()
            val periode = Periode.create(1.januar(2021), 31.desember(2021))

            val formueTryCreate = Formuegrunnlag.tryCreate(
                id = id,
                periode = periode,
                opprettet = Tidspunkt.EPOCH,
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                begrunnelse = null,
                bosituasjon = enslig,
                behandlingsPeriode = Periode.create(1.januar(2021), 31.desember(2021)),
            )

            formueTryCreate shouldBeRight Formuegrunnlag.create(
                id = id,
                periode = periode,
                opprettet = Tidspunkt.EPOCH,
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                begrunnelse = null,
                bosituasjon = enslig,
                behandlingsPeriode = Periode.create(1.januar(2021), 31.desember(2021)),
            )
        }
    }
}
