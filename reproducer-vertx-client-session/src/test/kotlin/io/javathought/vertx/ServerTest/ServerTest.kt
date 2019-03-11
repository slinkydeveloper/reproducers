package io.javathought.vertx.ServerTest


import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2FlowType
import io.vertx.ext.web.client.WebClientSession
import io.vertx.ext.web.client.spi.CookieStore
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.ext.auth.oauth2.oAuth2ClientOptionsOf
import io.vertx.rxjava.core.Vertx
import io.vertx.rxjava.ext.web.Router
import io.vertx.rxjava.ext.web.client.WebClient
import io.vertx.rxjava.ext.web.handler.BodyHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URLEncoder

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension::class)
class ServerTest {


    @BeforeAll
    fun setUp(vertx: Vertx, context: VertxTestContext) {

        val oAuth = TestOAuth(vertx.delegate)
        val serverTokenPath = oAuth.tokenPath

        vertx.deployVerticle(
            HttpVerticle(oAuth),
            context.completing()
        )

        val server = vertx.createHttpServer()
        val router = Router.router(vertx)

        router.get("/oauth/authorize").handler { rc ->
            // receive : /oauth/authorize?client_id=13393&redirect_uri=http://localhost:8080/callback&response_type=code&scope=profile:read_all
            // redirect to :
            // http://localhost:8080/callback?state=/welcome&code=<code>&scope=<scope>
            val state = URLEncoder.encode(rc.request().getParam("state"), "UTF-8")
            log.info("request auth from state {0}", state)
            rc.response().putHeader(
                "location",
                "http://localhost:8080/callback?state=${state}&code=01234567890scope=read,profile:read_all"
            )
                .setStatusCode(302)
                .end("reroute")
        }

        router.post(serverTokenPath).handler(BodyHandler.create())
        router.post(serverTokenPath).handler { rc ->
            log.info("request token with code {0}", rc.request().formAttributes().get("code"))

            val responseString = """
            { "token_type" : "Bearer",
                    "access_token" : "987654321234567898765432123456789",
                "refresh_token" : "1234567898765432112345678987654321",
                    "expires_at" : 1531378346 }
            """

            rc.response().putHeader("content-type", "application/json; charset=UTF-8")
                .setStatusCode(200)
                .end(responseString)


        }

        server.requestHandler(router).listen(8081)
    }

    @Test
    fun testMyApplication(vertx: Vertx, testContext: VertxTestContext) {

        val client = WebClient.create(vertx)  //, WebClientOptions().setMaxRedirects(2))
        val session = WebClientSession.create(client.delegate, CookieStore.build())

        session.get(8080, "localhost", "/")
            .`as`(BodyCodec.string())
            .send(testContext.succeeding<io.vertx.ext.web.client.HttpResponse<String>> { response ->
                testContext.verify {
                    assertThat(response.body()).isEqualTo("Hello World from Vert.x-Web!")
                    testContext.completeNow()
                }
            })

    }

    companion object {
        private val log = LoggerFactory.getLogger(ServerTest::class.java)
    }

}


class TestOAuth(vertx: io.vertx.core.Vertx) {

    val tokenPath: String
        get() = "/oauth/token"

    var provider: OAuth2Auth = OAuth2Auth.create(
        vertx, oAuth2ClientOptionsOf(
            flow = OAuth2FlowType.AUTH_CODE,
            clientID = "12345",
            clientSecret = "67890",
            site = "http://localhost:8081",
            tokenPath = tokenPath,
            userInfoPath = "/api/user",
            authorizationPath = "/oauth/authorize",
            scopeSeparator = ","
        )
    )

}
