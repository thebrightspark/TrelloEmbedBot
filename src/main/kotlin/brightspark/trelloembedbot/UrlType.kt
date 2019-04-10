package brightspark.trelloembedbot

enum class UrlType {
    B,
    C;

    companion object {
        fun fromString(type: String) : UrlType? = values().firstOrNull { it.name.equals(type, true) }
    }
}