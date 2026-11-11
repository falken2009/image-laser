package com.king365.fire

// ---------- Doublons : un seul exemplaire par film ou série, celui de la meilleure qualité ----------

object Duplicates {
    /// Mentions de qualité, de codec ou de langue qui distinguent deux copies du même titre
    private val tags = listOf(
        "4k", "uhd", "2160p", "2160", "fhd", "1080p", "1080", "hd", "720p", "720", "sd", "480p", "576p",
        "hdr", "hdr10", "dv", "dolby vision", "hevc", "h265", "h 265", "x265", "h264", "x264", "10bit",
        "multi", "vf", "vff", "vfq", "vf2", "vo", "vostfr", "french", "truefrench", "subfrench",
        "bluray", "blu ray", "webrip", "web dl", "webdl", "web", "hdrip", "dvdrip", "remux", "imax",
        "3d", "cam", "ts", "md", "light", "backup"
    ).sortedByDescending { it.length }

    private val yearInBrackets = Regex("""[(\[]\s*(19|20)\d{2}\s*[)\]]""")
    private val yearAtEnd = Regex("""\s(19|20)\d{2}\s*$""")
    private val notAlnum = Regex("""[^\p{L}\p{N}]+""")

    /// « FR - Avatar (2009) [4K HDR] » → « avatar »
    fun key(name: String): String {
        var s = Catalog.fold(Catalog.stripPrefix(name))
        s = yearInBrackets.replace(s, " ")
        s = yearAtEnd.replace(s, " ")
        s = notAlnum.replace(s, " ")
        var words = " $s "
        for (tag in tags) {
            while (words.contains(" $tag ")) words = words.replace(" $tag ", " ")
        }
        val cleaned = words.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        return cleaned.ifEmpty { Catalog.sortKey(name) }
    }

    /// Plus c'est haut, meilleure est la copie
    fun quality(name: String): Int {
        val s = " " + notAlnum.replace(name.lowercase(), " ") + " "
        fun has(vararg w: String) = w.any { s.contains(" $it ") }
        var score = when {
            has("4k", "uhd", "2160p", "2160") -> 40
            has("fhd", "1080p", "1080") -> 30
            has("hd", "720p", "720") -> 20
            has("sd", "480p", "576p") -> 10
            else -> 18 // sans mention : sans doute en HD
        }
        if (has("hdr", "hdr10", "dv")) score += 3
        if (has("remux", "bluray")) score += 2
        if (has("hevc", "h265", "x265")) score += 1
        if (has("cam", "ts", "md")) score -= 15 // copies filmées en salle
        if (has("vostfr", "vo", "subfrench")) score -= 1 // la VF passe avant à qualité égale
        return score
    }

    /// Garde une seule copie de chaque titre, à la place de la première rencontrée.
    /// Deux titres identiques mais d'années différentes (remakes) restent séparés.
    fun <T> keepBest(items: List<T>, name: (T) -> String, year: (T) -> Int? = { null }): List<T> {
        class Slot(var year: Int?, val index: Int)
        val slots = HashMap<String, MutableList<Slot>>()
        val result = ArrayList<T>()
        for (item in items) {
            val k = key(name(item))
            val y = year(item)
            val slot = slots[k]?.firstOrNull { it.year == null || y == null || it.year == y }
            if (slot != null) {
                if (quality(name(item)) > quality(name(result[slot.index]))) result[slot.index] = item
                if (slot.year == null && y != null) slot.year = y
            } else {
                slots.getOrPut(k) { mutableListOf() }.add(Slot(y, result.size))
                result.add(item)
            }
        }
        return result
    }
}
