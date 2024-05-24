package no.nav.su.se.bakover.web.routes.skatt

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.brev.KunneIkkeJournalføreDokument
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.web.routes.person.tilResultat
import vilkår.skatt.application.FrioppslagSkattRequest
import vilkår.skatt.application.KunneIkkeGenerereSkattePdfOgJournalføre
import vilkår.skatt.application.KunneIkkeHenteOgLagePdfAvSkattegrunnlag
import vilkår.skatt.application.SkatteService
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import vilkår.skatt.domain.journalpost.KunneIkkeLageJournalpostUtenforSak
import java.time.Year

internal const val SKATTE_PATH = "/skatt"

sealed interface FrioppslagValideringsFeil {
    data object KreverAtMinstEtFnrSendesInn : FrioppslagValideringsFeil
    data object InntektsårErFør2020 : FrioppslagValideringsFeil

    fun tilResultat(): Resultat = when (this) {
        InntektsårErFør2020 -> HttpStatusCode.BadRequest.errorJson(
            "Inntektsåret som ble forespurt er før 2020. Vi har kun avtale å hente fra 2020",
            "inntektsår_før_2020",
        )

        KreverAtMinstEtFnrSendesInn -> HttpStatusCode.BadRequest.errorJson(
            "Krever at fnr eller epsFnr sendes inn. Eventuelt begge",
            "krever_at_minst_et_fnr_sendes_inn",
        )
    }
}

internal fun Route.skattRoutes(skatteService: SkatteService) {
    data class FrioppslagRequestBody(
        val fnr: String?,
        val epsFnr: String?,
        val år: Int,
        val begrunnelse: String,
        val sakstype: String,
        val fagsystemId: String,
    ) {
        /**
         * fagsystemId & begrunnelse kan være tom string - Dette er ment for forhåndsvisning
         */
        fun tilFrioppslagSkattRequest(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Either<FrioppslagValideringsFeil, FrioppslagSkattRequest> {
            if (fnr == null && epsFnr == null) {
                return FrioppslagValideringsFeil.KreverAtMinstEtFnrSendesInn.left()
            }

            return FrioppslagSkattRequest(
                fnr = fnr?.let { Fnr.tryCreate(it) },
                epsFnr = epsFnr?.let { Fnr.tryCreate(it) },
                år = if (år < 2020) return FrioppslagValideringsFeil.InntektsårErFør2020.left() else Year.of(år),
                begrunnelse = begrunnelse,
                saksbehandler = saksbehandler,
                sakstype = Sakstype.from(sakstype),
                fagsystemId = fagsystemId,
            ).right()
        }
    }

    post("$SKATTE_PATH/forhandsvis") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withBody<FrioppslagRequestBody> { body ->
                body.tilFrioppslagSkattRequest(call.suUserContext.saksbehandler).fold(
                    {
                        call.svar(it.tilResultat())
                    },
                    { request ->
                        skatteService.hentOgLagSkattePdf(
                            request = request,
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                request.fnr?.let { call.audit(it, AuditLogEvent.Action.SEARCH, null) }
                                request.epsFnr?.let { call.audit(it, AuditLogEvent.Action.SEARCH, null) }

                                call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                            },
                        )
                    },
                )
            }
        }
    }

    post(SKATTE_PATH) {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withBody<FrioppslagRequestBody> { body ->
                body.tilFrioppslagSkattRequest(call.suUserContext.saksbehandler).fold(
                    {
                        call.svar(it.tilResultat())
                    },
                    { request ->
                        skatteService.hentLagOgJournalførSkattePdf(
                            request = request,
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                request.fnr?.let { call.audit(it, AuditLogEvent.Action.SEARCH, null) }
                                request.epsFnr?.let { call.audit(it, AuditLogEvent.Action.SEARCH, null) }
                                call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                            },
                        )
                    },
                )
            }
        }
    }
}

internal fun KunneIkkeGenerereSkattePdfOgJournalføre.tilResultat(): Resultat = when (this) {
    is KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedGenereringAvPdf -> this.originalFeil.tilResultat()
    is KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvSkattemelding -> this.originalFeil.tilResultat()
    is KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedJournalføring -> this.originalFeil.tilResultat()
    is KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedJournalpostUtenforSak -> this.originalFeil.tilResultat()
    KunneIkkeGenerereSkattePdfOgJournalføre.FantIkkeSak -> fantIkkeSak
    is KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvPerson -> it.tilResultat()
    KunneIkkeGenerereSkattePdfOgJournalføre.FnrPåSakErIkkeLikFnrViFikkFraPDL -> HttpStatusCode.BadRequest.errorJson(
        "Fødselsnummer som er registrert på sak er ikke lik den vi fikk fra PDL",
        "forespurt_fnr_på_sak_ikke_lik_fnr_fra_pdl",
    )

    is KunneIkkeGenerereSkattePdfOgJournalføre.SakstypeErIkkeDenSammeSomForespurt -> HttpStatusCode.BadRequest.errorJson(
        "Faktisk sakstype er ${this.faktiskSakstype}, forespurt sakstype er ${this.forespurtSakstype}",
        "faktisk_sakstype_er_ikke_lik_forespurt_sakstype",
    )

    KunneIkkeGenerereSkattePdfOgJournalføre.UføresaksnummerKanIkkeBrukesForAlder -> HttpStatusCode.BadRequest.errorJson(
        "Saksnummer som er for uføre, kan ikke brukes når man skal hente for en alderssak",
        "uføre_saksnummer_kan_ikke_brukes_for_alderssak",
    )

    KunneIkkeGenerereSkattePdfOgJournalføre.FikkTilbakeEtAnnetFnrFraPdlEnnDetSomBleSendtInn -> HttpStatusCode.BadRequest.errorJson(
        "Fikk tilbake et annet fnr fra PDL enn det som ble sendt inn. Er det som er sendt in et historisk fødselsnummer?",
        "pdl_returnerte_annet_fnr_enn_det_som_blev_sendt_inn",
    )
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
    KunneIkkeHenteSkattemelding.UkjentFeil -> this.tilErrorJson().tilResultat(HttpStatusCode.InternalServerError)
    KunneIkkeHenteSkattemelding.OppslagetInneholdtUgyldigData -> this.tilErrorJson()
        .tilResultat(HttpStatusCode.BadRequest)
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

    KunneIkkeHenteSkattemelding.UkjentFeil -> ErrorJson(
        "Uforventet feil oppstod ved kall til Sigrun/Skatteetaten.",
        "uforventet_feil_mot_skatt",
    )

    KunneIkkeHenteSkattemelding.OppslagetInneholdtUgyldigData -> ErrorJson(
        "Inntektsåret som ble forespurt er før 2020. Vi har kun avtale å hente fra 2020",
        "inntektsår_før_2020",
    )
}
