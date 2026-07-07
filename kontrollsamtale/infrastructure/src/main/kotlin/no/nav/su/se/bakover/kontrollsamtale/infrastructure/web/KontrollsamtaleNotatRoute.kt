package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotat
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotatService
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleReiseDato
import java.time.Clock
import java.time.LocalDate

fun Route.kontrollsamtaleNotatRoute(
    kontrollsamtaleNotatService: KontrollsamtaleNotatService,
    clock: Clock,
) {
    data class ReiseDatoBody(
        val utreiseDato: LocalDate,
        val innreiseDato: LocalDate,
    )

    data class Body(
        val personligOppmøte: Boolean,
        val fullmaktOgLegeerklæring: Boolean?,
        val originalPass: Boolean,
        val gyldigPass: Boolean,
        val harVærtUtenlands: Boolean,
        val utenlandsoppholdDatoer: List<ReiseDatoBody>,
        val harPlanerOmUtenlandsreise: Boolean,
        val planlagteUtenlandsreiseDatoer: List<ReiseDatoBody>,
        val reiseDokumentasjon: Boolean,
        val økonomiskSituasjon: Boolean,
        val andreForhold: Boolean,
        val skatteOpplysninger: Boolean,
        val fritekst: String?,
    )

    post("/saker/{sakId}/kontrollsamtaler/notat") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    val notat = KontrollsamtaleNotat(
                        personligOppmøte = body.personligOppmøte,
                        fullmaktOgLegeerklæring = body.fullmaktOgLegeerklæring,
                        originalPass = body.originalPass,
                        gyldigPass = body.gyldigPass,
                        harVærtUtenlands = body.harVærtUtenlands,
                        utenlandsoppholdDatoer = body.utenlandsoppholdDatoer.map {
                            KontrollsamtaleReiseDato(
                                utreiseDato = it.utreiseDato,
                                innreiseDato = it.innreiseDato,
                            )
                        },
                        harPlanerOmUtenlandsreise = body.harPlanerOmUtenlandsreise,
                        planlagteUtenlandsreiseDatoer = body.planlagteUtenlandsreiseDatoer.map {
                            KontrollsamtaleReiseDato(
                                utreiseDato = it.utreiseDato,
                                innreiseDato = it.innreiseDato,
                            )
                        },
                        reiseDokumentasjon = body.reiseDokumentasjon,
                        økonomiskSituasjon = body.økonomiskSituasjon,
                        andreForhold = body.andreForhold,
                        skatteOpplysninger = body.skatteOpplysninger,
                        opprettet = Tidspunkt.now(clock),
                        fritekst = body.fritekst,
                    )
                    kontrollsamtaleNotatService.lagre(
                        kontrollsamtaleNotat = notat,
                        sakId = sakId,
                    )

                    call.svar(Resultat.okJson())
                }
            }
        }
    }
}
