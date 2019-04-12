package brightspark.trelloembedbot.listener

import brightspark.trelloembedbot.Application
import brightspark.trelloembedbot.tokens.TrelloTokenHandler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.core.entities.Guild
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import java.io.IOException

@Component
class RequestHandler : InitializingBean {
    companion object {
        val rest: RestTemplate = RestTemplate()
        val jsonMapper: ObjectMapper = ObjectMapper()
    }

    @Autowired
    private lateinit var requestLimiter: RequestLimiter

    @Autowired
    private lateinit var urlBuilder: TrelloApiUrlBuilder

    @Autowired
    private lateinit var tokenHandler: TrelloTokenHandler

    private val log = LoggerFactory.getLogger(this::class.java)
    private lateinit var urlBoards: TrelloApiUrl
    private lateinit var urlCards: TrelloApiUrl

    override fun afterPropertiesSet() {
        // Don't want the error handler to do anything - will handle it later with the rest of the response handling
        rest.errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse): Boolean = false
            override fun handleError(response: ClientHttpResponse) = Unit
        }

        urlBoards = urlBuilder.create("boards")
            .addParam("fields", "name,desc,closed,prefs")
            .build()
        urlCards = urlBuilder.create("cards")
            .addParam("fields", "closed,desc,due,dueComplete,name,labels")
            .addParam("members", "true")
            .addParam("member_fields", "username")
            .addParam("checklists", "all")
            .addParam("checklist_fields", "name,pos")
            .addParam("board", "true")
            .addParam("board_fields", "name")
            .addParam("list", "true")
            .addParam("list_fields", "name")
            .addParam("label_fields", "name,color")
            .build()
    }

    private fun get(request: String, guild: Guild) : JsonNode? {
        val waitTime = requestLimiter.acquire()
        if (waitTime > 0) {
            log.warn("Waiting for ${waitTime}ms before sending next request")
            Thread.sleep(waitTime)
        }
        log.info("HTTP Get: $request")
        val response = rest.getForEntity<String>(request)
        val status = response.statusCode
        val json: JsonNode
        //TODO: Send message back to channel on failure?
        try {
            json = jsonMapper.readTree(response.body)
        } catch (e: IOException) {
            log.error("Exception parsing JSON from URL: $request", e)
            return null
        }
        if (status.isError) {
            log.error("Error getting from URL: $request\nCode: ${status.value()}\nError: ${json.get("error")}\nMessage: ${json.get("message")}")
            return null
        }
        return json
    }

    private fun getToken(guild: Guild): String? {
        val pair = tokenHandler.getTokenAndNotified(guild.idLong)
        val token = pair.first
        if (token.isBlank()) {
            log.warn("Token not set for guild ID ${guild.idLong} - not getting info")
            if (!pair.second) {
                tokenHandler.setNotified(guild.idLong)
                guild.owner.user.openPrivateChannel().queue { it.sendMessage(Application.messageDm).queue() }
            }
            return null
        }
        return token
    }

    fun getBoardInfo(boardId: String, guild: Guild) : JsonNode? {
        getToken(guild)?.let {
            log.info("Getting info for board ID '$boardId' from Trello using token '$it'")
            return get(urlBoards.create(boardId, it), guild)
        }
        return null
    }

    fun getCardInfo(cardId: String, guild: Guild) : JsonNode? {
        getToken(guild)?.let {
            log.info("Getting info for card ID '$cardId' from Trello using token '$it'")
            return get(urlCards.create(cardId, it), guild)
        }
        return null
    }
}