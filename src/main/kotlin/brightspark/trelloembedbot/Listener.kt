package brightspark.trelloembedbot

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.SubscribeEvent
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import java.util.regex.Pattern

class Listener {
    companion object {
        const val trelloKey: String = "10a61e7cd59f0840e292b285a9b21dab"
        lateinit var trelloToken: String
        val urlPattern: Pattern = Pattern.compile("(https?://)?trello.com/c/(\\w+)", Pattern.CASE_INSENSITIVE)
        val rest: RestTemplate = RestTemplate()
        val jsonMapper: ObjectMapper = ObjectMapper()
    }

    private val log = LoggerFactory.getLogger(this::class.java)
    private val cardIdMap: MutableMap<String, String> = HashMap()

    @SubscribeEvent
    fun onReady(event: ReadyEvent) {
        log.info("JDA ready with ${event.guildAvailableCount} / ${event.guildTotalCount} guilds!")
        event.jda.presence.setPresence(OnlineStatus.ONLINE, Game.of(Game.GameType.DEFAULT, "Watching for Trello card links"))
    }

    @SubscribeEvent
    fun onMessage(event: MessageReceivedEvent) {
        val message = event.message
        val content = message.contentRaw
        val matcher = urlPattern.matcher(content)
        while (matcher.find()) {
            val cardId = matcher.group(2)
            val cardInfo = getCardInfo(cardId)
            //TODO: Get other card data? e.g. board name, list name, member names, cover image... (cache them!)
            //event.textChannel.sendMessage(cardId).queue()
        }
    }

    private fun getCardInfo(cardId: String) : JsonNode? {
        val response = rest.getForEntity<String>("https://api.trello.com/1/cards/$cardId?fields=closed,dateLastActivity,desc,descData,due,dueComplete,idAttachmentCover,idBoard,idChecklists,idList,idMembers,labels,name&key=$trelloKey&token=$trelloToken")
        return if (response.statusCode.isError) null else jsonMapper.readTree(response.body)
    }
}