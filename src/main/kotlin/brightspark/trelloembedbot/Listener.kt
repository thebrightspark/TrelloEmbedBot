package brightspark.trelloembedbot

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.SubscribeEvent
import org.slf4j.LoggerFactory
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
            //TODO: Move this into a handler with request limiter
            //Limit: 100 requests per 10 seconds
            val cardId = matcher.group(2)
            val cardInfo = getCardInfo(cardId)
            if (cardInfo == null) {
                print("Couldn't get info for card $cardId")
                continue
            }
            val sb = StringBuilder()
            val closed = cardInfo.get("closed").asBoolean()
            if (closed) sb.append("**This card is closed**")
            appendInfo(sb, "Desc", cardInfo.get("desc").textValue())
            val due = cardInfo.get("due")
            if (!due.isNull) {
                appendInfo(sb, "Due", due.asText())
                appendInfo(sb, "Due Completed", cardInfo.get("dueComplete").asBoolean())
            }
            val members = ArrayList<String>()
            cardInfo.get("members").elements().forEach { members.add(it.get("username").asText()) }
            if (members.isNotEmpty()) {
                members.sort()
                appendInfo(sb, "Members", members.joinToString())
            }
            appendInfo(sb, "List", cardInfo.get("list").get("name").asText())

            val embedBuilder = EmbedBuilder()
                .setTitle(cardInfo.get("name").asText())
                .setDescription(sb.toString())
                .setFooter(cardInfo.get("board").get("name").asText(), null)
            event.textChannel.sendMessage(embedBuilder.build()).queue()
        }
    }

    private fun getCardInfo(cardId: String) : JsonNode? {
        val response = rest.getForEntity<String>("https://api.trello.com/1/cards/$cardId?key=$trelloKey&token=$trelloToken&fields=closed,dateLastActivity,desc,due,dueComplete,name&members=true&member_fields=username&checklists=all&checklist_fields=name,pos&board=true&board_fields=name&list=true&list_fields=name&labels=all&label_fields=name,color")
        return if (response.statusCode.isError) null else jsonMapper.readTree(response.body)
    }

    private fun appendInfo(builder: StringBuilder, name: String, value: Any) {
        builder.append("**").append(name).append(":** ").append(value).append("\n")
    }
}