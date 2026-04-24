package no.nav.su.se.bakover.domain.oppgave

import java.time.LocalDate

data class OppgaveV2Config(
    val beskrivelse: String,
    val kategorisering: Kategorisering,
    val bruker: Bruker? = null,
    val aktivDato: LocalDate? = null,
    val fristDato: LocalDate? = null,
    val prioritet: Prioritet? = null,
    val fordeling: Fordeling? = null,
    val nokkelord: List<String> = emptyList(),
    val arkivreferanse: Arkivreferanse? = null,
    val tilknyttetSystem: String? = null,
    val meta: Meta? = null,
) {
    init {
        require(bruker != null || arkivreferanse != null) {
            "Minst en av bruker eller arkivreferanse må være satt"
        }
        requireGyldigFritekst("beskrivelse", beskrivelse)
        meta?.kommentar?.let { requireGyldigFritekst("meta.kommentar", it) }
        arkivreferanse?.let {
            require(it.saksnr != null || it.journalpostId != null) {
                "Arkivreferanse må inneholde minst saksnr eller journalpostId"
            }
        }
    }

    data class Kategorisering(
        val tema: Kode,
        val oppgavetype: Kode,
        val behandlingstema: Kode? = null,
        val behandlingstype: Kode? = null,
    )

    data class Kode(
        val kode: String,
    )

    data class Bruker(
        val ident: String,
        val type: Type,
    ) {
        enum class Type {
            PERSON,
        }
    }

    data class Fordeling(
        val enhet: Enhet? = null,
        val mappe: Mappe? = null,
        val medarbeider: Medarbeider? = null,
    ) {
        data class Enhet(
            val nr: String,
        )

        data class Mappe(
            val id: Long,
        )

        data class Medarbeider(
            val navident: String,
        )
    }

    data class Arkivreferanse(
        val saksnr: String? = null,
        val journalpostId: String? = null,
    )

    data class Meta(
        val kommentar: String? = null,
    )

    enum class Prioritet {
        NORMAL,
        HOY,
        LAV,
    }

    private fun requireGyldigFritekst(feltnavn: String, verdi: String) {
        require(verdi.length in 2..2500) {
            "$feltnavn må være mellom 2 og 2500 tegn"
        }
        require(!verdi.contains("---")) {
            "$feltnavn kan ikke inneholde ---"
        }
    }
}
