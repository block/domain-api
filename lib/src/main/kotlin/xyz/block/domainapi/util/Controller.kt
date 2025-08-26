package xyz.block.domainapi.util

import app.cash.kfsm.State
import app.cash.kfsm.StateMachine
import app.cash.kfsm.Value
import arrow.core.raise.result
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ResultCode

/**
 * An abstract class that represents a controller in the implementation of a domain API based on a state machine.
 * Concrete implementations are responsible for dealing with interactions through the _execute_ and _resume_ endpoints
 * for a specific state of the process value.
 */
abstract class Controller<ID, S : State<ID, V, S>, V : Value<ID, V, S>, R>(
  val stateMachine: StateMachine<ID, V, S>
) {
  /**
   * Called when a client calls the execute or resume endpoints.
   *
   * @param value The process value.
   * @param inputs The inputs sent by the client or another service.
   * @param operation The operation that initiated this call.
   * @return Either the updated process value or a list of hurdles that are still missing.
   */
  fun process(
    value: V,
    inputs: List<Input<R>>,
    operation: Operation
  ): Result<ProcessingState<V, R>> =
    result {
      handleCancelled(value, inputs).bind() ?: processInputs(value, inputs, operation).bind()
    }

  abstract fun processInputs(
    value: V,
    inputs: List<Input<R>>,
    operation: Operation
  ): Result<ProcessingState<V, R>>

  open fun handleCancelled(
    value: V,
    requirementResults: List<Input<R>>
  ): Result<ProcessingState<V, R>?> =
    result {
      requirementResults.find { it.result == ResultCode.CANCELLED }?.let {
        raise(DomainApiError.ProcessWasCancelled(it.id.toString()))
      }
    }
}

enum class Operation { CREATE, EXECUTE, RESUME }
