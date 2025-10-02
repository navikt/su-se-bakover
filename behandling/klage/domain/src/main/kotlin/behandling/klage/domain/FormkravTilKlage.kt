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
    val fremsattRettsligKlageinteresse: Svarord?

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
        override val fremsattRettsligKlageinteresse: Svarord?,
    ) : FormkravTilKlage {
        companion object {

            fun empty(): Påbegynt {
                // Går via create for å verifisere at vi bruker de samme reglene.
                return create(
                    vedtakId = null,
                    innenforFristen = null,
                    klagesDetPåKonkreteElementerIVedtaket = null,
                    erUnderskrevet = null,
                    fremsattRettsligKlageinteresse = null,
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
                fremsattRettsligKlageinteresse: Svarord?,
            ): FormkravTilKlage {
                // TODO: endre til if sjekk så vi slipper !!
                val erAlleFelterUtfylt = listOf(
                    vedtakId,
                    innenforFristen,
                    klagesDetPåKonkreteElementerIVedtaket,
                    erUnderskrevet,
                ).all {
                    it != null
                }
                // TODO: vi må ha et tidsskille her som sier at noe er utfylt etter dato x siden fremsattRettsligKlageinteresse kreves ikke null fremover fra denne kommer ut
                return if (erAlleFelterUtfylt) {
                    createUtfyltOnly(
                        vedtakId = vedtakId!!,
                        innenforFristen = innenforFristen!!,
                        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket!!,
                        erUnderskrevet = erUnderskrevet!!,
                        fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
                    )
                } else {
                    Påbegynt(
                        vedtakId = vedtakId,
                        innenforFristen = innenforFristen,
                        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                        erUnderskrevet = erUnderskrevet,
                        fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
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
        override val fremsattRettsligKlageinteresse: Svarord?,
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
            fremsattRettsligKlageinteresse: Svarord?,
        ): FormkravTilKlage {
            return Utfylt(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
                fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
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
            fremsattRettsligKlageinteresse: Svarord?,
        ): FormkravTilKlage {
            return Påbegynt.create(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
                fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
            )
        }
    }
}
