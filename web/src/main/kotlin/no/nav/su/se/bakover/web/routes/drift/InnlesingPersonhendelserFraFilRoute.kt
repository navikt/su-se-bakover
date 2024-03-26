package no.nav.su.se.bakover.web.routes.drift

import arrow.core.Either
import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
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
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService

internal fun Route.innlesingPersonhendelserFraFilRoute(
    personhendelseService: PersonhendelseService,
) {
    post("$DRIFT_PATH/personhendelser") {
        authorize(Brukerrolle.Drift) {
            call.request.headers["content-type"]?.contains("multipart/form-data")
                // TODO: return error if content-type is not multipart/form-data
                ?: throw IllegalStateException("Missing content-type multipart/form-data")

            val parts = call.receiveMultipart()

            when (val thePart = parts.readAllParts().single()) {
                is PartData.FileItem -> {
                    val fileBytes = thePart.streamProvider().readBytes()
                    val fileAsString = String(fileBytes)

                    Either.catch {
                        deserializeList<PersonhendelseJson>(fileAsString).mapNotNull {
                            it.toDomain().getOrNull()
                        }.forEach {
                            personhendelseService.prosesserNyHendelse(it)
                        }
                        call.svar(Resultat.okJson())
                    }.getOrElse {
                        call.svar(
                            HttpStatusCode.BadRequest.errorJson(
                                "Deserialisering av personhendelser feilet",
                                "deserialisering_av_personhendelser_feilet",
                            ),
                        )
                    }
                }

                else -> call.svar(Feilresponser.ukjentMultipartType)
            }
        }
    }

    post("$DRIFT_PATH/personhendelser/dry") {
        authorize(Brukerrolle.Drift) {
            call.request.headers["content-type"]?.contains("multipart/form-data")
                // TODO: return error if content-type is not multipart/form-data
                ?: throw IllegalStateException("Missing content-type multipart/form-data")

            val parts = call.receiveMultipart()

            when (val thePart = parts.readAllParts().single()) {
                is PartData.FileItem -> {
                    val fileBytes = thePart.streamProvider().readBytes()
                    val fileAsString = String(fileBytes)

                    Either.catch {
                        val personhendelser = deserializeList<PersonhendelseJson>(fileAsString).mapNotNull {
                            it.toDomain().getOrNull()
                        }
                        personhendelseService.dryRunPersonhendelser(personhendelser)

                        call.svar(Resultat.okJson())
                    }.getOrElse {
                        call.svar(
                            HttpStatusCode.BadRequest.errorJson(
                                "Deserialisering av personhendelser feilet",
                                "deserialisering_av_personhendelser_feilet",
                            ),
                        )
                    }
                }

                else -> call.svar(Feilresponser.ukjentMultipartType)
            }
        }
    }
}
