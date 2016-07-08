package mesosphere.marathon.integration

import mesosphere.marathon.health.HealthCheck
import mesosphere.marathon.integration.setup._
import mesosphere.marathon.state._
import org.scalatest.{ BeforeAndAfter, GivenWhenThen, Matchers }

import scala.concurrent.duration._

class GracefulTaskKillIntegrationTest
    extends IntegrationFunSuite
    with SingleMarathonIntegrationTest
    with Matchers
    with BeforeAndAfter
    with GivenWhenThen {

  //clean up state before running the test case
  before(cleanUp())

  // this command simulates a 'long terminating' application
  val appCommand: String = "trap \"sleep 20\" SIGTERM && sleep 100000"
  val taskKillGracePeriod = 10.seconds

  test("create a 'long terminating' app with custom taskKillGracePeriod duration") {
    Given("a new 'long terminating' app with taskKillGracePeriod set to 10 seconds")
    val app = AppDefinition(
      testBasePath / "app",
      cmd = Some(appCommand),
      taskKillGracePeriod = Some(taskKillGracePeriod))

    When("The app is deployed")
    val result = marathon.createAppV2(app)

    Then("The app is created")
    result.code should be (201) //Created
    waitForEvent("deployment_success")
    waitForTasks(app.id, 1) //make sure, the app has really started
    val taskId = marathon.tasks(app.id).value.head.id

    When("a task of an app is killed")
    val taskKillSentTimestamp = System.currentTimeMillis()
    marathon.killTask(app.id, taskId).code should be (200)

    waitForEventWith("status_update_event",
      _.info("taskStatus") == "TASK_KILLED",
      maxWait = taskKillGracePeriod.plus(2.seconds))

    val taskKilledReceivedTimestamp = System.currentTimeMillis()
    val waitedForTaskKilledEvent = (taskKilledReceivedTimestamp - taskKillSentTimestamp).milliseconds

    // the task_killed event should occur at least 10 seconds after sending it
    waitedForTaskKilledEvent should be >= taskKillGracePeriod
  }

  def healthCheck = HealthCheck(gracePeriod = 20.second, interval = 1.second, maxConsecutiveFailures = 10)
}
