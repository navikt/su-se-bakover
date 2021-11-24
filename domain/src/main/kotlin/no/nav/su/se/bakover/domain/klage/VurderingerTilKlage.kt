package no.nav.su.se.bakover.domain.klage

import arrow.core.Either

/**
 * Støtter kun opprettholdelse i MVP, men vi har støtte for å lagre alle feltene.
 * Validerer at vi kan bekrefte eller sende til attestering.
 */
sealed class VurderingerTilKlage {

    abstract val fritekstTilBrev: String?
    abstract val vedtaksvurdering: Vedtaksvurdering?

    companion object {

        fun empty(): Påbegynt {
            // Går via create for å verifisere at vi bruker de samme reglene.
            return create(
                fritekstTilBrev = null,
                vedtaksvurdering = null,
            ) as Påbegynt
        }

        /**
         * [VurderingerTilKlage.Påbegynt] dersom minst en av disse er oppfylt:
         * 1. fritekstTilBrev er null
         * 2. vedtaksvurdering er null
         * 3. vedtaksvurdering er [Vedtaksvurdering.Påbegynt]
         *
         * Ellers [VurderingerTilKlage.Utfylt]
         *
         * @param vedtaksvurdering En [VurderingerTilKlage.Påbegynt] kan inneholde enten en [Vedtaksvurdering.Påbegynt] eller [Vedtaksvurdering.Utfylt]
         * */
        fun create(
            fritekstTilBrev: String?,
            vedtaksvurdering: Vedtaksvurdering?,
        ): VurderingerTilKlage {
            return Påbegynt.create(
                fritekstTilBrev = fritekstTilBrev,
                vedtaksvurdering = vedtaksvurdering,
            )
        }
    }

    /**
     * Påbegynt dersom en av disse er oppfylt:
     * 1. fritekstTilBrev er null
     * 2. vedtaksvurdering er null
     * 3. vedtaksvurdering er [Vedtaksvurdering.Påbegynt]
     */
    data class Påbegynt private constructor(
        override val fritekstTilBrev: String?,
        override val vedtaksvurdering: Vedtaksvurdering?,
    ) : VurderingerTilKlage() {

        companion object {
            /**
             * [VurderingerTilKlage.Påbegynt] dersom minst en av disse er oppfylt:
             * 1. fritekstTilBrev er null
             * 2. vedtaksvurdering er null
             * 3. vedtaksvurdering er [Vedtaksvurdering.Påbegynt]
             *
             * Ellers [VurderingerTilKlage.Utfylt]
             *
             * @param vedtaksvurdering En [VurderingerTilKlage.Påbegynt] kan inneholde enten en [Vedtaksvurdering.Påbegynt] eller [Vedtaksvurdering.Utfylt]
             * */
            internal fun create(
                fritekstTilBrev: String?,
                vedtaksvurdering: Vedtaksvurdering?,
            ): VurderingerTilKlage {
                val erUtfylt =
                    fritekstTilBrev != null && vedtaksvurdering != null && vedtaksvurdering is Vedtaksvurdering.Utfylt
                return if (erUtfylt) {
                    Utfylt(
                        fritekstTilBrev = fritekstTilBrev!!,
                        vedtaksvurdering = vedtaksvurdering!! as Vedtaksvurdering.Utfylt,
                    )
                } else {
                    Påbegynt(
                        fritekstTilBrev = fritekstTilBrev,
                        vedtaksvurdering = vedtaksvurdering,
                    )
                }
            }
        }
    }

    data class Utfylt(
        override val fritekstTilBrev: String,
        override val vedtaksvurdering: Vedtaksvurdering.Utfylt,
    ) : VurderingerTilKlage()

    sealed class Vedtaksvurdering {

        companion object {

            /**
             * @return [Vedtaksvurdering.Påbegynt.Omgjør] eller [Vedtaksvurdering.Utfylt.Omgjør]
             */
            fun createOmgjør(årsak: Årsak?, utfall: Utfall?): Vedtaksvurdering {
                return Påbegynt.Omgjør.create(
                    årsak = årsak,
                    utfall = utfall,
                )
            }

            fun createOppretthold(hjemler: List<Hjemmel>): Either<Hjemler.KunneIkkeLageHjemler, Vedtaksvurdering> {
                return Hjemler.tryCreate(hjemler).map {
                    when (it) {
                        is Hjemler.IkkeUtfylt -> Påbegynt.Oppretthold(hjemler = it)
                        is Hjemler.Utfylt -> Utfylt.Oppretthold(hjemler = it)
                    }
                }
            }
        }

        sealed class Påbegynt : Vedtaksvurdering() {
            data class Omgjør private constructor(val årsak: Årsak?, val utfall: Utfall?) : Påbegynt() {

                companion object {
                    /**
                     * Bruk heller [Vedtaksvurdering.createOmgjør]
                     *
                     * @return [Vedtaksvurdering.Påbegynt.Omgjør] eller [Vedtaksvurdering.Utfylt.Omgjør]
                     */
                    internal fun create(
                        årsak: Årsak?,
                        utfall: Utfall?,
                    ): Vedtaksvurdering {
                        return if (årsak != null && utfall != null) {
                            Utfylt.Omgjør(
                                årsak = årsak,
                                utfall = utfall,
                            )
                        } else {
                            Omgjør(
                                årsak = årsak,
                                utfall = utfall,
                            )
                        }
                    }
                }
            }

            data class Oppretthold(val hjemler: Hjemler.IkkeUtfylt) : Påbegynt()
        }

        sealed class Utfylt : Vedtaksvurdering() {
            data class Omgjør(val årsak: Årsak, val utfall: Utfall) : Utfylt()
            data class Oppretthold(val hjemler: Hjemler.Utfylt) : Utfylt()
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
