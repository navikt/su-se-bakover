package no.nav.su.se.bakover.web.routes.kontrollsamtale

import arrow.core.getOrElse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toUUID
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleReiseDato
import no.nav.su.se.bakover.kontrollsamtale.domain.kontrollnotat.KontrollsamtaleNotatService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal data class ReiseDatoBody(
    val utreiseDato: LocalDate,
    val innreiseDato: LocalDate,
) {
    fun toDomain(): KontrollsamtaleReiseDato {
        return KontrollsamtaleReiseDato(
            utreiseDato = utreiseDato,
            innreiseDato = innreiseDato,
        )
    }
}

internal data class KontrollNotatDto(
    val id: String,
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
) {
    fun toDomain(
        kontrollnotatId: UUID,
        sakId: UUID,
        opprettet: Tidspunkt,
    ): KontrollsamtaleNotat {
        return KontrollsamtaleNotat(
            id = kontrollnotatId,
            sakId = sakId,
            personligOppmøte = personligOppmøte,
            fullmaktOgLegeerklæring = fullmaktOgLegeerklæring,
            originalPass = originalPass,
            gyldigPass = gyldigPass,
            harVærtUtenlands = harVærtUtenlands,
            utenlandsoppholdDatoer = utenlandsoppholdDatoer.map { it.toDomain() },
            harPlanerOmUtenlandsreise = harPlanerOmUtenlandsreise,
            planlagteUtenlandsreiseDatoer = planlagteUtenlandsreiseDatoer.map { it.toDomain() },
            reiseDokumentasjon = reiseDokumentasjon,
            økonomiskSituasjon = økonomiskSituasjon,
            andreForhold = andreForhold,
            skatteOpplysninger = skatteOpplysninger,
            opprettet = opprettet,
            fritekst = fritekst,
        )
    }
}

fun Route.kontrollsamtaleNotatRoute(
    kontrollsamtaleNotatService: KontrollsamtaleNotatService,
    clock: Clock,
) {
    post("/saker/{sakId}/kontrollsamtaler/notat") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<KontrollNotatDto> { dto ->
                    val kontrollnotatId = dto.id.toUUID().getOrElse {
                        return@authorize call.svar(
                            BadRequest.errorJson(
                                message = "kontrollnotatId er ikke en gyldig UUID",
                                code = "ikke_gyldig_uuid",
                            ),
                        )
                    }
                    val notat = dto.toDomain(
                        kontrollnotatId = kontrollnotatId,
                        sakId = sakId,
                        opprettet = Tidspunkt.now(clock),
                    )
                    val resultat = kontrollsamtaleNotatService.lagre(
                        kontrollsamtaleNotat = notat,
                        sakId = sakId,
                    ).map {
                        Resultat.okJson()
                    }.getOrElse {
                        when (it) {
                            KontrollsamtaleNotatService.KontrollnotatIdAlleredeBrukt ->
                                Conflict.errorJson(
                                    message = "kontrollnotatId er allerede brukt på en annen sak",
                                    code = "kontrollnotat_id_allerede_brukt",
                                )
                            is KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost ->
                                InternalServerError.errorJson(
                                    message = it.grunn,
                                    code = "kunne_ikke_opprette_journalpost",
                                )
                        }
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    get("/saker/{sakId}/kontrollsamtaler/notat/pdf") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                kontrollsamtaleNotatService.hentKontrollsamtaleNotatPdf(sakId).fold(
                    ifLeft = {
                        val responseMessage = when (it) {
                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkeSak -> fantIkkeSak
                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.KunneIkkeLagePdf ->
                                InternalServerError.errorJson(
                                    message = "Kunne ikke lage pdf",
                                    code = "kunne_ikke_lage_pdf",

                                )
                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkePerson ->
                                Feilresponser.fantIkkePerson
                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkeKontrollnotat ->
                                Feilresponser.fantIkkeKontrollnotat
                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.KunneIkkeGenerereForside ->
                                InternalServerError.errorJson(
                                    message = "Kunne ikke generere forside",
                                    code = "kunne_ikke_generere_forside",
                                )
                        }
                        call.svar(resultat = responseMessage)
                    },
                    ifRight = {
                        call.respondBytes(
                            bytes = it.getContent(),
                            contentType = ContentType.Application.Pdf,
                        )
                    },
                )
            }
        }
    }
}
