package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VilkårsvurderRequestTest {

    @Test
    fun `validerer request OK`() {
        val request = SøknadsbehandlingService.VilkårsvurderRequest(
            behandlingId = UUID.randomUUID(),
            behandlingsinformasjon = Behandlingsinformasjon(
                uførhet = null, flyktning = null, lovligOpphold = null, fastOppholdINorge = null,
                institusjonsopphold = null, oppholdIUtlandet = null,
                formue = Behandlingsinformasjon.Formue(
                    status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                    verdier = Behandlingsinformasjon.Formue.Verdier(
                        verdiIkkePrimærbolig = 0, verdiEiendommer = 0,
                        verdiKjøretøy = 0, innskudd = 0,
                        verdipapir = 0, pengerSkyldt = 0,
                        kontanter = 0, depositumskonto = 0,
                    ),
                    epsVerdier = null, begrunnelse = null,
                ),
                personligOppmøte = null,
            ),
        )

        request.hentValidertBehandlingsinformasjon(bosituasjon = null) shouldBe
            Behandlingsinformasjon(
                uførhet = null, flyktning = null, lovligOpphold = null, fastOppholdINorge = null,
                institusjonsopphold = null, oppholdIUtlandet = null,
                formue = Behandlingsinformasjon.Formue(
                    status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                    verdier = Behandlingsinformasjon.Formue.Verdier(
                        verdiIkkePrimærbolig = 0, verdiEiendommer = 0,
                        verdiKjøretøy = 0, innskudd = 0,
                        verdipapir = 0, pengerSkyldt = 0,
                        kontanter = 0, depositumskonto = 0,
                    ),
                    epsVerdier = null, begrunnelse = null,
                ),
                personligOppmøte = null,
            ).right()
    }

    @Test
    fun `får feil dersom depositum er høyere enn innskudd ved validering`() {
        val request = SøknadsbehandlingService.VilkårsvurderRequest(
            behandlingId = UUID.randomUUID(),
            behandlingsinformasjon = Behandlingsinformasjon(
                uførhet = null, flyktning = null, lovligOpphold = null, fastOppholdINorge = null,
                institusjonsopphold = null, oppholdIUtlandet = null,
                formue = Behandlingsinformasjon.Formue(
                    status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                    verdier = Behandlingsinformasjon.Formue.Verdier(
                        verdiIkkePrimærbolig = 0, verdiEiendommer = 0,
                        verdiKjøretøy = 0, innskudd = 10,
                        verdipapir = 0, pengerSkyldt = 0,
                        kontanter = 0, depositumskonto = 20,
                    ),
                    epsVerdier = null, begrunnelse = null,
                ),
                personligOppmøte = null,
            ),
        )

        request.hentValidertBehandlingsinformasjon(bosituasjon = null) shouldBe
            SøknadsbehandlingService.FeilVedValideringAvBehandlingsinformasjon.DepositumIkkeMindreEnnInnskudd.left()
    }

    @Test
    fun `får feil dersom det finnes eps verdier, men har ikke eps i bosituasjon`() {
        val request = SøknadsbehandlingService.VilkårsvurderRequest(
            behandlingId = UUID.randomUUID(),
            behandlingsinformasjon = Behandlingsinformasjon(
                uførhet = null, flyktning = null, lovligOpphold = null, fastOppholdINorge = null,
                institusjonsopphold = null, oppholdIUtlandet = null,
                formue = Behandlingsinformasjon.Formue(
                    status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                    verdier = Behandlingsinformasjon.Formue.Verdier(
                        verdiIkkePrimærbolig = 0, verdiEiendommer = 0,
                        verdiKjøretøy = 0, innskudd = 0,
                        verdipapir = 0, pengerSkyldt = 0,
                        kontanter = 0, depositumskonto = 0,
                    ),
                    epsVerdier = null, begrunnelse = null,
                ),
                personligOppmøte = null,
            ),
        )

        val bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            fnr = Fnr.generer(),
        )

        request.hentValidertBehandlingsinformasjon(bosituasjon) shouldBe
            SøknadsbehandlingService.FeilVedValideringAvBehandlingsinformasjon.HarEPSVerdierUtenEPS.left()
    }
}
