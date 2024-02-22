package leonidas

import com.apollographql.apollo3.api.ExecutionContext
import com.apollographql.apollo3.ast.toGQLDocument
import com.apollographql.apollo3.ast.toSchema
import com.apollographql.apollo3.execution.*
import com.apollographql.apollo3.execution.websocket.ApolloWebSocketHandler
import com.apollographql.apollo3.execution.websocket.ConnectionInitAck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import leonidas.execution.LeonidasExecutableSchemaBuilder
import okio.Buffer
import okio.buffer
import okio.source
import org.http4k.core.*
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.filter.CorsPolicy.Companion.UnsafeGlobalPermissive
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.websockets
import org.http4k.server.Jetty
import org.http4k.server.PolyHandler
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsHandler
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse
import org.http4k.routing.ws.bind as wsBind

fun ExecutableSchema(): ExecutableSchema {
    val schema = GraphQLHttpHandler::class.java.classLoader
        .getResourceAsStream("schema.graphqls")!!
        .source()
        .buffer()
        .readUtf8()
        .toGQLDocument()
        .toSchema()

    return LeonidasExecutableSchemaBuilder(schema)
        .build()
}

class GraphQLHttpHandler(val executableSchema: ExecutableSchema, val executionContext: ExecutionContext) : HttpHandler {
    override fun invoke(request: Request): Response {

        val graphQLRequestResult = when (request.method) {
            Method.GET -> request.uri.toString().parseGetGraphQLRequest()
            Method.POST -> request.body.stream.source().buffer().use { it.parsePostGraphQLRequest() }
            else -> error("")
        }

        if (graphQLRequestResult is GraphQLRequestError) {
            return Response(BAD_REQUEST).body(graphQLRequestResult.message)
        }
        graphQLRequestResult as GraphQLRequest

        val response = executableSchema.execute(graphQLRequestResult, executionContext)

        val buffer = Buffer()
        response.serialize(buffer)
        val responseText = buffer.readUtf8()

        return Response(OK)
            .header("content-type", "application/json")
            .body(responseText)
    }
}

class SandboxHandler : HttpHandler {
    override fun invoke(request: Request): Response {
        return Response(OK).body(javaClass.classLoader!!.getResourceAsStream("sandbox.html")!!)
    }
}


fun ApolloWebsocketHandler(executableSchema: ExecutableSchema): WsHandler {

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    return { _: Request ->
        WsResponse { ws: Websocket ->

            val handler = ApolloWebSocketHandler(
                executableSchema = executableSchema,
                scope = scope,
                executionContext = ExecutionContext.Empty,
                sendMessage = { ws.send(it.toWsMessage()) },
                connectionInitHandler = { ConnectionInitAck }
            )

            ws.onMessage {
                it.body.payload.array().decodeToString().let {
                    handler.handleMessage(WebSocketTextMessage(it))
                }
            }
            ws.onClose {
                handler.close()
            }
            ws.onError {
                handler.close()
            }
        }
    }
}

private fun WebSocketMessage.toWsMessage(): WsMessage {
    return when (this) {
        is WebSocketBinaryMessage -> {
            WsMessage(MemoryBody(data))
        }

        is WebSocketTextMessage -> {
            WsMessage(data)
        }
    }
}

fun AppHandler(): PolyHandler {
    val executableSchema = ExecutableSchema()
    val ws =
        websockets("/subscription" wsBind ApolloWebsocketHandler(executableSchema))

    val http = ServerFilters.CatchAll {
        it.printStackTrace()
        ServerFilters.CatchAll.originalBehaviour(it)
    }
        .then(ServerFilters.Cors(UnsafeGlobalPermissive))
        .then(
            routes(
                "/graphql" bind Method.POST to GraphQLHttpHandler(executableSchema, ExecutionContext.Empty),
                "/graphql" bind Method.GET to GraphQLHttpHandler(executableSchema,  ExecutionContext.Empty),
                "/sandbox" bind Method.GET to SandboxHandler()
            )
        )

    return PolyHandler(http, ws)
}

fun main() {
    AppHandler().asServer(Jetty(System.getenv("PORT")?.toIntOrNull() ?: 8080)).start().block()
}
