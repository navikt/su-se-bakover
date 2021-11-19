package no.nav.su.se.bakover.domain.klage

/**
 * Støtter kun opprettholdelse i MVP, men vi har støtte for å lagre alle feltene.
 * Validerer at vi kan bekrefte eller sende til attestering.
 */
sealed class VurderingerTilKlage {

    abstract val fritekstTilBrev: String?
    abstract val vedtaksvurdering: Vedtaksvurdering?

    data class Påbegynt(
        override val fritekstTilBrev: String?,
        /** En påbegynt vurderingerTilKlage kan inneholde både en påbegynt eller utfylt vedtaksvurdering. */
        override val vedtaksvurdering: Vedtaksvurdering?,
    ) : VurderingerTilKlage()

    data class Utfylt(
        override val fritekstTilBrev: String,
        override val vedtaksvurdering: Vedtaksvurdering.Utfylt,
    ) : VurderingerTilKlage()

    sealed class Vedtaksvurdering {
        sealed class Påbegynt : Vedtaksvurdering() {
            data class Omgjør(val årsak: Årsak?, val utfall: Utfall?) : Påbegynt()
            data class Oppretthold(val hjemler: Hjemler) : Påbegynt()
        }

        sealed class Utfylt : Vedtaksvurdering() {
            data class Omgjør(val årsak: Årsak, val utfall: Utfall) : Utfylt()
            data class Oppretthold(val hjemler: Hjemler) : Utfylt()
        }

        /** Kopiert fra K9 */
        enum class Årsak {
            FEIL_LOVANVENDELSE,
            ULIK_SKJØNNSVURDERING,
            SAKSBEHANDLINGSFEIL,
            NYTT_FAKTUM;
        }

        /** Kopiert fra K9 */
        enum class Utfall {
            TIL_GUNST,
            TIL_UGUNST,
        }
    }
}
