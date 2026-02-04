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
   * Concrete classes implement this function to process the inputs sent by the client.
   *
   * @param value The current process value.
   * @param inputs Any inputs sent by the client.
   * @param operation Indicates which operation was called by the client.
   * @param hurdleGroupId An optional identifier of a profile that groups hurdles.
   *
   * @return The resulting [ProcessingState].
   */
  fun processInputs(
    value: V,
    inputs: List<Input<R>>,
    operation: Operation,
    hurdleGroupId: String? = null
  ): Result<ProcessingState<V, R>>

  /**
   * Called when something goes wrong.
   *
   * @param failure The failure that happened.
   * @param value The current value.
   * @return The updated value if failing implies, for example, transitioning to a failed state.
   */
  fun handleFailure(failure: Throwable, value: V): Result<V>

  /**
   * What to do if the client sends a `CANCELLED` hurdle response. By default, a
   * [xyz.block.domainapi.DomainApiError.ProcessWasCancelled] exception is returned.
   */
  fun handleCancelled(
    value: V,
    requirementResults: List<Input<R>>
  ): Result<ProcessingState<V, R>?> = result {
    requirementResults.find { it.result == ResultCode.CANCELLED }?.let {
      raise(DomainApiError.ProcessWasCancelled(it.id.toString()))
    }
  }
}

enum class Operation { CREATE, EXECUTE, RESUME }
