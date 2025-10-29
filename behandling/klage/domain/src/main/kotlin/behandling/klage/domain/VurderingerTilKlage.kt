package behandling.klage.domain

import arrow.core.Either
import behandling.klage.domain.VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Oppretthold
import behandling.klage.domain.VurderingerTilKlage.Vedtaksvurdering.Utfylt.SkalTilKabal

/**
 * Støtter kun opprettholdelse i MVP, men vi har støtte for å lagre alle feltene.
 * Validerer at vi kan bekrefte eller sende til attestering.
 */
sealed interface VurderingerTilKlage {

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
         * 1. fritekstTilOversendelsesbrev er null hvis det ikke er omgjøring
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
        val fritekstTilOversendelsesbrev: String?,
        override val vedtaksvurdering: Vedtaksvurdering?,
    ) : VurderingerTilKlage {

        companion object {
            /**
             *
             * [VurderingerTilKlage.Påbegynt] dersom minst en av disse er oppfylt:
             * 1. fritekstTilOversendelsesbrev er null hvis det ikke er omgjøring
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
                return when (vedtaksvurdering) {
                    is Vedtaksvurdering.Utfylt.Omgjør -> {
                        if (vedtaksvurdering.begrunnelse != null) {
                            UtfyltOmgjøring(vedtaksvurdering = vedtaksvurdering)
                        } else {
                            Påbegynt(
                                fritekstTilOversendelsesbrev = null,
                                vedtaksvurdering = vedtaksvurdering,
                            )
                        }
                    }
                    is Vedtaksvurdering.Utfylt.Oppretthold, is Vedtaksvurdering.Utfylt.DelvisOmgjøringKa -> {
                        if (fritekstTilOversendelsesbrev != null && vedtaksvurdering.hjemler.isNotEmpty()) {
                            OversendtKA.create(
                                fritekstTilOversendelsesbrev = fritekstTilOversendelsesbrev,
                                vedtaksvurdering = vedtaksvurdering,
                            )
                        } else {
                            Påbegynt(
                                fritekstTilOversendelsesbrev = fritekstTilOversendelsesbrev,
                                vedtaksvurdering = vedtaksvurdering,
                            )
                        }
                    }
                    null -> Påbegynt(
                        fritekstTilOversendelsesbrev = fritekstTilOversendelsesbrev,
                        vedtaksvurdering = vedtaksvurdering,
                    )
                    is Oppretthold, is Vedtaksvurdering.Påbegynt.DelvisOmgjøringKA, is Vedtaksvurdering.Påbegynt.Omgjør -> Påbegynt(
                        fritekstTilOversendelsesbrev = fritekstTilOversendelsesbrev,
                        vedtaksvurdering = vedtaksvurdering,
                    )
                }
            }
        }
    }

    sealed interface Utfylt : VurderingerTilKlage {
        override val vedtaksvurdering: Vedtaksvurdering.Utfylt
    }

    data class UtfyltOmgjøring(
        override val vedtaksvurdering: Vedtaksvurdering.Utfylt.Omgjør,
    ) : Utfylt

    sealed interface OversendtKA : Utfylt {
        val fritekstTilOversendelsesbrev: String
        override val vedtaksvurdering: SkalTilKabal
        companion object {
            fun create(
                fritekstTilOversendelsesbrev: String,
                vedtaksvurdering: SkalTilKabal,
            ): OversendtKA {
                return when (vedtaksvurdering) {
                    is Vedtaksvurdering.Utfylt.DelvisOmgjøringKa -> UtfyltDelvisOmgjøringKA(
                        fritekstTilOversendelsesbrev = fritekstTilOversendelsesbrev,
                        vedtaksvurdering = vedtaksvurdering,
                    )
                    is Vedtaksvurdering.Utfylt.Oppretthold -> UtfyltOppretthold(
                        fritekstTilOversendelsesbrev = fritekstTilOversendelsesbrev,
                        vedtaksvurdering = vedtaksvurdering,
                    )
                }
            }
        }
    }
    data class UtfyltOppretthold(
        override val fritekstTilOversendelsesbrev: String,
        override val vedtaksvurdering: Vedtaksvurdering.Utfylt.Oppretthold,
    ) : OversendtKA

    data class UtfyltDelvisOmgjøringKA(
        override val fritekstTilOversendelsesbrev: String,
        override val vedtaksvurdering: Vedtaksvurdering.Utfylt.DelvisOmgjøringKa,
    ) : OversendtKA

    sealed interface Vedtaksvurdering {

        companion object {

            /**
             * @return [Vedtaksvurdering.Påbegynt.Omgjør] eller [Vedtaksvurdering.Utfylt.Omgjør]
             */
            fun createOmgjør(årsak: Årsak?, begrunnelse: String?): Vedtaksvurdering {
                return Påbegynt.Omgjør.create(
                    årsak = årsak,
                    begrunnelse = begrunnelse,
                )
            }

            fun createDelvisEllerOpprettholdelse(hjemler: List<Hjemmel>, klagenotat: String?, erOppretthold: Boolean): Either<Klagehjemler.KunneIkkeLageHjemler, Vedtaksvurdering> {
                return Klagehjemler.tryCreate(hjemler).map {
                    when (it) {
                        is Klagehjemler.IkkeUtfylt -> {
                            Påbegynt.OversendtTilKA.create(erOppretthold = erOppretthold, hjemler = it, klagenotat = klagenotat)
                        }
                        is Klagehjemler.Utfylt -> {
                            SkalTilKabal.create(erOppretthold = erOppretthold, hjemler = it, klagenotat = klagenotat)
                        }
                    }
                }
            }
        }

        sealed interface Påbegynt : Vedtaksvurdering {
            data class Omgjør private constructor(val årsak: Årsak?, val begrunnelse: String?) : Påbegynt {

                companion object {
                    /**
                     * Bruk heller [Vedtaksvurdering.createOmgjør]
                     *
                     * @return [Vedtaksvurdering.Påbegynt.Omgjør] eller [Vedtaksvurdering.Utfylt.Omgjør]
                     */
                    internal fun create(
                        årsak: Årsak?,
                        begrunnelse: String? = null,
                    ): Vedtaksvurdering {
                        return if (årsak != null) {
                            Utfylt.Omgjør(
                                årsak = årsak,
                                begrunnelse = begrunnelse,
                            )
                        } else {
                            Omgjør(
                                årsak = årsak,
                                begrunnelse = begrunnelse,
                            )
                        }
                    }
                }
            }
            interface OversendtTilKA {
                val hjemler: Klagehjemler.IkkeUtfylt
                val klagenotat: String?
                companion object {
                    fun create(
                        erOppretthold: Boolean,
                        hjemler: Klagehjemler.IkkeUtfylt,
                        klagenotat: String?,
                    ): Vedtaksvurdering {
                        return if (erOppretthold) {
                            Oppretthold(hjemler = hjemler, klagenotat = klagenotat)
                        } else {
                            DelvisOmgjøringKA(hjemler = hjemler, klagenotat = klagenotat)
                        }
                    }
                }
            }
            data class Oppretthold(
                override val hjemler: Klagehjemler.IkkeUtfylt,
                override val klagenotat: String?,
            ) : Påbegynt,
                OversendtTilKA

            data class DelvisOmgjøringKA(
                override val hjemler: Klagehjemler.IkkeUtfylt,
                override val klagenotat: String?,
            ) : Påbegynt,
                OversendtTilKA
        }

        sealed interface Utfylt : Vedtaksvurdering {
            data class Omgjør(val årsak: Årsak, val begrunnelse: String?) : Utfylt
            sealed interface SkalTilKabal : Utfylt {
                val hjemler: Klagehjemler.Utfylt
                val klagenotat: String?

                companion object {
                    fun create(
                        erOppretthold: Boolean,
                        hjemler: Klagehjemler.Utfylt,
                        klagenotat: String?,
                    ): Vedtaksvurdering {
                        return if (erOppretthold) {
                            Oppretthold(hjemler = hjemler, klagenotat = klagenotat)
                        } else {
                            DelvisOmgjøringKa(hjemler = hjemler, klagenotat = klagenotat)
                        }
                    }
                }
            }
            data class Oppretthold(override val hjemler: Klagehjemler.Utfylt, override val klagenotat: String?) : SkalTilKabal
            data class DelvisOmgjøringKa(override val hjemler: Klagehjemler.Utfylt, override val klagenotat: String?) : SkalTilKabal
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
    }
}
