package no.nav.su.se.bakover.statistikk.sak

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

/**
 * Se `src/resources/sak_schema.json` for dokumentasjon.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class SaksstatistikkDto(
    val funksjonellTid: Tidspunkt,
    val tekniskTid: Tidspunkt,
    val opprettetDato: LocalDate,
    val sakId: UUID,
    val aktorId: Long,
    @param:JsonSerialize(using = ToStringSerializer::class)
    val saksnummer: Long,
    val ytelseType: String = "SUUFORE",
    val ytelseTypeBeskrivelse: String? = "Supplerende stønad for uføre flyktninger",
    val sakStatus: String = "OPPRETTET",
    val sakStatusBeskrivelse: String? = null,
    val avsender: String = "su-se-bakover",
    val versjon: String?,
    val aktorer: List<Aktør>? = null,
    val underType: String? = null,
    val underTypeBeskrivelse: String? = null,
) {
    data class Aktør(
        val aktorId: Int,
        val rolle: String,
        val rolleBeskrivelse: String,
    )
}
