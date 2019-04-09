package brightspark.trelloembedbot

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.asCoroutineDispatcher
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.SubscribeEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.regex.Pattern

@Component
class Listener : DisposableBean {
    companion object {
        val urlPattern: Pattern = Pattern.compile("(https?://)?trello.com/c/(\\w+)", Pattern.CASE_INSENSITIVE)
    }

    @Autowired
    private lateinit var requestHandler: RequestHandler

    private val log = LoggerFactory.getLogger(this::class.java)
    private val pool = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override fun destroy() = pool.close()

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
            pool.executor.execute {
                val cardInfo = requestHandler.getCardInfo(cardId)
                if (cardInfo == null) {
                    print("Couldn't get info for card $cardId")
                    return@execute
                }
                val sb = StringBuilder()

                val closed = cardInfo.get("closed").asBoolean()
                if (closed) sb.append("**This card is closed**")

                val due = cardInfo.get("due")
                if (!due.isNull) {
                    appendInfo(sb, "Due", due.asText())
                    appendInfo(sb, "Due Completed", cardInfo.get("dueComplete").asBoolean())
                }

                val members = extractFromJsonArray(cardInfo.get("members")) { it.get("username").asText() }
                if (members.isNotEmpty()) {
                    members.sort()
                    appendInfo(sb, "Members", members.joinToString())
                }

                appendInfo(sb, "List", cardInfo.get("list").get("name").asText())

                val labels = extractFromJsonArray(cardInfo.get("labels")) {
                    Pair(it.get("name").asText(), LabelColour.valueOf(it.get("color").asText().toUpperCase()))
                }
                var messageColour = LabelColour.NULL.colour
                if (labels.isNotEmpty()) {
                    messageColour = labels[0].second.colour
                    appendInfo(sb, "Labels", labels.joinToString { it.first })
                }

                appendInfo(sb, "Desc", cardInfo.get("desc").textValue())

                //TODO: Add checklists

                val embedBuilder = EmbedBuilder()
                        .setTitle(cardInfo.get("name").asText())
                        .setDescription(sb.toString())
                        .setFooter(cardInfo.get("board").get("name").asText(), null)
                        .setColor(messageColour)
                event.textChannel.sendMessage(embedBuilder.build()).queue()
            }
        }
    }

    private fun appendInfo(builder: StringBuilder, name: String, value: Any) {
        builder.append("**").append(name).append(":** ").append(value).append("\n")
    }

    private fun <T> extractFromJsonArray(array: JsonNode, mapper: (JsonNode) -> T) : ArrayList<T> {
        val list = ArrayList<T>()
        array.elements().forEach { list.add(mapper.invoke(it)) }
        return list
    }
}