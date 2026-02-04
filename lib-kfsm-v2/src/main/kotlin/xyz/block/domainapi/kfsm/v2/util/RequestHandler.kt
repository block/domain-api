package xyz.block.domainapi.kfsm.v2.util

import app.cash.kfsm.v2.State
import app.cash.kfsm.v2.Value
import arrow.core.raise.result
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState

/**
 * Handles incoming API requests by routing to the appropriate controller based on the current state.
 *
 * Subclasses implement [getController] to provide state-specific controller logic.
 */
abstract class RequestHandler<ID, STATE : State<STATE>, T : Value<ID, T, STATE>, REQ> {
  /**
   * Get the controller for the current state of the process. This is implemented by subclasses to provide
   * state-specific controller logic.
   */
  protected abstract fun getController(instance: T): Result<Controller<ID, STATE, T, REQ>>

  /**
   * Provides a hook to react to failures in the request handler. This can be used, e.g., to transition to a failed
   * state when an error happens.
   */
  open fun onExecuteFailure(instance: T, error: Throwable) = Unit

  /**
   * Handles an API request by routing to the appropriate controller and returning the result.
   *
   * This function:
   * - Fetches the controller for the current state
   * - Calls the controller to process the inputs
   * - Returns the appropriate response:
   *     - Complete: The state has been processed successfully
   *     - Waiting: The state is waiting for an external event
   *     - UserInteractions: There are hurdles that need to be overcome by the client
   *
   * Note: Automatic state progression (if needed) is handled by the KFSM EffectProcessor,
   * not by this handler.
   */
  fun execute(
    instance: T,
    requirementResults: List<Input<REQ>>,
    operation: Operation,
    hurdleGroupId: String? = null
  ): Result<ExecuteResponse<ID, REQ>> = result {
    when (val processingState = process(instance, requirementResults, operation, hurdleGroupId).bind()) {
      is ProcessingState.Complete ->
        ExecuteResponse(id = instance.id, interactions = emptyList(), nextEndpoint = null)

      is ProcessingState.Waiting ->
        ExecuteResponse(id = instance.id, interactions = emptyList(), nextEndpoint = null)

      is ProcessingState.UserInteractions ->
        ExecuteResponse(
          id = instance.id,
          interactions = processingState.hurdles,
          nextEndpoint = processingState.nextEndpoint
        )
    }
  }
    .onFailure { onExecuteFailure(instance, it) }

  /**
   * Called when a client calls the execute or resume endpoints.
   *
   * @param value The process value.
   * @param inputs The inputs sent by the client or another service.
   * @param operation The operation that initiated this call.
   * @param hurdleGroupId The id of an optional [xyz.block.domainapi.kfsm.v2.util.HurdleGroup] that
   * specifies how hurdles should be returned.
   * @return Either the updated process value or a list of hurdles that are still missing.
   */
  fun process(
    value: T,
    inputs: List<Input<REQ>>,
    operation: Operation,
    hurdleGroupId: String? = null
  ): Result<ProcessingState<T, REQ>> = result {
    val controller = getController(value).bind()
    result {
      controller.handleCancelled(value, inputs).bind()
        ?: controller.processInputs(value, inputs, operation, hurdleGroupId).bind()
    }.onFailure {
      controller.handleFailure(it, value).bind()
    }.bind()
  }
}
