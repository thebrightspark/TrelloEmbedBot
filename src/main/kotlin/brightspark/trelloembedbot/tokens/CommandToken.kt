package brightspark.trelloembedbot.tokens

import brightspark.trelloembedbot.Utils
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.core.hooks.SubscribeEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class CommandToken {
	companion object {
		// This is a map of userId to guildId and start timestamp so actions by a user in DM can affect the specific guild
		private val privateConvos = HashMap<Long, Pair<Long, Long>>()
		private val dmTimeout = TimeUnit.MINUTES.toMillis(5)
	}

	@Value("\${prefix:t!}")
	private lateinit var prefix: String

	@Autowired
	private lateinit var tokenHandler: TrelloTokenHandler

	// Looks for "t! token" in guilds sent by the guild owner or admins
	@SubscribeEvent
	fun onGuildMessage(event: GuildMessageReceivedEvent) {
		if (event.author.isBot)
			return

		// Only the guild owner or admins can change the bot token
		val member = event.member
		if (!member.isOwner && !member.hasPermission(Permission.ADMINISTRATOR))
			return
		val parts = getCommandParts(event.message, true)
		if (parts.size == 1 && parts[0].equals("token", true)) {
			// Open a private session with the user to manage the token
			val user = member.user
			privateConvos[user.idLong] = event.guild.idLong to System.currentTimeMillis() + dmTimeout
			user.openPrivateChannel().queue { it.sendMessage(Utils.getDmMessage(event.guild)).queue() }
		}
	}

	// Responds to DMs who have a valid session open
	@SubscribeEvent
	fun onPrivateMessage(event: PrivateMessageReceivedEvent) {
		if (event.author.isBot)
			return

		val userId = event.author.idLong
		val channel = event.channel
		val session = privateConvos[userId]
		if (session == null) {
			Utils.sendMessage(channel, "You need to use `t! token` in your server before we can do anything here!")
		} else {
			// Validate session
			val guildId = session.first
			if (event.jda.getGuildById(guildId) == null) {
				privateConvos.remove(userId)
				Utils.sendMessage(channel, "Couldn't find server with ID $guildId!")
				return
			}
			if (session.second < System.currentTimeMillis()) {
				Utils.sendMessage(channel, "Our private message session has timed out!\nPlease use `t! token` in your guild again if you want to continue.")
				return
			}

			// Handle command
			val parts = getCommandParts(event.message, false)
			val numParts = parts.size
			if (numParts < 1)
				return
			when (parts[0]) {
				"set" -> {
					when {
						numParts < 2 -> {
							Utils.sendMessage(channel, "No token provided!", success = false)
							return
						}
						numParts > 2 -> {
							Utils.sendMessage(channel, "Invalid token provided!", success = false)
							return
						}
					}
					val token = parts[1]
					tokenHandler.setToken(guildId, token, event.author.idLong)
					Utils.sendMessage(channel, "Set token for this server to $token")
				}
				"get" -> {
					val pair = tokenHandler.getToken(guildId)
					if (pair == null)
						Utils.sendMessage(channel, "There is no Trello token set for this server")
					else {
						val owner = event.jda.getUserById(pair.second)
						Utils.sendMessage(channel, "Trello token: ${pair.first}\nAdded by: ${if (owner != null) owner.name else "Unknown"}")
					}
				}
				"del" -> {
					tokenHandler.removeToken(guildId)
					Utils.sendMessage(channel, "Trello token removed for this server")
				}
				"end" -> {
					privateConvos.remove(userId)
					Utils.sendMessage(channel, "Session ended")
				}
				else -> Utils.sendMessage(channel, "Invalid command arguments", success = false)
			}
		}
	}

	private fun getCommandParts(message: Message, requiresPrefix: Boolean): List<String> {
		var content = message.contentRaw
		if (content.startsWith(prefix))
			content = content.substring(prefix.length).trim()
		else if (requiresPrefix)
			return emptyList()
		return content.split(Regex("\\s+"))
	}
}