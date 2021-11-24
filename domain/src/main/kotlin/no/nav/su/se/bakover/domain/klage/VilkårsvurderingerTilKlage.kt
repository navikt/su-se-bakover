package no.nav.su.se.bakover.domain.klage

import java.util.UUID

/**
 * Inneholder kun selve vilkårsvurderingene som er gjort i forbindelse med en klage.
 * For selve klagen se [VilkårsvurdertKlage]
 */
sealed class VilkårsvurderingerTilKlage {

    abstract val vedtakId: UUID?
    abstract val innenforFristen: Boolean?
    abstract val klagesDetPåKonkreteElementerIVedtaket: Boolean?
    abstract val erUnderskrevet: Boolean?
    abstract val begrunnelse: String?

    /**
     * Bruk [VilkårsvurderingerTilKlage.create] som returnerer [VilkårsvurderingerTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [VilkårsvurderingerTilKlage.Påbegynt]
     */
    data class Påbegynt private constructor(
        override val vedtakId: UUID?,
        override val innenforFristen: Boolean?,
        override val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
        override val erUnderskrevet: Boolean?,
        override val begrunnelse: String?,
    ) : VilkårsvurderingerTilKlage() {
        companion object {

            fun empty(): Påbegynt {
                // Går via create for å verifisere at vi bruker de samme reglene.
                return create(
                    vedtakId = null,
                    innenforFristen = null,
                    klagesDetPåKonkreteElementerIVedtaket = null,
                    erUnderskrevet = null,
                    begrunnelse = null,
                ) as Påbegynt
            }

            /**
             * @return [VilkårsvurderingerTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [VilkårsvurderingerTilKlage.Påbegynt]
             */
            internal fun create(
                vedtakId: UUID?,
                innenforFristen: Boolean?,
                klagesDetPåKonkreteElementerIVedtaket: Boolean?,
                erUnderskrevet: Boolean?,
                begrunnelse: String?,
            ): VilkårsvurderingerTilKlage {
                val erAlleFelterUtfylt = listOf(
                    vedtakId,
                    innenforFristen,
                    klagesDetPåKonkreteElementerIVedtaket,
                    erUnderskrevet,
                    begrunnelse,
                ).all {
                    it != null
                }
                return if (erAlleFelterUtfylt) {
                    Utfylt(
                        vedtakId = vedtakId!!,
                        innenforFristen = innenforFristen!!,
                        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket!!,
                        erUnderskrevet = erUnderskrevet!!,
                        begrunnelse = begrunnelse!!,
                    )
                } else {
                    Påbegynt(
                        vedtakId = vedtakId,
                        innenforFristen = innenforFristen,
                        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                        erUnderskrevet = erUnderskrevet,
                        begrunnelse = begrunnelse,
                    )
                }
            }
        }
    }

    data class Utfylt(
        override val vedtakId: UUID,
        override val innenforFristen: Boolean,
        override val klagesDetPåKonkreteElementerIVedtaket: Boolean,
        override val erUnderskrevet: Boolean,
        override val begrunnelse: String,
    ) : VilkårsvurderingerTilKlage()

    companion object {

        fun empty(): Påbegynt {
            return Påbegynt.empty()
        }

        /**
         * @return [VilkårsvurderingerTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [VilkårsvurderingerTilKlage.Påbegynt]
         */
        fun create(
            vedtakId: UUID?,
            innenforFristen: Boolean?,
            klagesDetPåKonkreteElementerIVedtaket: Boolean?,
            erUnderskrevet: Boolean?,
            begrunnelse: String?,
        ): VilkårsvurderingerTilKlage {
            return Påbegynt.create(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
                begrunnelse = begrunnelse,
            )
        }
    }
}
