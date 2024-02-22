package leonidas

import com.apollographql.apollo3.annotations.GraphQLObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Date

@GraphQLObject
class Subscription {
    fun count(from: Int, to: Int, delayMillis: Int): Flow<Int> {
        return flow {
            from.until(to).forEach {
                emit(it)
                delay(delayMillis.toLong())
            }
        }
    }

    fun time(delayMillis: Int): Flow<String> {
        return flow {
            while(true) {
                emit(Date().toString())
                delay(delayMillis.toLong())
            }
        }
    }
}