package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

data class KontrollsamtaleReiseDato(
    val utreiseDato: LocalDate,
    val innreiseDato: LocalDate,
)
data class KontrollsamtaleNotat(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt,
    val personligOppmøte: Boolean,
    val fullmaktOgLegeerklæring: Boolean,
    val originalPass: Boolean,
    val gyldigPass: Boolean,
    val harVærtUtenlands: Boolean,
    val utenlandsoppholdDatoer: List<KontrollsamtaleReiseDato>,
    val harPlanerOmUtenlandsreise: Boolean,
    val planlagteUtenlandsreiseDatoer: List<KontrollsamtaleReiseDato>,
    val reiseDokumentasjon: Boolean,
    val økonomiskSituasjon: Boolean,
    val andreForhold: Boolean,
    val skatteOpplysninger: Boolean,
    val fritekst: String?,

)
