@file:Suppress("DEPRECATION")

package xyz.block.domainapi.util

import app.cash.kfsm.guice.StateMachine
import arrow.core.raise.result
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import xyz.block.domainapi.DomainApi.Endpoint.EXECUTE
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.ResultCode

@RunWith(JUnitPlatform::class)
class ProcessAdvancerTest {
  private val injector = Guice.createInjector(TestModule())
  private val stateMachine =
    injector.getInstance(Key.get(object : TypeLiteral<StateMachine<String, TestValue, TestState>>() {}))

  private class TestProcessAdvancer(private val stateMachine: StateMachine<String, TestValue, TestState>) :
    ProcessAdvancer<String, TestState, TestValue, TestRequirement>() {
    override fun getController(instance: TestValue): Result<Controller<String, TestState, TestValue, TestRequirement>> =
      result {
        when (instance.state) {
          is TestState.Initial -> TestController(stateMachine)
          is TestState.Processing -> TestController(stateMachine)
          is TestState.Complete -> FinalController(stateMachine)
          else -> raise(IllegalStateException("Invalid state ${instance.state}"))
        }
      }
  }

  @Test
  fun `should return hurdles when requirements are missing`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", TestState.Initial)
    val result = advancer.execute(value, emptyList())
    val expected = ExecuteResponse(
      "test",
      listOf(TestHurdle(TestRequirement.REQ1), TestHurdle(TestRequirement.REQ2)),
      EXECUTE
    )
    result.getOrThrow().id shouldBe expected.id
    result.getOrThrow().interactions shouldBe expected.interactions
  }

  @Test
  fun `should return hurdles when some requirements are missing`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", TestState.Initial)
    val result = advancer.execute(value, listOf(TestRequirementResult(TestRequirement.REQ1, ResultCode.CLEARED)))
    val expected = ExecuteResponse("test", listOf(TestHurdle(TestRequirement.REQ2)), EXECUTE)
    result.getOrThrow().id shouldBe expected.id
    result.getOrThrow().interactions shouldBe expected.interactions
  }

  @Test
  fun `should complete when all requirements are met`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", TestState.Initial)
    val result =
      advancer.execute(
        value,
        listOf(
          TestRequirementResult(TestRequirement.REQ1, ResultCode.CLEARED),
          TestRequirementResult(TestRequirement.REQ2, ResultCode.CLEARED),
        ),
      )
    val expected = ExecuteResponse<String, TestRequirement>("test", emptyList(), EXECUTE)
    result.getOrThrow().id shouldBe expected.id
    result.getOrThrow().interactions shouldBe expected.interactions
  }

  @Test
  fun `should handle cancelled requirement result`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", TestState.Initial)
    val result =
      advancer.execute(value, listOf(TestRequirementResult(TestRequirement.REQ1, ResultCode.CANCELLED)))
    result shouldBeFailure DomainApiError.ProcessWasCancelled("REQ1")
  }

  @Test
  fun `should handle waiting state`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", TestState.Complete)
    val result = advancer.execute(value, emptyList())
    val expected = ExecuteResponse<String, TestRequirement>("test", emptyList(), EXECUTE)
    result.getOrThrow().id shouldBe expected.id
    result.getOrThrow().interactions shouldBe expected.interactions
  }

  @Test
  fun `should not handle terminal state`() {
    val advancer = TestProcessAdvancer(stateMachine)
    val value = TestValue("test", TestState.Final)
    val result = advancer.execute(value, emptyList())
    result.shouldBeFailure()
  }
}
