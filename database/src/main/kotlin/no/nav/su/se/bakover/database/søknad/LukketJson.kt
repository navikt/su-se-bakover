package no.nav.su.se.bakover.database.søknad

import dokument.database.BrevvalgDbJson
import dokument.database.BrevvalgDbJson.Companion.toJson
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknad.Søknad
import java.time.LocalDate

internal data class LukketJson(
    val tidspunkt: Tidspunkt,
    val saksbehandler: String,
    val type: Type,
    val brevvalg: BrevvalgDbJson,
    val trukketDato: LocalDate?,
) {

    fun toBrevvalg(): Brevvalg {
        return brevvalg.toDomain()
    }
    companion object {
        fun Søknad.Journalført.MedOppgave.Lukket.toLukketJson(): String = LukketJson(
            tidspunkt = this.lukketTidspunkt,
            saksbehandler = this.lukketAv.toString(),
            type = when (this) {
                is Søknad.Journalført.MedOppgave.Lukket.Bortfalt -> Type.BORTFALT
                is Søknad.Journalført.MedOppgave.Lukket.Avvist -> Type.AVVIST
                is Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker -> Type.TRUKKET
            },
            brevvalg = brevvalg.toJson(),
            trukketDato = (this as? Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker)?.trukketDato,
        ).let { serialize(it) }
    }

    enum class Type {
        BORTFALT,
        AVVIST,
        TRUKKET,
    }
}
