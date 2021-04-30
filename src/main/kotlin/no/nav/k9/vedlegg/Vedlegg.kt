package no.nav.k9.vedlegg

import com.fasterxml.jackson.annotation.JsonProperty

data class Vedlegg(
    val content: ByteArray,
    val contentType: String,
    val title: String,
    val eier: DokumentEier

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vedlegg

        if (!content.contentEquals(other.content)) return false
        if (contentType != other.contentType) return false
        if (title != other.title) return false
        if (eier != other.eier) return false

        return true
    }

    override fun hashCode(): Int {
        var result = content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + title.hashCode()
        return result
    }
}

data class DokumentEier(
    @JsonProperty("eiers_fødselsnummer") val eiersFødselsnummer: String
)
