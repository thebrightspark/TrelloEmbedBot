package brightspark.trelloembedbot.tokens

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

@Component
class TrelloTokenHandler : InitializingBean {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val dbUrl = "jdbc:sqlite:" + File("db").absolutePath
    private var dbConnection: Connection? = null

    override fun afterPropertiesSet() =
        execute("create table if not exists tokens (guild_id integer primary key not null, trello_token text, token_owner integer, notified integer default 0 not null)")

    private fun getConnection() : Connection {
        try {
            if (dbConnection?.isClosed != true)
                dbConnection = DriverManager.getConnection(dbUrl)
        } catch (e: SQLException) {
            log.error("Couldn't open connection to DB", e)
            System.exit(0)
        }
        return dbConnection!!
    }

    private fun execute(query: String) {
        log.info("Executing query: $query")
        getConnection().createStatement().use { it.execute(query) }
    }

    private fun <T> executeWithResult(query: String, resultParser: (results: ResultSet) -> T) : T? {
        log.info("Executing query: $query")
        getConnection().createStatement().use {
            it.executeQuery(query).use { resultSet ->
                var result: T? = null
                while (resultSet.next()) {
                    if (result != null)
                        throw RuntimeException("Expected 1 result, but got more than 1!")
                    else
                        result = resultParser.invoke(resultSet)
                }
                return result
            }
        }
    }

    fun getToken(guildId: Long): Pair<String, Long>? =
            executeWithResult("select trello_token from tokens where guild_id = $guildId") {
                val token = it.getString("trello_token")
                val owner = it.getLong("token_owner")
                if (token != null && owner > 0) Pair(token, owner) else null
            }

    fun setToken(guildId: Long, trelloToken: String, userId: Long) =
            execute("replace into tokens (guild_id,trello_token,token_owner) values ($guildId,$trelloToken,$userId)")

    fun removeToken(guildId: Long) = execute("delete from tokens where guild_id = $guildId")

    fun getTokenAndNotified(guildId: Long): Pair<String, Boolean> =
            executeWithResult("select trello_token, notified from tokens where guild_id = $guildId") {
                Pair(it.getString("trello_token"), it.getBoolean("notified"))
            } ?: Pair("", false)

    fun setNotified(guildId: Long) = execute("replace into tokens (guild_id,notified) values ($guildId,true)")
}