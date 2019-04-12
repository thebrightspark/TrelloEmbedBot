package brightspark.trelloembedbot.listener

import brightspark.trelloembedbot.tokens.TrelloTokenHandler
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.asCoroutineDispatcher
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.SubscribeEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.concurrent.Executors
import java.util.regex.Pattern

@Component
class Listener : DisposableBean {
    companion object {
        val urlPattern: Pattern = Pattern.compile("(https?://)?trello.com/(invite/)?(?<type>\\w)/(?<id>\\w+)", Pattern.CASE_INSENSITIVE)
        val dueDateFormat = SimpleDateFormat("d MMM 'at' HH:mm z")
    }

    @Autowired
    private lateinit var requestHandler: RequestHandler

    @Autowired
    private lateinit var tokenHandler: TrelloTokenHandler

    private val log = LoggerFactory.getLogger(this::class.java)
    private val pool = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override fun destroy() = pool.close()

    @SubscribeEvent
    fun onReady(event: ReadyEvent) {
        log.info("JDA ready with ${event.guildAvailableCount} / ${event.guildTotalCount} guilds!")
        event.jda.presence.setPresence(OnlineStatus.ONLINE, Game.of(Game.GameType.DEFAULT, "Watching for Trello card links"))
    }

    @SubscribeEvent
    fun onGuildJoin(event: GuildJoinEvent) = log.info("Joined guild ${event.guild}")

    @SubscribeEvent
    fun onGuildLeave(event: GuildLeaveEvent) {
        // Remove the token (if any) for the guild
        val guild = event.guild
        log.info("Left guild $guild")
        tokenHandler.removeToken(event.guild.idLong)
    }

    @SubscribeEvent
    fun onMessage(event: MessageReceivedEvent) {
        if (event.author.isBot)
            return

        val channel = event.textChannel
        val message = event.message
        val content = message.contentRaw
        val matcher = urlPattern.matcher(content)
        //TODO: Make this more efficient by batching multiple requests together from a single Discord message
        // https://developers.trello.com/reference/#batch
        while (matcher.find()) {
            val type = matcher.group("type")
            val id = matcher.group("id")
            // Create a function to handle the ID depending on the type
            val func: ((String) -> Unit)? = when (type) {
                "b" -> { boardId: String -> handleBoard(boardId, channel) }
                "c" -> { cardId: String -> handleCard(cardId, channel) }
                else -> null
            }
            // Run the function in a coroutine
            func?.let { handler -> pool.executor.execute { handler.invoke(id) } }
        }
    }

    private fun handleBoard(boardId: String, channel: TextChannel) {
        val boardInfo = requestHandler.getBoardInfo(boardId, channel)
        if (boardInfo == null) {
            log.info("Couldn't get info for board $boardId")
            return
        }
        val sb = StringBuilder()

        if (boardInfo.get("closed").asBoolean())
            sb.append("**This board is closed**\n\n")
        var desc = boardInfo.get("desc").asText()
        if (desc.isBlank())
            desc = "Description empty"
        sb.append(desc)

        val colour = boardInfo.get("prefs").get("backgroundBottomColor").asText().substring(1).toInt(16)

        channel.sendMessage(EmbedBuilder()
                .setTitle(boardInfo.get("name").asText())
                .setDescription(sb.toString())
                .setColor(colour)
                .build())
                .queue()
    }

    private fun handleCard(cardId: String, channel: TextChannel) {
        // Get card details from Trello API
        val cardInfo = requestHandler.getCardInfo(cardId, channel)
        if (cardInfo == null) {
            log.info("Couldn't get info for card $cardId")
            return
        }
        val sb = StringBuilder()

        // Closed
        if (cardInfo.get("closed").asBoolean())
            sb.append("**This card is closed**\n")

        // Due date
        val due = cardInfo.get("due")
        if (!due.isNull) {
            val dueDate = Instant.parse(due.asText())
            val curDate = Instant.now()
            val dueEmoji =
                    when {
                        cardInfo.get("dueComplete").asBoolean() -> " ✅"
                        dueDate.isBefore(curDate) -> " ❌"
                        else -> ""
                    }

            appendInfo(sb, "Due", dueDateFormat.format(dueDate.toEpochMilli()) + dueEmoji)
        }

        // Members
        val members = extractFromJsonArray(cardInfo.get("members")) { it.get("username").asText() }
        if (members.isNotEmpty()) {
            members.sort()
            appendInfo(sb, "Members", members.joinToString())
        }

        // List
        appendInfo(sb, "List", cardInfo.get("list").get("name").asText())

        // Labels
        val labels = extractFromJsonArray(cardInfo.get("labels")) {
            val labelColour = it.get("color").asText()
            var colour = LabelColour.fromString(labelColour)
            if (colour == null) {
                log.warn("Unknown colour '$labelColour', using colour NULL instead")
                colour = LabelColour.NULL
            }
            Pair(it.get("name").asText(), colour)
        }
        var messageColour = LabelColour.NULL.colour
        if (labels.isNotEmpty()) {
            messageColour = labels[0].second.colour
            appendInfo(sb, "Labels", labels.joinToString { it.first })
        }

        // Description
        var desc = cardInfo.get("desc").textValue()
        if (desc.isBlank())
            desc = "Empty"
        appendInfo(sb, "Desc", desc)

        // Checklists (Added as fields further down)
        val checklists = extractFromJsonArray(cardInfo.get("checklists")) { list ->
            val items = extractFromJsonArray(list.get("checkItems")) {
                Triple(it.get("name").asText(),
                        it.get("state").asText().equals("complete", true),
                        it.get("pos").asInt())
            }
            items.sortBy { it.third }
            return@extractFromJsonArray Triple(list.get("name").asText(), list.get("pos").asInt(), items)
        }

        // Create embed message
        val embedBuilder = EmbedBuilder()
                .setTitle(cardInfo.get("name").asText())
                .setDescription(sb.toString())
                .setFooter(cardInfo.get("board").get("name").asText(), null)
                .setColor(messageColour)

        if (checklists.isNotEmpty()) {
            checklists.sortBy { it.second }
            checklists.forEach { list ->
                val listSb = StringBuilder()
                list.third.forEach { listSb.append(if (it.second) "☑️" else "\uD83D\uDD18").append(" ").append(it.first).append("\n") }
                embedBuilder.addField(list.first, listSb.toString(), true)
            }
        }

        channel.sendMessage(embedBuilder.build()).queue()
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