package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.util.UUID

enum class Fradragstype {
    Uføretrygd,
    Barnetillegg,
    Arbeidsinntekt,
    Pensjon,
    Kapitalinntekt,
    UtenlandskInntekt,
    AndreYtelser;

    companion object {
        fun isValid(s: String) =
            runBlocking {
                Either.catch { valueOf(s) }
                    .isRight()
            }
    }
}

data class Fradrag(
    val id: UUID = UUID.randomUUID(),
    val type: Fradragstype,
    val beløp: Int,
    val beskrivelse: String? = null
) : DtoConvertable<FradragDto> {
    fun perMåned(): Int = beløp / 12

    override fun toDto(): FradragDto = FradragDto(id, type, beløp, beskrivelse)
}

data class FradragDto(
    val id: UUID,
    val type: Fradragstype,
    var beløp: Int,
    val beskrivelse: String?
)
