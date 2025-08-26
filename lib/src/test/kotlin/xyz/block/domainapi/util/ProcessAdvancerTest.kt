@file:Suppress("DEPRECATION")

package xyz.block.domainapi.util

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import xyz.block.domainapi.DomainApi.Endpoint.EXECUTE
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.ResultCode

class ProcessAdvancerTest {
  private class TestProcessAdvancer(
    private val stateMachine: StateMachine<String, TestValue, TestState>
  ) : ProcessAdvancer<String, TestState, TestValue, TestRequirement>() {
    override fun getController(instance: TestValue): Result<Controller<String, TestState, TestValue, TestRequirement>> =
      result {
        when (instance.state) {
          is Initial -> TestController(stateMachine)
          is Processing -> TestController(stateMachine)
          is Complete -> FinalController(stateMachine)
          else -> raise(IllegalStateException("Invalid state ${instance.state}"))
        }
      }
  }

  @Test
  fun `should return hurdles when requirements are missing`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", Initial)
    val result = advancer.execute(value, emptyList(), Operation.EXECUTE)
    val expected =
      ExecuteResponse(
        "test",
        listOf(TestHurdle(TestRequirement.REQ1), TestHurdle(TestRequirement.REQ2)),
        EXECUTE
      )
    result.getOrThrow().id shouldBe expected.id
    result.getOrThrow().interactions shouldBe expected.interactions
  }

  // TODO (ametke): rewrite example so it works with the new changes
  fun `should return hurdles when some requirements are missing`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", Initial)
    val result =
      advancer.execute(
        value,
        listOf(TestRequirementResult(TestRequirement.REQ1, ResultCode.CLEARED)),
        Operation.EXECUTE
      )
    val expected = ExecuteResponse("test", listOf(TestHurdle(TestRequirement.REQ2)), EXECUTE)
    result.getOrThrow().id shouldBe expected.id
    result.getOrThrow().interactions shouldBe expected.interactions
  }

  // TODO (ametke): rewrite example so it works with the new changes
  fun `should complete when all requirements are met`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", Initial)
    val result =
      advancer.execute(
        value,
        listOf(
          TestRequirementResult(TestRequirement.REQ1, ResultCode.CLEARED),
          TestRequirementResult(TestRequirement.REQ2, ResultCode.CLEARED)
        ),
        Operation.EXECUTE
      )
    val expected = ExecuteResponse<String, TestRequirement>("test", emptyList(), EXECUTE)
    result.getOrThrow().id shouldBe expected.id
    result.getOrThrow().interactions shouldBe expected.interactions
  }

  @Test
  fun `should handle cancelled requirement result`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", Initial)
    val result =
      advancer.execute(
        value,
        listOf(TestRequirementResult(TestRequirement.REQ1, ResultCode.CANCELLED)),
        Operation.EXECUTE
      )
    result shouldBeFailure DomainApiError.ProcessWasCancelled("REQ1")
  }

  @Test
  fun `should handle waiting state`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", Complete)
    val result = advancer.execute(value, emptyList(), Operation.EXECUTE)
    val expected = ExecuteResponse<String, TestRequirement>("test", emptyList(), EXECUTE)
    result.getOrThrow().id shouldBe expected.id
    result.getOrThrow().interactions shouldBe expected.interactions
  }

  @Test
  fun `should not handle terminal state`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", Final)
    val result = advancer.execute(value, emptyList(), Operation.EXECUTE)
    result.shouldBeFailure()
  }
}
