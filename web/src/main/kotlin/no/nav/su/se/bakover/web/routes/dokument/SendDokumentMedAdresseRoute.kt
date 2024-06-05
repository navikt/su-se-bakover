package no.nav.su.se.bakover.web.routes.dokument

import dokument.domain.brev.KunneIkkeBestilleBrevForDokument
import dokument.domain.distribuering.DistribuerDokumentCommand
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.distribuering.KunneIkkeDistribuereJournalførtDokument
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withDokumentId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.presentation.web.toJson
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import java.util.UUID

fun Route.sendDokumentMedAdresse(
    brevService: DistribuerDokumentService,
) {
    data class Adresse(
        val adresselinje1: String? = null,
        val adresselinje2: String? = null,
        val adresselinje3: String? = null,
        val postnummer: String,
        val poststed: String,
    )

    data class Body(
        val adresse: Adresse,
    ) {
        fun toDomain(
            sakId: UUID,
            dokumentId: UUID,
        ): DistribuerDokumentCommand {
            return DistribuerDokumentCommand(
                sakId = sakId,
                dokumentId = dokumentId,
                distribueringsadresse = Distribueringsadresse(
                    adresselinje1 = adresse.adresselinje1,
                    adresselinje2 = adresse.adresselinje2,
                    adresselinje3 = adresse.adresselinje3,
                    postnummer = adresse.postnummer,
                    poststed = adresse.poststed,
                ),
            )
        }
    }
    post("/sak/{sakId}/dokumenter/{dokumentId}/distribuer") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Drift) {
            this.call.withSakId { sakId ->
                this.call.withDokumentId { dokumentId ->
                    this.call.withBody<Body> { body ->

                        brevService.distribuerDokument(body.toDomain(sakId, dokumentId))
                            .fold(
                                { call.svar(it.toResultat()) },
                                { call.svar(Resultat.json(HttpStatusCode.OK, it.toJson())) },
                            )
                    }
                }
            }
        }
    }
}

fun KunneIkkeDistribuereJournalførtDokument.toResultat(): Resultat {
    return when (this) {
        is KunneIkkeDistribuereJournalførtDokument.AlleredeDistribuert -> {
            HttpStatusCode.BadRequest.errorJson(
                message = "Dokumentet ${this.dokumentId} er allerede distribuert med journalpostId ${this.journalpostId} og brevbestillingId ${this.brevbestillingId}.",
                code = "dokument_allerede_distribuert",
            )
        }

        is KunneIkkeDistribuereJournalførtDokument.IkkeJournalført -> {
            HttpStatusCode.BadRequest.errorJson(
                message = "Dokumentet ${this.dokumentId} er ikke journalført. Dette er en forutsetning for å kunne distribuere dokumentet. Det gjøres automatisk.",
                code = "dokument_ikke_journalført",
            )
        }

        is KunneIkkeDistribuereJournalførtDokument.FantIkkeDokument -> Feilresponser.fantIkkeDokument(this.dokumentId)
        is KunneIkkeDistribuereJournalførtDokument.FeilVedDistribusjon -> when (this.underliggendeFeil) {
            is KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev -> HttpStatusCode.BadRequest.errorJson(
                message = "Feil ved bestilling av brev for dokumentet ${this.dokumentId} med journalpostId ${this.journalpostId} og brevbestillingId ${this.brevbestillingId}.",
                code = "feil_ved_bestilling_av_brev",

            )

            is KunneIkkeBestilleBrevForDokument.MåJournalføresFørst -> {
                HttpStatusCode.BadRequest.errorJson(
                    message = "Dokumentet ${this.dokumentId} er ikke journalført. Dette er en forutsetning for å kunne distribuere dokumentet. Det gjøres automatisk.",
                    code = "dokument_ikke_journalført",
                )
            }
        }

        is KunneIkkeDistribuereJournalførtDokument.IkkeTilgang -> HttpStatusCode.Forbidden.errorJson(
            message = "Du har ikke tilgang til å distribuere dokumentet med $dokumentId",
            code = "ikke_tilgang_til_distribusjon",
        )
    }
}
