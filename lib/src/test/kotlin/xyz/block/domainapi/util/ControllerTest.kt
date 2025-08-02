@file:Suppress("DEPRECATION")

package xyz.block.domainapi.util

import app.cash.kfsm.guice.StateMachine
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import xyz.block.domainapi.DomainApi
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ResultCode

@RunWith(JUnitPlatform::class)
class ControllerTest {
  private val injector = Guice.createInjector(TestModule())
  private val stateMachine =
    injector.getInstance(Key.get(object : TypeLiteral<StateMachine<String, TestValue, TestState>>() {}))

  @Test
  fun `should fail when value is in wrong state`() {
    val controller = TestController(stateMachine)
    val value = TestValue("test", TestState.Complete)

    val result = controller.process(value, emptyList())

    result.isFailure shouldBe true
    result.exceptionOrNull()!!.message shouldBe "State should be Initial but was Complete"
  }

  @Test
  fun `should return hurdles when requirements are missing`() {
    val controller = TestController(stateMachine)
    val value = TestValue("test", TestState.Initial)

    val result = controller.process(value, emptyList())

    result.getOrThrow() shouldBe
      ProcessingState.UserInteractions(
        listOf(TestHurdle(TestRequirement.REQ1), TestHurdle(TestRequirement.REQ2)),
        nextEndpoint = DomainApi.Endpoint.EXECUTE
      )
  }

  @Test
  fun `should complete when all requirements are met`() {
    val controller = TestController(stateMachine)
    val value = TestValue("test", TestState.Initial)

    val result =
      controller.process(
        value,
        listOf(
          TestRequirementResult(TestRequirement.REQ1, ResultCode.CLEARED),
          TestRequirementResult(TestRequirement.REQ2, ResultCode.CLEARED),
        ),
      )

    val processingState = result.getOrThrow() as ProcessingState.Complete<TestValue, TestRequirement>
    processingState.value.state shouldBe TestState.Complete
    processingState.value.data shouldBe "Req2"
  }

  @Test
  fun `should handle cancelled requirement result`() {
    val controller = TestController(stateMachine)
    val value = TestValue("test", TestState.Initial)
    val requirementResult = TestRequirementResult(TestRequirement.REQ1, ResultCode.CANCELLED)

    val result = controller.process(value, listOf(requirementResult))

    result.exceptionOrNull() shouldBe DomainApiError.ProcessWasCancelled(TestRequirement.REQ1.toString())
  }

  @Test
  fun `should update value when requirement result is processed`() {
    val controller = TestController(stateMachine)
    val value = TestValue("test", TestState.Initial)

    val result =
      controller.process(
        value,
        listOf(
          TestRequirementResult(TestRequirement.REQ1, ResultCode.CLEARED),
          TestRequirementResult(TestRequirement.REQ2, ResultCode.CLEARED),
        ),
      )

    val processingState = result.getOrThrow() as ProcessingState.Complete<TestValue, TestRequirement>
    processingState.value.state shouldBe TestState.Complete
    processingState.value.data shouldBe "Req2"
  }
}
