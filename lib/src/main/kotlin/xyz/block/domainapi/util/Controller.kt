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
 * An interface that represents a controller in the implementation of a domain API based on a state machine.
 * Concrete implementations are responsible for dealing with interactions through the _execute_ and _resume_ endpoints
 * for a specific state of the process value.
 */
interface Controller<ID, S : State<ID, V, S>, V : Value<ID, V, S>, R> {

  /** Implementers must supply the state machine (e.g., via constructor). */
  val stateMachine: StateMachine<ID, V, S>
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
  ): Result<ProcessingState<V, R>> = result {
    result {
      handleCancelled(value, inputs).bind() ?: processInputs(value, inputs, operation).bind()
    }.onFailure {
      handleFailure(it, value).bind()
    }.bind()
  }

  /**
   * Concrete classes implement this function to process the inputs sent by the client.
   *
   * @param value The current process value.
   * @param inputs Any inputs sent by the client.
   * @param operation Indicates which operation was called by the client.
   *
   * @return The resulting [ProcessingState].
   */
  abstract fun processInputs(
    value: V,
    inputs: List<Input<R>>,
    operation: Operation
  ): Result<ProcessingState<V, R>>

  /**
   * Called when something goes wrong.
   *
   * @param failure The failure that happened.
   * @param value The current value.
   * @return The updated value if failing implies, for example, transitioning to a failed state.
   */
  abstract fun handleFailure(failure: Throwable, value: V): Result<V>

  /**
   * What to do if the client sends a `CANCELLED` hurdle response. By default, a
   * [xyz.block.domainapi.DomainApiError.ProcessWasCancelled] exception is returned.
   */
  open fun handleCancelled(
    value: V,
    requirementResults: List<Input<R>>
  ): Result<ProcessingState<V, R>?> = result {
    requirementResults.find { it.result == ResultCode.CANCELLED }?.let {
      raise(DomainApiError.ProcessWasCancelled(it.id.toString()))
    }
  }
}

enum class Operation { CREATE, EXECUTE, RESUME }
