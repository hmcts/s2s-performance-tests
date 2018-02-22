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
      .formParam("oneTimePassword", authenticator.getTotpPassword(config.getString("service.pass")))
      .check(bodyString.saveAs("jwt"))
  )

  val checkJwt = exec(
    http("Check token")
      .get("/details")
      .header("Authorization", "Bearer ${jwt}")
      .check(substring(config.getString("service.name")))
  )

  setUp(
    scenario("Testing")
      .exec(requestJwt)
      .repeat(config.getInt("repeats")) {
        exec(checkJwt)
      }
      .inject(
        rampUsers(10) over (10 seconds),
        constantUsersPerSec(100) during (10 seconds),
        nothingFor(5 seconds),
        constantUsersPerSec(200) during (10 seconds),
        nothingFor(5 seconds)
      )
  )
    .protocols(
      http
        .baseURL(config.getString("baseUrl"))
    )
}
