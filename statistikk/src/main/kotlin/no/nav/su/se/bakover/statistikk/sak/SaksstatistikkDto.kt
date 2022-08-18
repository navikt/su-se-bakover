package no.nav.su.se.bakover.statistikk.sak

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import no.nav.su.se.bakover.common.Tidspunkt
import java.time.LocalDate
import java.util.UUID

/**
 * @param funksjonellTid  Tidspunktet da hendelsen faktisk ble gjennomført eller registrert i kildesystemet
 * @param tekniskTid  Tidspunktet da kildesystemet ble klar over hendelsen. Denne er oftest lik 'funskjonellTid'
 * @param opprettetDato  Tidspunkt da saken først blir opprettet
 * @param sakId  Nøkkelen til saken i kildesystemet
 * @param aktorId  Aktør IDen til primær mottager av ytelsen om denne blir godkjent
 * @param saksnummer  Saksnummeret tilknyttet saken
 * @param ytelseType  Stønaden eller ytelsen saken omhandler. F.eks "SUUFORE"
 * @param ytelseTypeBeskrivelse  Beskriver den funksjonelle verdien av koden. F.eks "Supplerende stønad".
 * @param sakStatus  Kode som angir sakens status. F.eks OPPRETTET.
 * @param sakStatusBeskrivelse  Beskriver den funksjonelle verdien av koden
 * @param avsender  Feltet angir hvem som er avsender av dataene. Primært su-se-bakover.
 * @param versjon  Angir på hvilken versjon av kildekoden JSON stringen er generert på bakgrunn av
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class SaksstatistikkDto(
    val funksjonellTid: Tidspunkt,
    val tekniskTid: Tidspunkt,
    val opprettetDato: LocalDate,
    val sakId: UUID,
    val aktorId: Long,
    @JsonSerialize(using = ToStringSerializer::class)
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
