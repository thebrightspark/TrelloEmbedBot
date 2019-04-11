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
        //TODO: Change this to only be used in private channels
        /*
        Probably will need a separate command to initiate/authorise the DM and use of this command for a guild?
        e.g.
        (following command can only be used in guilds by members with admin perms)
        t! token
        (bot opens DM with user and allows them to use this command to change the token for the guild)
        t! set <token>
         */
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
                        tokenHandler.setToken(guildId, token, event.author.idLong)
                        channel.sendMessage(createMessage("Set token for this server to $token"))
                    }
                    "get" -> {
                        val pair = tokenHandler.getToken(guildId)
                        if (pair == null)
                            channel.sendMessage(createMessage("There is no Trello token set for this server"))
                        else {
                            val owner = event.jda.getUserById(pair.second)
                            channel.sendMessage(createMessage("Trello token: ${pair.first}\nAdded by: ${if (owner != null) owner.name else "Unknown"}"))
                        }
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