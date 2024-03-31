package behandling.klage.domain

import java.util.UUID

/**
 * Inneholder kun selve vilkårsvurderingene som er gjort i forbindelse med en klage.
 * For selve klagen se [VilkårsvurdertKlage]
 */
sealed interface VilkårsvurderingerTilKlage {

    val vedtakId: UUID?
    val innenforFristen: Svarord?
    val klagesDetPåKonkreteElementerIVedtaket: Boolean?
    val erUnderskrevet: Svarord?
    val begrunnelse: String?

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
     * Bruk [VilkårsvurderingerTilKlage.create] som returnerer [VilkårsvurderingerTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [VilkårsvurderingerTilKlage.Påbegynt]
     */
    data class Påbegynt private constructor(
        override val vedtakId: UUID?,
        override val innenforFristen: Svarord?,
        override val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
        override val erUnderskrevet: Svarord?,
        override val begrunnelse: String?,
    ) : VilkårsvurderingerTilKlage {
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
                innenforFristen: Svarord?,
                klagesDetPåKonkreteElementerIVedtaket: Boolean?,
                erUnderskrevet: Svarord?,
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
        override val innenforFristen: Svarord,
        override val klagesDetPåKonkreteElementerIVedtaket: Boolean,
        override val erUnderskrevet: Svarord,
        override val begrunnelse: String,
    ) : VilkårsvurderingerTilKlage

    companion object {

        fun empty(): Påbegynt {
            return Påbegynt.empty()
        }

        /**
         * @return [VilkårsvurderingerTilKlage.Utfylt] dersom alle feltene er utfylt, ellers [VilkårsvurderingerTilKlage.Påbegynt]
         */
        fun create(
            vedtakId: UUID?,
            innenforFristen: Svarord?,
            klagesDetPåKonkreteElementerIVedtaket: Boolean?,
            erUnderskrevet: Svarord?,
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
