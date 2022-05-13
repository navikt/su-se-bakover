package no.nav.su.se.bakover.domain.brev

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FormueForBrevTest {

    private val periode = år(2021)

    @Test
    fun `regner ut formuen riktig`() {
        val formuegrunnlag = Formuegrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            epsFormue = Formuegrunnlag.Verdier.create(
                verdiIkkePrimærbolig = 10,
                verdiEiendommer = 10,
                verdiKjøretøy = 10,
                innskudd = 10,
                verdipapir = 10,
                pengerSkyldt = 10,
                kontanter = 10,
                depositumskonto = 10,
            ),
            søkersFormue = Formuegrunnlag.Verdier.create(
                verdiIkkePrimærbolig = 10,
                verdiEiendommer = 10,
                verdiKjøretøy = 10,
                innskudd = 10,
                verdipapir = 10,
                pengerSkyldt = 10,
                kontanter = 10,
                depositumskonto = 10,
            ),
            bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                fnr = Fnr.generer(),
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
            ),
            behandlingsPeriode = periode,
        )

        formuegrunnlag.tilFormueForBrev() shouldBe FormueForBrev(
            søkersFormue = FormueVerdierForBrev(
                verdiSekundærBoliger = 20,
                verdiSekundærKjøretøyer = 10,
                pengerIBanken = 10,
                depositumskonto = 10,
                pengerIKontanter = 10,
                aksjerOgVerdiPapir = 10,
                pengerSøkerSkyldes = 10,
            ),
            epsFormue = FormueVerdierForBrev(
                verdiSekundærBoliger = 20,
                verdiSekundærKjøretøyer = 10,
                pengerIBanken = 10,
                depositumskonto = 10,
                pengerIKontanter = 10,
                aksjerOgVerdiPapir = 10,
                pengerSøkerSkyldes = 10,
            ),
            totalt = 120,
        )
    }

    @Test
    fun `regner ut verdiene av formuen riktig`() {
        val verdier = Formuegrunnlag.Verdier.create(
            verdiIkkePrimærbolig = 10,
            verdiEiendommer = 10,
            verdiKjøretøy = 10,
            innskudd = 10,
            verdipapir = 10,
            pengerSkyldt = 10,
            kontanter = 10,
            depositumskonto = 10,
        )

        verdier.tilFormueVerdierForBrev() shouldBe FormueVerdierForBrev(
            verdiSekundærBoliger = 20,
            verdiSekundærKjøretøyer = 10,
            pengerIBanken = 10,
            depositumskonto = 10,
            pengerIKontanter = 10,
            aksjerOgVerdiPapir = 10,
            pengerSøkerSkyldes = 10,
        )
    }
}
