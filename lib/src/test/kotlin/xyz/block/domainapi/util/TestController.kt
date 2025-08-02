package xyz.block.domainapi.util

import app.cash.kfsm.guice.StateMachine
import app.cash.quiver.extensions.success
import arrow.core.raise.result
import org.jooq.DSLContext
import xyz.block.domainapi.DomainApi
import xyz.block.domainapi.Input

class TestController(stateMachine: StateMachine<String, TestValue, TestState>) :
  InfoCollectionController<String, TestState, TestValue, TestRequirement>(
    pendingCollectionState = TestState.Initial,
    stateMachine = stateMachine,
  ) {

  override fun findMissingRequirements(value: TestValue) =
    when (value.state) {
      is TestState.Initial -> listOf(TestRequirement.REQ1, TestRequirement.REQ2).success()
      is TestState.Processing -> emptyList<TestRequirement>().success()
      is TestState.Complete -> emptyList<TestRequirement>().success()
      TestState.Final -> emptyList<TestRequirement>().success()
    }

  override fun updateValue(
    value: TestValue,
    hurdleResponse: Input.HurdleResponse<TestRequirement>,
  ) =
    when (hurdleResponse.id) {
      TestRequirement.REQ1 -> value.copy(data = "Req1").success()
      TestRequirement.REQ2 -> value.copy(data = "Req2").success()
    }

  override fun onCancel(value: TestValue, ctx: DSLContext?) = value.success()

  override fun getHurdleForRequirementId(requirementId: TestRequirement, value: TestValue) =
    TestHurdle(requirementId).success()

  override fun requiresSecureEndpoint(requirement: TestRequirement): Boolean = false

  override fun transition(value: TestValue): Result<TestValue> = result {
    when (value.state) {
      is TestState.Initial -> {
        val updatedValue = stateMachine.transitionToState(value, TestState.Processing).bind()
        stateMachine.transitionToState(updatedValue, TestState.Complete).bind()
      }
      else -> raise(IllegalStateException("Invalid state ${value.state}"))
    }
  }
}
