package no.nav.su.se.bakover.web.routes.regulering

import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.regulering.ReguleringssupplementFor
import no.nav.su.se.bakover.domain.regulering.ReguleringssupplementInnhold
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate

fun Map<String, List<SupplementInnholdAsCsv>>.toReguleringssupplementInnhold(): List<ReguleringssupplementFor> {
    return this.map { (stringFnr, csv) ->
        val fnr = Fnr.tryCreate(stringFnr) ?: TODO()
        val alleFradragGruppert = csv.groupBy { it.type }

        val perType = alleFradragGruppert.map { (fradragstype, csv) ->
            val type = try {
                val kategori = Fradragstype.Kategori.valueOf(fradragstype)
                Fradragstype.from(kategori, null)
            } catch (e: Exception) {
                TODO()
            }
            ReguleringssupplementInnhold.PerType(
                fradragsperiode = csv.map { csvInnslag ->
                    ReguleringssupplementInnhold.Fradragsperiode(
                        periode = Periode.create(
                            fraOgMed = LocalDate.parse(csvInnslag.fom),
                            tilOgMed = LocalDate.parse(csvInnslag.tom),
                        ),
                        type = type,
                        beløp = csvInnslag.beløp.toInt(),
                    )
                }.toNonEmptyList(),
                type = type,
            )
        }

        val p = perType.groupBy { it.type }

        val alleInnhold = p.values.map {
            ReguleringssupplementInnhold(
                fnr = fnr,
                perType = it.toNonEmptyList(),
            )
        }

        ReguleringssupplementFor(
            fnr = fnr,
            innhold = alleInnhold,
        )
    }
}
