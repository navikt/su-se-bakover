package no.nav.su.se.bakover.web.routes.skatt

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withFnr
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeLageJournalpostUtenforSak
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.service.skatt.FrioppslagSkattRequest
import no.nav.su.se.bakover.service.skatt.KunneIkkeGenerereSkattePdfOgJournalføre
import no.nav.su.se.bakover.service.skatt.KunneIkkeHenteOgLagePdfAvSkattegrunnlag
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.web.routes.person.tilResultat
import java.time.Year

internal const val skattPath = "/skatt"

internal fun Route.skattRoutes(skatteService: SkatteService) {
    data class FrioppslagRequestBody(
        val år: Int,
        val begrunnelse: String,
        val sakstype: String,
        val fagsystemId: String,
    ) {
        /**
         * fagsystemId & begrunnelse kan være tom string - Dette er ment for forhåndsvisning
         */
        fun tilFrioppslagSkattRequest(
            fnr: Fnr,
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): FrioppslagSkattRequest = FrioppslagSkattRequest(
            fnr = fnr,
            år = Year.of(år),
            begrunnelse = begrunnelse,
            saksbehandler = saksbehandler,
            sakstype = Sakstype.from(sakstype),
            fagsystemId = fagsystemId,
        )
    }

    post("$skattPath/person/{fnr}/forhandsvis") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withFnr { fnr ->
                call.withBody<FrioppslagRequestBody> { body ->
                    skatteService.hentOgLagSkattePdf(
                        request = body.tilFrioppslagSkattRequest(fnr, call.suUserContext.saksbehandler),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                            call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }

    post("$skattPath/person/{fnr}") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withFnr { fnr ->
                call.withBody<FrioppslagRequestBody> { body ->
                    skatteService.hentLagOgJournalførSkattePdf(
                        request = body.tilFrioppslagSkattRequest(fnr, call.suUserContext.saksbehandler),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                            call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }
}

internal fun KunneIkkeGenerereSkattePdfOgJournalføre.tilResultat(): Resultat = when (this) {
    is KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedGenereringAvPdf -> this.originalFeil.tilResultat()
    is KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvSkattemelding -> this.originalFeil.tilResultat()
    is KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedJournalføring -> this.originalFeil.tilResultat()
    is KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedJournalpostUtenforSak -> this.originalFeil.tilResultat()
}

internal fun KunneIkkeLageJournalpostUtenforSak.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLageJournalpostUtenforSak.FagsystemIdErTom -> HttpStatusCode.BadRequest.errorJson(
            "Ugyldig data - FagsystemId er tom",
            "fagsystemId_er_tom",
        )
    }
}

internal fun KunneIkkeJournalføreDokument.tilResultat(): Resultat = when (this) {
    KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost -> Feilresponser.feilVedOpprettelseAvJournalpost
    KunneIkkeJournalføreDokument.KunneIkkeFinnePerson -> Feilresponser.fantIkkePerson
}

internal fun KunneIkkeHenteOgLagePdfAvSkattegrunnlag.tilResultat(): Resultat = when (this) {
    is KunneIkkeHenteOgLagePdfAvSkattegrunnlag.FeilVedHentingAvPerson -> this.originalFeil.tilResultat()
    is KunneIkkeHenteOgLagePdfAvSkattegrunnlag.FeilVedPdfGenerering -> ErrorJson(
        "Feil ved generering av pdf",
        "feil_ved_generering_av_pdf",
    ).tilResultat(HttpStatusCode.InternalServerError)

    is KunneIkkeHenteOgLagePdfAvSkattegrunnlag.KunneIkkeHenteSkattemelding -> this.originalFeil.tilResultat()
}

internal fun KunneIkkeHenteSkattemelding.tilResultat(): Resultat = when (this) {
    KunneIkkeHenteSkattemelding.FinnesIkke -> this.tilErrorJson().tilResultat(HttpStatusCode.NotFound)
    KunneIkkeHenteSkattemelding.ManglerRettigheter -> this.tilErrorJson().tilResultat(HttpStatusCode.Forbidden)
    KunneIkkeHenteSkattemelding.Nettverksfeil -> this.tilErrorJson().tilResultat(HttpStatusCode.InternalServerError)
    KunneIkkeHenteSkattemelding.PersonFeil -> this.tilErrorJson().tilResultat(HttpStatusCode.InternalServerError)
    KunneIkkeHenteSkattemelding.UkjentFeil -> this.tilErrorJson().tilResultat(HttpStatusCode.InternalServerError)
}

internal fun KunneIkkeHenteSkattemelding.tilErrorJson(): ErrorJson = when (this) {
    is KunneIkkeHenteSkattemelding.FinnesIkke -> ErrorJson(
        "Ingen summert skattegrunnlag funnet på oppgitt fødselsnummer og inntektsår",
        "ingen_skattegrunnlag_for_gitt_fnr_og_år",
    )

    KunneIkkeHenteSkattemelding.ManglerRettigheter -> ErrorJson(
        "Autentiserings- eller autoriseringsfeil mot Sigrun/Skatteetaten. Mangler bruker noen rettigheter?",
        "mangler_rettigheter_mot_skatt",
    )

    KunneIkkeHenteSkattemelding.Nettverksfeil -> ErrorJson(
        "Får ikke kontakt med Sigrun/Skatteetaten. Prøv igjen senere.",
        "nettverksfeil_skatt",
    )

    KunneIkkeHenteSkattemelding.PersonFeil -> ErrorJson(
        "Personfeil ved oppslag",
        "feil_ved_oppslag_person",
    )

    KunneIkkeHenteSkattemelding.UkjentFeil -> ErrorJson(
        "Uforventet feil oppstod ved kall til Sigrun/Skatteetaten.",
        "uforventet_feil_mot_skatt",
    )
}
