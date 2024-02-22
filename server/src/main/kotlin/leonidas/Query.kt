package leonidas

import com.apollographql.apollo3.annotations.GraphQLObject

@GraphQLObject
class Query {
    val hello = "world"
}