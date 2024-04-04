package no.nav.su.se.bakover.web.routes.drift

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.toMåned
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import java.time.LocalDate

internal fun Route.innlesingPersonhendelserFraFilRoute(
    personhendelseService: PersonhendelseService,
) {
    post("$DRIFT_PATH/personhendelser") {
        authorize(Brukerrolle.Drift) {
            call.request.headers["content-type"]?.contains("multipart/form-data")
                // TODO: return error if content-type is not multipart/form-data
                ?: throw IllegalStateException("Missing content-type multipart/form-data")

            val parts = call.receiveMultipart()

            val (fraOgMed, personhendelser) = getDataFromMultipartOrResultat(parts).getOrElse {
                call.svar(it)
                return@authorize
            }

            personhendelser.forEach {
                personhendelseService.prosesserNyHendelse(fraOgMed, it)
            }
            call.svar(Resultat.okJson())
        }
    }

    post("$DRIFT_PATH/personhendelser/dry") {
        authorize(Brukerrolle.Drift) {
            call.request.headers["content-type"]?.contains("multipart/form-data")
                // TODO: return error if content-type is not multipart/form-data
                ?: throw IllegalStateException("Missing content-type multipart/form-data")

            val parts = call.receiveMultipart()

            val (fraOgMed, personhendelser) = getDataFromMultipartOrResultat(parts).getOrElse {
                call.svar(it)
                return@authorize
            }

            personhendelseService.dryRunPersonhendelser(fraOgMed, personhendelser)

            call.svar(Resultat.okJson())
        }
    }
}

private suspend fun getDataFromMultipartOrResultat(
    parts: MultiPartData,
): Either<Resultat, Pair<Måned, List<Personhendelse.IkkeTilknyttetSak>>> {
    // forventer at første parten er datoen
    val fraOgMed = parts.readPart().let {
        when (it) {
            is PartData.FormItem -> {
                Either.catch {
                    LocalDate.parse(it.value).toMåned().right()
                }.getOrElse {
                    HttpStatusCode.BadRequest.errorJson(
                        "Måned er ikke gyldig",
                        "måned_er_ikke_gyldig",
                    ).left()
                }
            }

            else -> return Feilresponser.ukjentMultipartType.left()
        }
    }.getOrElse { return it.left() }

    // forventer at andre parten er hendelsene
    val personhendelser = parts.readPart().let {
        when (it) {
            is PartData.FileItem -> {
                val fileBytes = it.streamProvider().readBytes()
                val fileAsString = String(fileBytes)
                Either.catch {
                    deserializeList<PersonhendelseJson>(fileAsString).mapNotNull {
                        it.toDomain().getOrNull()
                    }.right()
                }.getOrElse {
                    HttpStatusCode.BadRequest.errorJson(
                        "Deserialisering av personhendelser feilet",
                        "deserialisering_av_personhendelser_feilet",
                    ).left()
                }
            }

            else -> return Feilresponser.ukjentMultipartType.left()
        }
    }.getOrElse { return it.left() }

    return Pair(fraOgMed, personhendelser).right()
}
