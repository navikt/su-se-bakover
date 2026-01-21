package no.nav.su.se.bakover.domain.klage.relatertId

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.domain.klage.FerdigstiltOmgjortKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("finnRelatertIdForOmgjøring")

sealed interface FantIkkeRelatertKlageId {
    data object MåhaOmgjøringsgrunn : FantIkkeRelatertKlageId
    data object KlageUgyldigUUID : FantIkkeRelatertKlageId
    data object KlageMåFinnesForKnytning : FantIkkeRelatertKlageId
    data object KlageErAlleredeKnyttetTilBehandling : FantIkkeRelatertKlageId
    data object UlikOmgjøringsgrunn : FantIkkeRelatertKlageId
    data object KlageErIkkeFerdigstilt : FantIkkeRelatertKlageId
    data object IngenKlageHendelserFraKA : FantIkkeRelatertKlageId
    data object IngenAvsluttedeKlageHendelserFraKA : FantIkkeRelatertKlageId
    data object IngenTrygderettenAvsluttetHendelser : FantIkkeRelatertKlageId
    data object KlageErIkkeOversendt : FantIkkeRelatertKlageId
    data object KlageErIkkeFerdigstiltOmgjortKlage : FantIkkeRelatertKlageId

    data class UgyldigRevurderingsårsak(
        val feil: Revurderingsårsak.UgyldigRevurderingsårsak,
    ) : FantIkkeRelatertKlageId
}

fun Klage.finnRelatertIdForOmgjøring(
    revurderingsårsak: Revurderingsårsak.Årsak,
    sakId: UUID,
    omgjøringsGrunn: String?,
): Either<FantIkkeRelatertKlageId, KlageId> =
    when (revurderingsårsak) {
        Revurderingsårsak.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN ->
            this.finnRelatertIdFraKlageinstans(sakId)

        Revurderingsårsak.Årsak.OMGJØRING_KLAGE ->
            this.finnRelatertIdFraFerdigstiltOmgjortKlage(
                sakId = sakId,
                omgjøringsGrunn = omgjøringsGrunn,
            )

        Revurderingsårsak.Årsak.OMGJØRING_TRYGDERETTEN ->
            this.finnRelatertIdFraTrygderetten(sakId)

        else -> {
            log.error("Feil årsak $revurderingsårsak for sakid $sakId ved omgjøring")
            FantIkkeRelatertKlageId
                .UgyldigRevurderingsårsak(
                    Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak,
                )
                .left()
        }
    }

private fun Klage.finnRelatertIdFraKlageinstans(
    sakId: UUID,
): Either<FantIkkeRelatertKlageId, KlageId> =
    when (this) {
        is OversendtKlage -> {
            when {
                klageinstanshendelser.isEmpty() -> {
                    log.error("Klage $id er oversendt men har ingen klagehendelser fra KABAL. Sakid: $sakId")
                    FantIkkeRelatertKlageId.IngenKlageHendelserFraKA.left()
                }

                klageinstanshendelser.any { it is ProsessertKlageinstanshendelse.AvsluttetKaMedUtfall } -> {
                    log.info("Fant avsluttet KA-hendelse på klage $id, kan opprette omgjøring.")
                    id.right()
                }

                else -> {
                    log.error("Klage $id er oversendt men har ingen avsluttede klagehendelser fra KABAL. Sakid: $sakId")
                    FantIkkeRelatertKlageId.IngenAvsluttedeKlageHendelserFraKA.left()
                }
            }
        }

        else -> {
            log.error(
                "OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN -> " +
                    "Klage $id er ikke OversendtKlage men ${javaClass.name}. Sakid: $sakId",
            )
            FantIkkeRelatertKlageId.KlageErIkkeOversendt.left()
        }
    }

private fun Klage.finnRelatertIdFraFerdigstiltOmgjortKlage(
    sakId: UUID,
    omgjøringsGrunn: String?,
): Either<FantIkkeRelatertKlageId, KlageId> =
    when (this) {
        is FerdigstiltOmgjortKlage -> {
            when {
                behandlingId != null -> {
                    log.error("Klage $id er allerede knyttet mot $behandlingId. Sakid: $sakId")
                    FantIkkeRelatertKlageId.KlageErAlleredeKnyttetTilBehandling.left()
                }

                vurderinger.vedtaksvurdering.årsak.name != omgjøringsGrunn -> {
                    log.error(
                        "Klage $id har grunn ${vurderinger.vedtaksvurdering.årsak.name}, " +
                            "saksbehandler valgte $omgjøringsGrunn. Sakid: $sakId",
                    )
                    FantIkkeRelatertKlageId.UlikOmgjøringsgrunn.left()
                }

                else -> {
                    log.info("Knytter omgjøring mot klage $id for sakid $sakId")
                    id.right()
                }
            }
        }

        else -> {
            log.error(
                "Klage $id er ikke FerdigstiltOmgjortKlage men ${javaClass.name}. Sakid: $sakId",
            )
            FantIkkeRelatertKlageId.KlageErIkkeFerdigstiltOmgjortKlage.left()
        }
    }

private fun Klage.finnRelatertIdFraTrygderetten(
    sakId: UUID,
): Either<FantIkkeRelatertKlageId, KlageId> =
    when (this) {
        is OversendtKlage -> {
            when {
                klageinstanshendelser.isEmpty() -> {
                    log.error("Klage $id er oversendt men har ingen klagehendelser fra KABAL. Sakid: $sakId")
                    FantIkkeRelatertKlageId.IngenKlageHendelserFraKA.left()
                }

                klageinstanshendelser.any {
                    it is ProsessertKlageinstanshendelse.AnkeITrygderettenAvsluttet
                } -> id.right()

                else -> {
                    log.error(
                        "Klage $id mangler AnkeITrygderettenAvsluttet-hendelse fra KABAL. Sakid: $sakId",
                    )
                    FantIkkeRelatertKlageId.IngenTrygderettenAvsluttetHendelser.left()
                }
            }
        }

        else -> {
            log.error(
                "OMGJØRING_TRYGDERETTEN -> " +
                    "Klage $id er ikke OversendtKlage men ${javaClass.name}. Sakid: $sakId",
            )
            FantIkkeRelatertKlageId.KlageErIkkeOversendt.left()
        }
    }
