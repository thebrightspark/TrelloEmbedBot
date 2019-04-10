package brightspark.trelloembedbot.listener

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class TrelloApiUrlBuilder {
    companion object {
        private const val urlBase = "https://api.trello.com/1/"
        private const val trelloKey: String = "10a61e7cd59f0840e292b285a9b21dab"
    }

    @Value("\${trelloToken}")
    private lateinit var trelloToken: String

    private lateinit var api: String
    private val params = ArrayList<Pair<String, String>>()

    fun create(api: String) : TrelloApiUrlBuilder {
        this.api = api
        params.clear()
        return this
    }

    fun addParam(key: String, value: String) : TrelloApiUrlBuilder {
        params.add(Pair(key, value))
        return this
    }

    fun build() : TrelloApiUrl {
        val sb = StringBuilder("$urlBase$api/%s?key=$trelloKey&token=$trelloToken")
        params.forEach { sb.append('&').append(it.first).append('=').append(it.second) }
        return TrelloApiUrl(sb.toString())
    }
}