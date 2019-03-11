package io.javathought.vertx.ServerTest

import io.vertx.config.ConfigRetriever
import io.vertx.core.AbstractVerticle
import io.vertx.core.Context
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.OAuth2AuthHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.UserSessionHandler
import io.vertx.ext.web.sstore.LocalSessionStore

class HttpVerticle(var oAuthInstance: TestOAuth) : AbstractVerticle() {

    private val log = LoggerFactory.getLogger(HttpVerticle::class.java)
    private lateinit var config: JsonObject

    override fun init(vertx: Vertx?, context: Context?) {
        val retriever = ConfigRetriever.create(vertx)
        retriever.getConfig { ar ->
            if (ar.failed()) {
                vertx?.close()
            } else {
                config = ar.result()
            }
        }

        super.init(vertx, context)
    }

    override fun start() {
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)

        setOAuth(router, "/*", oAuthInstance.provider)

        router.route().handler { routingContext ->

            log.info("responding to path {0}", routingContext.request().path())
            // This handler will be called for every request
            val response = routingContext.response()
            response.putHeader("content-type", "text/plain")

            // Write to the response and end it
            response.end("Hello World from Vert.x-Web!")
        }

        server.requestHandler(router).listen(8080)
    }

    private fun setOAuth(router: Router, path: String, authProvider: OAuth2Auth) {
        val oauth2 = OAuth2AuthHandler.create(authProvider, "http://localhost:8080/callback")
        oauth2.addAuthority("scope")
        router.route().handler(CookieHandler.create())
        val sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx))
        sessionHandler.setCookieHttpOnlyFlag(true)
        router.route().handler(sessionHandler)

        // We need a user session handler too to make sure
        // the user is stored in the session between requests
        router.route().handler(UserSessionHandler.create(authProvider))

        oauth2.setupCallback(router.route())
        router.route(path).handler(oauth2)
    }

}