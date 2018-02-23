import com.typesafe.config.{Config, ConfigFactory}
import com.warrenstrange.googleauth.GoogleAuthenticator
import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class SingleLeaseMultipleChecks extends Simulation {
  val config: Config = ConfigFactory.load()

  val authenticator: GoogleAuthenticator = new GoogleAuthenticator()

  val requestJwt = exec(
    http("Lease token")
      .post("/lease")
      .formParam("microservice", config.getString("service.name"))
      .formParam("oneTimePassword", "${otp}")
      .check(bodyString.saveAs("jwt"))
  )

  val checkJwt = exec(
    http("Check token")
      .get("/details")
      .header("Authorization", "Bearer ${jwt}")
      .check(substring(config.getString("service.name")))
  )

  val otpFeeder = Iterator.continually(Map("otp" -> authenticator.getTotpPassword(config.getString("service.pass"))))

  setUp(
    scenario("Testing")
      .feed(otpFeeder)
      .exec(requestJwt)
      .repeat(config.getInt("repeats")) {
        exec(checkJwt)
      }
      .inject(
        rampUsers(10000) over (1 minute)
      )
  )
    .protocols(
      http
        .baseURL(config.getString("baseUrl"))
    )
}
