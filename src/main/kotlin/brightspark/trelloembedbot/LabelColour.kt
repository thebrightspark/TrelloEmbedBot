package brightspark.trelloembedbot

enum class LabelColour(val colour: Int) {
    YELLOW(0xF2D600),
    PURPLE(0xC377E0),
    BLUE(0x0079BF),
    RED(0xEB5A46),
    GREEN(0x61BD4F),
    ORANGE(0xFF9F1A),
    BLACK(0x355263),
    SKY(0x00C2E0),
    PINK(0xFF78CB),
    LIME(0x51E898),
    NULL(0xB3BEC4);

    companion object {
        fun fromString(type: String) : LabelColour? = values().firstOrNull { it.name.equals(type, true) }
    }
}