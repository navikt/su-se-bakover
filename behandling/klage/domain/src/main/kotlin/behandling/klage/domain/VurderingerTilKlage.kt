package behandling.klage.domain

import arrow.core.Either

/**
 * Støtter kun opprettholdelse i MVP, men vi har støtte for å lagre alle feltene.
 * Validerer at vi kan bekrefte eller sende til attestering.
 */
sealed interface VurderingerTilKlage {

    val fritekstTilOversendelsesbrev: String?
    val vedtaksvurdering: Vedtaksvurdering?

    companion object {

        fun empty(): Påbegynt {
            // Går via create for å verifisere at vi bruker de samme reglene.
            return create(
                fritekstTilOversendelsesbrev = null,
                vedtaksvurdering = null,
            ) as Påbegynt
        }

        /**
         * [VurderingerTilKlage.Påbegynt] dersom minst en av disse er oppfylt:
         * 1. fritekstTilOversendelsesbrev er null
         * 2. vedtaksvurdering er null
         * 3. vedtaksvurdering er [Vedtaksvurdering.Påbegynt]
         *
         * Ellers [VurderingerTilKlage.Utfylt]
         *
         * @param vedtaksvurdering En [VurderingerTilKlage.Påbegynt] kan inneholde enten en [Vedtaksvurdering.Påbegynt] eller [Vedtaksvurdering.Utfylt]
         * */
        fun create(
            fritekstTilOversendelsesbrev: String?,
            vedtaksvurdering: Vedtaksvurdering?,
        ): VurderingerTilKlage {
            return Påbegynt.create(
                fritekstTilOversendelsesbrev = fritekstTilOversendelsesbrev,
                vedtaksvurdering = vedtaksvurdering,
            )
        }
    }

    /**
     * Påbegynt dersom en av disse er oppfylt:
     * 1. fritekstTilOversendelsesbrev er null
     * 2. vedtaksvurdering er null
     * 3. vedtaksvurdering er [Vedtaksvurdering.Påbegynt]
     */
    data class Påbegynt private constructor(
        override val fritekstTilOversendelsesbrev: String?,
        override val vedtaksvurdering: Vedtaksvurdering?,
    ) : VurderingerTilKlage {

        companion object {
            /**
             * [VurderingerTilKlage.Påbegynt] dersom minst en av disse er oppfylt:
             * 1. fritekstTilOversendelsesbrev er null
             * 2. vedtaksvurdering er null
             * 3. vedtaksvurdering er [Vedtaksvurdering.Påbegynt]
             *
             * Ellers [VurderingerTilKlage.Utfylt]
             *
             * @param vedtaksvurdering En [VurderingerTilKlage.Påbegynt] kan inneholde enten en [Vedtaksvurdering.Påbegynt] eller [Vedtaksvurdering.Utfylt]
             * */
            fun create(
                fritekstTilOversendelsesbrev: String?,
                vedtaksvurdering: Vedtaksvurdering?,
            ): VurderingerTilKlage {
                val erUtfylt = when (vedtaksvurdering) {
                    is Vedtaksvurdering.Utfylt.Omgjør -> {
                        fritekstTilOversendelsesbrev == null && vedtaksvurdering.begrunnelse != null
                    }
                    is Vedtaksvurdering.Utfylt.Oppretthold -> {
                        fritekstTilOversendelsesbrev != null && vedtaksvurdering.hjemler.isNotEmpty()
                    }
                    null -> false
                    is Vedtaksvurdering.Påbegynt.Omgjør -> false
                    is Vedtaksvurdering.Påbegynt.Oppretthold -> false
                }
                return if (erUtfylt) {
                    Utfylt(
                        fritekstTilOversendelsesbrev = fritekstTilOversendelsesbrev,
                        vedtaksvurdering = vedtaksvurdering!! as Vedtaksvurdering.Utfylt,
                    )
                } else {
                    Påbegynt(
                        fritekstTilOversendelsesbrev = fritekstTilOversendelsesbrev,
                        vedtaksvurdering = vedtaksvurdering,
                    )
                }
            }
        }
    }

    data class Utfylt(
        override val fritekstTilOversendelsesbrev: String?,
        override val vedtaksvurdering: Vedtaksvurdering.Utfylt,
    ) : VurderingerTilKlage

    sealed interface Vedtaksvurdering {

        companion object {

            /**
             * @return [Vedtaksvurdering.Påbegynt.Omgjør] eller [Vedtaksvurdering.Utfylt.Omgjør]
             */
            fun createOmgjør(årsak: Årsak?, utfall: Utfall?, begrunnelse: String?): Vedtaksvurdering {
                return Påbegynt.Omgjør.create(
                    årsak = årsak,
                    utfall = utfall,
                    begrunnelse = begrunnelse,
                )
            }

            fun createOppretthold(hjemler: List<Hjemmel>): Either<Klagehjemler.KunneIkkeLageHjemler, Vedtaksvurdering> {
                return Klagehjemler.tryCreate(hjemler).map {
                    when (it) {
                        is Klagehjemler.IkkeUtfylt -> Påbegynt.Oppretthold(hjemler = it)
                        is Klagehjemler.Utfylt -> Utfylt.Oppretthold(hjemler = it)
                    }
                }
            }
        }

        sealed interface Påbegynt : Vedtaksvurdering {
            data class Omgjør private constructor(val årsak: Årsak?, val utfall: Utfall?, val begrunnelse: String?) : Påbegynt {

                companion object {
                    /**
                     * Bruk heller [Vedtaksvurdering.createOmgjør]
                     *
                     * @return [Vedtaksvurdering.Påbegynt.Omgjør] eller [Vedtaksvurdering.Utfylt.Omgjør]
                     */
                    internal fun create(
                        årsak: Årsak?,
                        utfall: Utfall?,
                        begrunnelse: String? = null,
                    ): Vedtaksvurdering {
                        return if (årsak != null && utfall != null) {
                            Utfylt.Omgjør(
                                årsak = årsak,
                                utfall = utfall,
                                begrunnelse = begrunnelse,
                            )
                        } else {
                            Omgjør(
                                årsak = årsak,
                                utfall = utfall,
                                begrunnelse = begrunnelse,
                            )
                        }
                    }
                }
            }

            data class Oppretthold(val hjemler: Klagehjemler.IkkeUtfylt) : Påbegynt
        }

        sealed interface Utfylt : Vedtaksvurdering {
            data class Omgjør(val årsak: Årsak, val utfall: Utfall, val begrunnelse: String?) : Utfylt
            data class Oppretthold(val hjemler: Klagehjemler.Utfylt) : Utfylt
        }

        // Se også Omgjøringsgrunn - må se om de skal konsolideres evt fjernes fra behandlingsløpet hvis det lagres her. Blir da kun historisk
        enum class Årsak {
            FEIL_LOVANVENDELSE,
            NYE_OPPLYSNINGER,
            FEIL_REGELFORSTÅELSE,
            FEIL_FAKTUM,
            ;

            companion object {
                fun toDomain(dbValue: String): Årsak {
                    return entries.find { it.name == dbValue }
                        ?: throw IllegalStateException("Ukjent klageårsak i klage-tabellen: $dbValue")
                }
            }
        }

        enum class Utfall {
            TIL_GUNST,
            TIL_UGUNST,
            ;

            companion object {
                fun toDomain(dbValue: String): Utfall {
                    return entries.find { it.name == dbValue }
                        ?: throw IllegalStateException("Ukjent klage utfall i klage-tabellen: $dbValue")
                }
            }
        }
    }
}
