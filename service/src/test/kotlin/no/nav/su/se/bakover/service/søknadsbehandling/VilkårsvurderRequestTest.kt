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

    private val bosituasjonUtenEPS = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
    )
    private val bosituasjonMedEPS = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        fnr = Fnr.generer(),
    )

    @Test
    fun `validerer request OK`() {
        val request = VilkårsvurderRequest(
            behandlingId = UUID.randomUUID(),
            behandlingsinformasjon = Behandlingsinformasjon(
                flyktning = null,
                lovligOpphold = null,
                fastOppholdINorge = null,
                institusjonsopphold = null,
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

        request.hentValidertBehandlingsinformasjon(bosituasjonUtenEPS) shouldBe
            Behandlingsinformasjon(
                flyktning = null,
                lovligOpphold = null,
                fastOppholdINorge = null,
                institusjonsopphold = null,
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
        val request = VilkårsvurderRequest(
            behandlingId = UUID.randomUUID(),
            behandlingsinformasjon = Behandlingsinformasjon(
                flyktning = null,
                lovligOpphold = null,
                fastOppholdINorge = null,
                institusjonsopphold = null,
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

        request.hentValidertBehandlingsinformasjon(bosituasjonUtenEPS) shouldBe
            VilkårsvurderRequest.FeilVedValideringAvBehandlingsinformasjon.DepositumErHøyereEnnInnskudd.left()
    }

    @Test
    fun `får feil dersom det finnes eps, men har ikke epsVerdier`() {
        val request = VilkårsvurderRequest(
            behandlingId = UUID.randomUUID(),
            behandlingsinformasjon = Behandlingsinformasjon(
                flyktning = null,
                lovligOpphold = null,
                fastOppholdINorge = null,
                institusjonsopphold = null,
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

        request.hentValidertBehandlingsinformasjon(bosituasjonMedEPS) shouldBe
            VilkårsvurderRequest.FeilVedValideringAvBehandlingsinformasjon.BosituasjonOgFormueForEpsErIkkeKonsistent.left()
    }

    @Test
    fun `får feil dersom det ikke finnes eps, men har epsVerdier`() {
        val request = VilkårsvurderRequest(
            behandlingId = UUID.randomUUID(),
            behandlingsinformasjon = Behandlingsinformasjon(
                flyktning = null,
                lovligOpphold = null,
                fastOppholdINorge = null,
                institusjonsopphold = null,
                formue = Behandlingsinformasjon.Formue(
                    status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                    verdier = Behandlingsinformasjon.Formue.Verdier(
                        verdiIkkePrimærbolig = 0, verdiEiendommer = 0,
                        verdiKjøretøy = 0, innskudd = 0,
                        verdipapir = 0, pengerSkyldt = 0,
                        kontanter = 0, depositumskonto = 0,
                    ),
                    epsVerdier = Behandlingsinformasjon.Formue.Verdier(
                        verdiIkkePrimærbolig = 0, verdiEiendommer = 0,
                        verdiKjøretøy = 0, innskudd = 0,
                        verdipapir = 0, pengerSkyldt = 0,
                        kontanter = 0, depositumskonto = 0,
                    ),
                    begrunnelse = null,
                ),
                personligOppmøte = null,
            ),
        )

        request.hentValidertBehandlingsinformasjon(bosituasjonUtenEPS) shouldBe
            VilkårsvurderRequest.FeilVedValideringAvBehandlingsinformasjon.BosituasjonOgFormueForEpsErIkkeKonsistent.left()
    }
}
