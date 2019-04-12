package brightspark.trelloembedbot

import brightspark.trelloembedbot.listener.Listener
import brightspark.trelloembedbot.tokens.CommandToken
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.hooks.AnnotatedEventManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import javax.security.auth.login.LoginException
import kotlin.system.exitProcess

@SpringBootApplication
class Application {
	@Value("\${token}")
	private lateinit var token: String

	@Autowired
	private lateinit var listener: Listener

	@Autowired
	private lateinit var commandToken: CommandToken

	private val log = LoggerFactory.getLogger(this::class.java)

	@Bean
	fun init() {
		val builder = JDABuilder(AccountType.BOT)
			.setToken(token)
			.setStatus(OnlineStatus.DO_NOT_DISTURB)
			.setGame(Game.playing("Starting up..."))
			.setEventManager(AnnotatedEventManager())
			.addEventListener(listener, commandToken)
		try {
			builder.build()
		}
		catch (e: LoginException) {
			log.error("Token '$token' is invalid!")
			exitProcess(0)
		}
	}
}

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}
