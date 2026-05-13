package no.nav.su.se.bakover.client.oppgave

internal data class OppgaveV2Response(
    val id: Long,
    val beskrivelse: String?,
    val status: String,
    val fordeling: Fordeling?,
    val kategorisering: Kategorisering,
) {
    internal data class Fordeling(
        val enhet: Enhet?,
        val mappe: Mappe?,
        val medarbeider: Medarbeider?,
    ) {
        internal data class Enhet(
            val nr: String,
            val navn: String?,
        )

        internal data class Mappe(
            val id: Long?,
            val navn: String?,
        )

        internal data class Medarbeider(
            val ident: String?,
            val navn: String?,
        )
    }

    internal data class Kategorisering(
        val tema: Kodeverkverdi?,
        val oppgavetype: Kodeverkverdi,
        val behandlingstema: Kodeverkverdi?,
        val behandlingstype: Kodeverkverdi?,
    ) {
        internal data class Kodeverkverdi(
            val kode: String,
            val term: String?,
        )
    }
}
