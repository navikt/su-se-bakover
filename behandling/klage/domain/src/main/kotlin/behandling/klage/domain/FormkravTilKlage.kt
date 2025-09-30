package behandling.klage.domain

import java.util.UUID

/**
 * Inneholder kun selve vilkårsvurderingene/formkravene som er gjort i forbindelse med en klage.
 * For selve klagen se [VilkårsvurdertKlage]
 */
sealed interface FormkravTilKlage {

    val vedtakId: UUID?
    val innenforFristen: Svarord?
    val klagesDetPåKonkreteElementerIVedtaket: Boolean?
    val erUnderskrevet: Svarord?

    enum class Svarord {
        JA,
        NEI_MEN_SKAL_VURDERES,
        NEI,
    }

    fun erAvvist(): Boolean {
        return this.klagesDetPåKonkreteElementerIVedtaket == false ||
            this.innenforFristen == Svarord.NEI ||
            this.erUnderskrevet == Svarord.NEI
    }

    /**
     * Bruk [FormkravTilKlage.create] som returnerer [FormkravTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [FormkravTilKlage.Påbegynt]
     */
    data class Påbegynt private constructor(
        override val vedtakId: UUID?,
        override val innenforFristen: Svarord?,
        override val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
        override val erUnderskrevet: Svarord?,
    ) : FormkravTilKlage {
        companion object {

            fun empty(): Påbegynt {
                // Går via create for å verifisere at vi bruker de samme reglene.
                return create(
                    vedtakId = null,
                    innenforFristen = null,
                    klagesDetPåKonkreteElementerIVedtaket = null,
                    erUnderskrevet = null,
                ) as Påbegynt
            }

            /**
             * Denne styrer hvilken klagetype vi får se [VilkårsvurdertKlage] når man går videre i klageflyten
             * @return [FormkravTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [FormkravTilKlage.Påbegynt]
             */
            internal fun create(
                vedtakId: UUID?,
                innenforFristen: Svarord?,
                klagesDetPåKonkreteElementerIVedtaket: Boolean?,
                erUnderskrevet: Svarord?,
            ): FormkravTilKlage {
                val erAlleFelterUtfylt = listOf(
                    vedtakId,
                    innenforFristen,
                    klagesDetPåKonkreteElementerIVedtaket,
                    erUnderskrevet,
                ).all {
                    it != null
                }
                // TODO: fjern begrunnelse herifra som aldri er bruk
                return if (erAlleFelterUtfylt) {
                    createUtfyltOnly(
                        vedtakId = vedtakId!!,
                        innenforFristen = innenforFristen!!,
                        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket!!,
                        erUnderskrevet = erUnderskrevet!!,
                    )
                } else {
                    Påbegynt(
                        vedtakId = vedtakId,
                        innenforFristen = innenforFristen,
                        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                        erUnderskrevet = erUnderskrevet,
                    )
                }
            }
        }
    }

    data class Utfylt(
        override val vedtakId: UUID,
        override val innenforFristen: Svarord,
        override val klagesDetPåKonkreteElementerIVedtaket: Boolean,
        override val erUnderskrevet: Svarord,
    ) : FormkravTilKlage

    companion object {

        fun empty(): Påbegynt {
            return Påbegynt.empty()
        }

        private fun createUtfyltOnly(
            vedtakId: UUID,
            innenforFristen: Svarord,
            klagesDetPåKonkreteElementerIVedtaket: Boolean,
            erUnderskrevet: Svarord,
        ): FormkravTilKlage {
            return Utfylt(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
            )
        }

        /**
         * @return [FormkravTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [FormkravTilKlage.Påbegynt]
         */
        fun create(
            vedtakId: UUID?,
            innenforFristen: Svarord?,
            klagesDetPåKonkreteElementerIVedtaket: Boolean?,
            erUnderskrevet: Svarord?,
        ): FormkravTilKlage {
            return Påbegynt.create(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
            )
        }
    }
}
