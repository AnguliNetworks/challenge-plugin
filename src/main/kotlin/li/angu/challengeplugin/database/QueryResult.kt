package li.angu.challengeplugin.database

import java.sql.ResultSet

/**
 * Represents the result of a database query
 */
sealed class QueryResult {
    data class Success(override val resultSet: ResultSet?) : QueryResult()
    data class Error(val message: String) : QueryResult()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    open val resultSet: ResultSet? get() = (this as? Success)?.resultSet
    val errorMessage: String? get() = (this as? Error)?.message

    companion object {
        fun success(resultSet: ResultSet? = null) = Success(resultSet)
        fun error(message: String) = Error(message)
    }
}
