package no.nav.su.se.bakover.domain

class Vilkårsvurdering(
    private val vilkår: Vilkår,
    private val begrunnelse: String,
    private val status: Status
) {
    //language=JSON
    fun toJson() = """
        {
            "vilkår": "$vilkår",
            "begrunnelse" : "$begrunnelse",
            "status": "$status"
        }
    """.trimIndent()

    enum class Status {
        OK,
        IKKE_OK,
        IKKE_VURDERT
    }
}

enum class Vilkår {
    UFØRE
}
