package brightspark.trelloembedbot.listener

import brightspark.trelloembedbot.Application
import org.springframework.stereotype.Component

@Component
class TrelloApiUrlBuilder {
    companion object {
        private const val urlBase = "https://api.trello.com/1/"
    }

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
        val sb = StringBuilder("$urlBase$api/%s?key=${Application.trelloKey}&token=%s")
        params.forEach { sb.append('&').append(it.first).append('=').append(it.second) }
        return TrelloApiUrl(sb.toString())
    }
}