package brightspark.trelloembedbot

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import java.io.IOException

@Component
class RequestHandler : InitializingBean {
    companion object {
        const val trelloKey: String = "10a61e7cd59f0840e292b285a9b21dab"
        val rest: RestTemplate = RestTemplate()
        val jsonMapper: ObjectMapper = ObjectMapper()
    }

    @Value("\${trelloToken}")
    private lateinit var trelloToken: String

    @Autowired
    private lateinit var requestLimiter: RequestLimiter

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun afterPropertiesSet() {
        // Don't want the error handler to do anything - will handle it later with the rest of the response handling
        rest.errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse): Boolean = false
            override fun handleError(response: ClientHttpResponse) = Unit
        }
    }

    private fun get(request: String) : JsonNode? {
        val waitTime = requestLimiter.acquire()
        if (waitTime > 0) {
            log.warn("Waiting for ${waitTime}ms before sending next request")
            Thread.sleep(waitTime)
        }
        log.info("HTTP Get: $request")
        val response = rest.getForEntity<String>(request)
        val status = response.statusCode
        val json: JsonNode
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

    fun getCardInfo(cardId: String) : JsonNode? {
        log.info("Getting info for card ID '$cardId' from Trello")
        return get("https://api.trello.com/1/cards/$cardId?key=$trelloKey&token=$trelloToken&fields=closed,desc,due,dueComplete,name,labels&members=true&member_fields=username&checklists=all&checklist_fields=name,pos&board=true&board_fields=name&list=true&list_fields=name&label_fields=name,color")
    }
}