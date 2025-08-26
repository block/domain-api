package xyz.block.domainapi.util

import app.cash.kfsm.State
import app.cash.kfsm.Value
import arrow.core.raise.result
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState

abstract class ProcessAdvancer<ID, STATE : State<ID, T, STATE>, T : Value<ID, T, STATE>, REQ> {
  /**
   * Get the controller for the current state of the process. This is implemented by subclasses to provide
   * state-specific controller logic.
   */
  protected abstract fun getController(instance: T): Result<Controller<ID, STATE, T, REQ>>

  /**
   * Provides a hook to react to failures in the process advancer. This can be used, e.g., to transition to a failed
   * state when an error happens.
   */
  open fun onExecuteFailure(
    instance: T,
    error: Throwable
  ) = Unit

  /**
   * This function is called to continue executing the process once the information required to continue has been
   * provided by the client. It does the following:
   * - Loads the entity.
   * - Fetches the controller for the current state.
   * - Calls the controller to process the results. This can return a ProcessingState that indicates:
   *     - Complete: The state has been processed and may need to move to the next state
   *     - Waiting: The state is waiting for an external event with no specific hurdles
   *     - Hurdles: There are specific hurdles that need to be overcome
   * - If hurdles are returned, these are returned to the client because they still need to be overcome.
   * - If Waiting is returned, an empty hurdle list is returned to indicate waiting.
   * - If Complete is returned with an updated process instance and if the state is not terminal then the controller for
   *   the next state is called with no requirement results. Otherwise, the process is complete and returns the result
   *   to the client.
   */
  fun execute(
    instance: T,
    requirementResults: List<Input<REQ>>,
    operation: Operation
  ): Result<ExecuteResponse<ID, REQ>> =
    result {
      val controller = getController(instance).bind()
      when (
        val processingState =
          controller
            .process(
              instance,
              requirementResults,
              operation
            ).bind()
      ) {
        // This means there are no more hurdles to overcome, so the process instance transitioned to a new state
        is ProcessingState.Complete -> {
          if (processingState.value.state.reachableStates
              .isEmpty()
          ) {
            // If this is a terminal state, then we return the result to the client
            ExecuteResponse(id = instance.id, interactions = emptyList(), nextEndpoint = null)
          } else {
            // Otherwise, we get the controller for the new state and execute it with no requirement results
            execute(processingState.value, emptyList(), operation).bind()
          }
        }

        // This means we're waiting for an external event with no specific hurdles
        is ProcessingState.Waiting -> {
          ExecuteResponse(id = instance.id, interactions = emptyList(), nextEndpoint = null)
        }

        // This means there are still hurdles to be overcome by the client so we return the missing hurdles
        is ProcessingState.UserInteractions -> {
          ExecuteResponse(
            id = instance.id,
            interactions = processingState.hurdles,
            nextEndpoint = processingState.nextEndpoint
          )
        }
      }
    }.onFailure { onExecuteFailure(instance, it) }
}
