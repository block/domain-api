package xyz.block.domainapi.util

import app.cash.kfsm.State
import app.cash.kfsm.Value
import app.cash.kfsm.StateMachine
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState

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
   * @param ctx A Jooq DSLContext that can be used to perform multiple updates in a transaction.
   * @return Either the updated process value or a list of hurdles that are still missing.
   */
  abstract fun process(value: V, inputs: List<Input<R>>): Result<ProcessingState<V, R>>
}
