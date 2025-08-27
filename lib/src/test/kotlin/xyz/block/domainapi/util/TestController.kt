package xyz.block.domainapi.util

import app.cash.kfsm.StateMachine
import app.cash.quiver.extensions.success
import arrow.core.raise.result
import xyz.block.domainapi.Input
import xyz.block.domainapi.UserInteraction

class TestController(
  override val stateMachine: StateMachine<String, TestValue, TestState>
) : InfoCollectionController<String, TestState, TestValue, TestRequirement> {
  override val pendingCollectionState = Initial

  override fun findMissingRequirements(value: TestValue) =
    when (value.state) {
      is Initial -> listOf(TestRequirement.REQ1, TestRequirement.REQ2).success()
      is Processing -> emptyList<TestRequirement>().success()
      is Complete -> emptyList<TestRequirement>().success()
      Final -> emptyList<TestRequirement>().success()
    }

  override fun updateValue(
    value: TestValue,
    hurdleResponse: Input.HurdleResponse<TestRequirement>
  ) = when (hurdleResponse.id) {
    TestRequirement.REQ1 -> value.copy(data = "Req1").success()
    TestRequirement.REQ2 -> value.copy(data = "Req2").success()
  }

  override fun onCancel(value: TestValue) = value.success()

  override fun getHurdlesForRequirementId(
    requirementId: TestRequirement,
    value: TestValue,
    previousHurdles: List<UserInteraction.Hurdle<TestRequirement>>
  ): Result<List<UserInteraction.Hurdle<TestRequirement>>> = listOf(TestHurdle(requirementId)).success()

  override fun requiresSecureEndpoint(requirement: TestRequirement): Boolean = false

  override fun transition(value: TestValue): Result<TestValue> =
    result {
      when (value.state) {
        is Initial -> {
          val updatedValue = stateMachine.transitionTo(value, Processing).bind()
          stateMachine.transitionTo(updatedValue, Complete).bind()
        }
        else -> raise(IllegalStateException("Invalid state ${value.state}"))
      }
    }

  override fun handleFailure(failure: Throwable, value: TestValue): Result<TestValue> = result {
    value
  }
}
