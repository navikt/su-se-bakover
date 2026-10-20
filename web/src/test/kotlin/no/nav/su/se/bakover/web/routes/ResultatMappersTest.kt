package no.nav.su.se.bakover.web.routes

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.domain.oppdrag.simulering.KontrollsimuleringFeilet
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.web.routes.regulering.tilResultat
import org.junit.jupiter.api.Test
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetalinger

internal class ResultatMappersTest {
    @Test
    fun `kvitteringsfeil bevares gjennom kontrollsimulering og manuell regulering`() {
        listOf(
            Utbetalinger.Kvitteringsfeil.AvventerKvittering to ErrorJson(
                message = "En tidligere utbetaling mangler kvittering fra økonomisystemet. Du kan ikke simulere en ny utbetaling ennå. Prøv igjen senere.",
                code = "utbetaling_avventer_kvittering",
            ),
            Utbetalinger.Kvitteringsfeil.Feilkvittering to ErrorJson(
                message = "Økonomisystemet har svart med feil på en tidligere utbetaling. Dette må avklares før du kan simulere en ny utbetaling.",
                code = "utbetaling_har_feilkvittering",
            ),
        ).forEach { (kvitteringsfeil, forventet) ->
            val feil = SimuleringFeilet.Utbetalingskvittering(kvitteringsfeil)
            listOf(
                feil.tilResultat(),
                KontrollsimuleringFeilet.KunneIkkeSimulere(feil).tilResultat(),
                KunneIkkeRegulereManuelt.Utbetalingskvittering(feil).tilResultat(),
            ).forEach { resultat ->
                resultat.httpCode shouldBe HttpStatusCode.Conflict
                deserialize<ErrorJson>(resultat.json) shouldBe forventet
            }
        }
    }
}
