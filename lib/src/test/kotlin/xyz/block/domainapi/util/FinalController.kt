package xyz.block.domainapi.util

import app.cash.kfsm.StateMachine
import app.cash.quiver.extensions.failure
import arrow.core.raise.result
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState

class FinalController(
  override val stateMachine: StateMachine<String, TestValue, TestState>
) : Controller<String, TestState, TestValue, TestRequirement> {
  override fun processInputs(
    value: TestValue,
    inputs: List<Input<TestRequirement>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<TestValue, TestRequirement>> =
    result {
      when (value.state) {
        is Complete -> {
          val updatedValue = stateMachine.transitionTo(value, Final).bind()
          ProcessingState.Waiting(updatedValue)
        }
        else -> raise(IllegalStateException("Invalid state ${value.state}"))
      }
    }

  override fun handleFailure(failure: Throwable, value: TestValue): Result<TestValue> = failure.failure()
}
