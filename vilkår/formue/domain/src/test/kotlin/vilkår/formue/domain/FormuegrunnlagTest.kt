package vilkår.formue.domain

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagUtenEps0Innvilget
import no.nav.su.se.bakover.test.grunnlag.formueverdier
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import java.util.UUID

internal class FormuegrunnlagTest {

    @Nested
    inner class VerdierTest {
        @Test
        fun `Formue verdier kan ikke være negative for create`() {
            assertThrows<IllegalArgumentException> {
                formueverdier(
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
            Formueverdier.tryCreate(
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
            Formueverdier.empty().sumVerdier() shouldBe 0
        }

        @Test
        fun `feiler dersom depositum er høyere enn innskudd`() {
            shouldThrow<IllegalArgumentException> {
                formueverdier(
                    innskudd = 199,
                    depositumskonto = 200,
                ).sumVerdier() shouldBe 0
            }.message shouldBe "DepositumErStørreEnnInnskudd"
        }

        @Test
        fun `Depositum blir trekket fra innskud`() {
            formueverdier(
                innskudd = 500,
                depositumskonto = 200,
            ).sumVerdier() shouldBe 300
        }

        @Test
        fun `Innskudd blir ikke trekket fra dersom depositum er 0`() {
            formueverdier(
                innskudd = 500,
                depositumskonto = 0,
            ).sumVerdier() shouldBe 500
        }
    }

    @Nested
    inner class FormuegrunnlagTest {

        val enslig = Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            periode = januar(2021),
        )

        private val formueUtenEPS = Formuegrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            periode = januar(2021),
            epsFormue = null,
            søkersFormue = formueverdier(
                verdiIkkePrimærbolig = 1,
                verdiEiendommer = 1,
                verdiKjøretøy = 1,
                innskudd = 2,
                verdipapir = 1,
                pengerSkyldt = 1,
                kontanter = 1,
                depositumskonto = 1,
            ),
            behandlingsPeriode = januar(2021),
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
                periode = januar(2021),
                epsFormue = formueverdier(
                    verdiIkkePrimærbolig = 1,
                    verdiEiendommer = 1,
                    verdiKjøretøy = 1,
                    innskudd = 2,
                    verdipapir = 1,
                    pengerSkyldt = 1,
                    kontanter = 1,
                    depositumskonto = 1,
                ),
                søkersFormue = formueverdier(
                    verdiIkkePrimærbolig = 1,
                    verdiEiendommer = 1,
                    verdiKjøretøy = 1,
                    innskudd = 2,
                    verdipapir = 1,
                    pengerSkyldt = 1,
                    kontanter = 1,
                    depositumskonto = 1,
                ),
                behandlingsPeriode = januar(2021),
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
            val periode = år(2021)
            Formuegrunnlag.tryCreate(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.EPOCH,
                epsFormue = null,
                søkersFormue = Formueverdier.empty(),
                behandlingsPeriode = Periode.create(1.januar(2021), 31.mars(2021)),
            ) shouldBe KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden.left()
        }

        @Test
        fun `Ok formuegrunnlag `() {
            val id = UUID.randomUUID()
            val periode = år(2021)

            val formueTryCreate = Formuegrunnlag.tryCreate(
                id = id,
                periode = periode,
                opprettet = Tidspunkt.EPOCH,
                epsFormue = null,
                søkersFormue = Formueverdier.empty(),
                behandlingsPeriode = år(2021),
            )

            formueTryCreate shouldBe Formuegrunnlag.create(
                id = id,
                periode = periode,
                opprettet = Tidspunkt.EPOCH,
                epsFormue = null,
                søkersFormue = Formueverdier.empty(),
                behandlingsPeriode = år(2021),
            ).right()
        }
    }

    @Test
    fun `2 formue grunnlag som tilstøter og er lik`() {
        val f1 = lagFormuegrunnlag(periodeInnenfor2021 = januar(2021))
        val f2 = lagFormuegrunnlag(periodeInnenfor2021 = februar(2021))

        f1.tilstøterOgErLik(f2) shouldBe true
    }

    @Test
    fun `2 formue grunnlag som tilstøter ikke, men er lik`() {
        val f1 = lagFormuegrunnlag(periodeInnenfor2021 = januar(2021))
        val f2 = lagFormuegrunnlag(periodeInnenfor2021 = mars(2021))

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue grunnlag som tilstøter, verdier er ulik`() {
        val f1 = lagFormuegrunnlag(periodeInnenfor2021 = januar(2021))
        val f2 = lagFormuegrunnlag(
            periodeInnenfor2021 = februar(2021),
            søkersFormue = formueverdier(
                verdiEiendommer = 100,
            ),
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue grunnlag som tilstøter, men eps verdier er ulik`() {
        val f1 = lagFormuegrunnlag(
            periodeInnenfor2021 = januar(2021),
            epsFormue = formueverdier(verdiEiendommer = 40),
        )
        val f2 = lagFormuegrunnlag(
            periodeInnenfor2021 = februar(2021),
            epsFormue = formueverdier(verdiEiendommer = 100),
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `kopierer innholdet med ny id`() {
        val grunnlag = formueGrunnlagUtenEps0Innvilget()
        grunnlag.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(grunnlag, Formuegrunnlag::id)
            it.id shouldNotBe grunnlag.id
        }
    }

    private fun lagFormuegrunnlag(
        opprettet: Tidspunkt = fixedTidspunkt,
        periodeInnenfor2021: Periode,
        søkersFormue: Formueverdier = Formueverdier.empty(),
        epsFormue: Formueverdier? = null,
    ): Formuegrunnlag {
        return Formuegrunnlag.create(
            opprettet = opprettet,
            periode = periodeInnenfor2021,
            epsFormue = epsFormue,
            søkersFormue = søkersFormue,
            behandlingsPeriode = år(2021),
        )
    }
}
