package brightspark.trelloembedbot

import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.hooks.AnnotatedEventManager
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import javax.security.auth.login.LoginException
import kotlin.system.exitProcess

@SpringBootApplication
class Application {
	private val log = LoggerFactory.getLogger(this::class.java)

	@Bean
	fun init() {
		// Read configs
		val configFile = File("config.properties")
		if (!configFile.exists()) {
			log.error("No config file! Generating default for user...")
			val properties = Properties()
			properties.setProperty("token", "")
			properties.setProperty("trelloToken", "")
			val output = FileOutputStream(configFile)
			output.use { properties.store(it, null) }
			exitProcess(0)
		}

		val properties = Properties()
		val input = FileInputStream(configFile)
		input.use { properties.load(it) }

		if (!properties.containsKey("token")) {
			log.error("No token specified in config file!")
			exitProcess(0)
		}

		if (!properties.containsKey("trelloToken")) {
			log.error("No trelloToken specified in config file!")
			exitProcess(0)
		}

		val token = properties.getProperty("token")
		Listener.trelloToken = properties.getProperty("trelloToken")

		/*if (trelloToken.isBlank()) {
			log.error("Trello API Key '$trelloToken' is invalid!")
			exitProcess(0)
		}*/

		val builder = JDABuilder(AccountType.BOT)
			.setToken(token)
			.setStatus(OnlineStatus.DO_NOT_DISTURB)
			.setGame(Game.playing("Starting up..."))
			.setEventManager(AnnotatedEventManager())
			.addEventListener(Listener())
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
