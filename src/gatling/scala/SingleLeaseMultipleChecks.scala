import com.typesafe.config.{Config, ConfigFactory}
import com.warrenstrange.googleauth.GoogleAuthenticator
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.concurrent.duration._

class SingleLeaseMultipleChecks extends Simulation {
  private val config: Config = ConfigFactory.load()

  private val authenticator: GoogleAuthenticator = new GoogleAuthenticator()

  private val requestJwt = exec(
    applyOptionalProxy(
      http("Lease token")
      .post("lease")
      .body(StringBody("""{"microservice":"""" + config.getString("service.name") + """","oneTimePassword":"${otp}"}"""))
      .asJSON
      .check(bodyString.saveAs("jwt"))
    )
  )

  private val checkJwt = exec(
    applyOptionalProxy(
      http("Check token")
      .get("details")
      .header("Authorization", "Bearer ${jwt}")
      .check(status not 500)
    )
  )

  private def applyOptionalProxy(req: HttpRequestBuilder): HttpRequestBuilder =
    if (config.getString("proxy.host").isEmpty) req else
      req.proxy(Proxy(config.getString("proxy.host"), config.getInt("proxy.port")))

  private val otpFeeder = Iterator.continually(Map("otp" -> authenticator.getTotpPassword(config.getString("service.pass"))))

  setUp(
    scenario("Testing")
      .feed(otpFeeder)
      .exec(requestJwt)
      .during(20 minutes) {
        exec(checkJwt)
        .pause(40 seconds, 60 seconds)
      }
      .inject(
        rampUsers(5000) over (20 minutes)
      )
  )
  .protocols(
    http.baseURL(config.getString("baseUrl"))
  )
}
