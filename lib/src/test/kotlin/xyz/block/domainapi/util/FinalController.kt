package xyz.block.domainapi.util

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState

class FinalController(
  stateMachine: StateMachine<String, TestValue, TestState>
) : Controller<String, TestState, TestValue, TestRequirement>(stateMachine) {
  override fun process(
    value: TestValue,
    inputs: List<Input<TestRequirement>>
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
}
