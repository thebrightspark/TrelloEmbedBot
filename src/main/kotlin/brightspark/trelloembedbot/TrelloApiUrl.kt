package brightspark.trelloembedbot

class TrelloApiUrl(private val url: String) {
    fun create(id: String) : String = String.format(url, id)
}