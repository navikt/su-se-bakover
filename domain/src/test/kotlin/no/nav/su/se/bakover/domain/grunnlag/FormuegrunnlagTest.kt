package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.periode2021
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
                    fnr = Fnr.generer(),
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
            ) shouldBe KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden.left()
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

            formueTryCreate shouldBe Formuegrunnlag.create(
                id = id,
                periode = periode,
                opprettet = Tidspunkt.EPOCH,
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                begrunnelse = null,
                bosituasjon = enslig,
                behandlingsPeriode = Periode.create(1.januar(2021), 31.desember(2021)),
            ).right()
        }
    }

    @Test
    fun `2 formue grunnlag som tilstøter og er lik`() {
        val f1 = lagFormuegrunnlag(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormuegrunnlag(periodeInnenfor2021 = Periode.create(1.februar(2021), 28.februar(2021)))

        f1.tilstøterOgErLik(f2) shouldBe true
    }

    @Test
    fun `2 formue grunnlag som tilstøter ikke, men er lik`() {
        val f1 = lagFormuegrunnlag(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormuegrunnlag(periodeInnenfor2021 = Periode.create(1.mars(2021), 31.mars(2021)))

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue grunnlag som tilstøter, verdier er ulik`() {
        val f1 = lagFormuegrunnlag(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormuegrunnlag(
            periodeInnenfor2021 = Periode.create(1.februar(2021), 28.februar(2021)),
            søkersFormue = Formuegrunnlag.Verdier.empty().copy(
                verdiEiendommer = 100,
            ),
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue grunnlag som tilstøter, begrunnelse er ulik`() {
        val f1 = lagFormuegrunnlag(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormuegrunnlag(
            periodeInnenfor2021 = Periode.create(1.februar(2021), 28.februar(2021)),
            begrunnelse = "denne er ikke lik den første",
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue grunnlag som tilstøter, men eps verdier er ulik`() {
        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode2021,
            fnr = Fnr.generer(),
            begrunnelse = null,
        )
        val f1 = lagFormuegrunnlag(
            periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)),
            bosiutasjon = bosituasjon,
        )
        val f2 = lagFormuegrunnlag(
            epsFormue = Formuegrunnlag.Verdier.empty().copy(
                verdiEiendommer = 100,
            ),
            periodeInnenfor2021 = Periode.create(1.februar(2021), 28.februar(2021)),
            bosiutasjon = bosituasjon,
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    private fun lagFormuegrunnlag(
        opprettet: Tidspunkt = fixedTidspunkt,
        periodeInnenfor2021: Periode,
        søkersFormue: Formuegrunnlag.Verdier = Formuegrunnlag.Verdier.empty(),
        epsFormue: Formuegrunnlag.Verdier? = null,
        begrunnelse: String? = null,
        bosiutasjon: Grunnlag.Bosituasjon.Fullstendig = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periodeInnenfor2021,
            begrunnelse = null,
        ),
    ): Formuegrunnlag {
        return Formuegrunnlag.create(
            opprettet = opprettet,
            periode = periodeInnenfor2021,
            epsFormue = epsFormue,
            søkersFormue = søkersFormue,
            begrunnelse = begrunnelse,
            bosituasjon = bosiutasjon,
            behandlingsPeriode = Periode.create(1.januar(2021), 31.desember(2021)),
        )
    }
}
