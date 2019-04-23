package brightspark.trelloembedbot.listener

import brightspark.trelloembedbot.Utils
import brightspark.trelloembedbot.tokens.TrelloTokenHandler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.TextChannel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import java.awt.Color
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

	private fun get(request: String, channel: TextChannel): JsonNode? {
		val waitTime = requestLimiter.acquire()
		if (waitTime > 0) {
			log.warn("Waiting for ${waitTime}ms before sending next request")
			Thread.sleep(waitTime)
		}
		log.info("HTTP Get: $request")
		val response = rest.getForEntity<String>(request)
		val status = response.statusCode
		log.info("Response status: $status")
		var json: JsonNode? = null
		try {
			json = jsonMapper.readTree(response.body)
		} catch (e: IOException) {
			log.error("Exception parsing response body from URL as JSON\nResponse body: ${response.body}\n\n${e.message}")
		}
		if (status.isError) {
			val errorMessage =
				if (json == null)
					"Error: ${response.body}"
				else
					"Error: ${json.get("error")}\nMessage: ${json.get("message")}"
			log.error("Error getting from URL: $request\nCode: ${status.value()}\n$errorMessage")
			Utils.sendMessage(channel, "Request to Trello API failed\n$errorMessage", success = false)
			return null
		}
		return json
	}

	private fun getToken(channel: TextChannel): String? {
		val guild = channel.guild
		val pair = tokenHandler.getTokenAndNotified(guild.idLong)
		val token = pair.first
		if (token == null || token.isBlank()) {
			log.warn("Token not set for guild ID ${guild.idLong} - not getting info")
			if (!pair.second) {
				tokenHandler.setNotified(guild.idLong)
				channel.sendMessage(EmbedBuilder()
					.setDescription("The Trello token for this server hasn't been set!\nAn admin should use `t! token` to open a DM session with me to configure it.")
					.setColor(Color.RED)
					.build())
					.queue()
			}
			return null
		}
		return token
	}

	fun getBoardInfo(boardId: String, channel: TextChannel): JsonNode? {
		getToken(channel)?.let {
			log.info("Getting info for board ID '$boardId' from Trello using token '$it'")
			return get(urlBoards.create(boardId, it), channel)
		}
		return null
	}

	fun getCardInfo(cardId: String, channel: TextChannel): JsonNode? {
		getToken(channel)?.let {
			log.info("Getting info for card ID '$cardId' from Trello using token '$it'")
			return get(urlCards.create(cardId, it), channel)
		}
		return null
	}
}