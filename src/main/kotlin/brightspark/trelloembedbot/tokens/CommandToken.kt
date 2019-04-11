package brightspark.trelloembedbot.tokens

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.SubscribeEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class CommandToken {
    @Value("\${prefix:t!}")
    private lateinit var prefix: String

    @Autowired
    private lateinit var tokenHandler: TrelloTokenHandler

    @SubscribeEvent
    fun onMessage(event: MessageReceivedEvent) {
        if (!event.member.isOwner)
            return
        val channel = event.textChannel
        val message = event.message.contentRaw
        if (message.startsWith(prefix)) {
            val parts = message.substring(prefix.length).trim().split(Regex("\\s+"), 3)
            val numParts = parts.size
            if (numParts < 2) {
                channel.sendMessage(createFailMessage("Invalid command arguments"))
                return
            }
            if (parts[0] == "token") {
                val guildId = event.guild.idLong
                when (parts[1]) {
                    "set" -> {
                        if (numParts < 3) {
                            channel.sendMessage(createFailMessage("Invalid command arguments"))
                            return
                        }
                        val token = parts[2]
                        tokenHandler.putToken(guildId, token)
                        channel.sendMessage(createMessage("Set token for this server to $token"))
                    }
                    "get" -> {
                        val token = tokenHandler.getToken(guildId)
                        channel.sendMessage(createMessage(
                            if (token.isBlank())
                                "There is no Trello token set for this server"
                            else
                                "Trello token for this server is currently: $token"))
                    }
                    "del" -> {
                        tokenHandler.removeToken(guildId)
                        channel.sendMessage(createMessage("Trello token removed for this server"))
                    }
                    else -> channel.sendMessage(createFailMessage("Invalid command arguments"))
                }
            }
        }
    }

    private fun createFailMessage(text: String): MessageEmbed = createMessage(text, false)

    private fun createMessage(text: String, success: Boolean = true): MessageEmbed =
        EmbedBuilder()
            .setDescription(text)
            .setColor(if (success) Color.GREEN else Color.RED)
            .build()
}