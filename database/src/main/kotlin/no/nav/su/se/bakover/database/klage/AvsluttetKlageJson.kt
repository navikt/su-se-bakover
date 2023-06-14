package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage

data class AvsluttetKlageJson(
    val begrunnelse: String,
    val tidspunktAvsluttet: Tidspunkt,
) {
    companion object {
        fun fromJsonString(json: String): AvsluttetKlageJson = deserialize(json)

        fun AvsluttetKlage.toAvsluttetKlageJson(): String {
            return serialize(
                AvsluttetKlageJson(
                    begrunnelse = begrunnelse,
                    tidspunktAvsluttet = avsluttetTidspunkt,
                ),
            )
        }
    }
}
