package xyz.block.domainapi.util

import app.cash.kfsm.State
import app.cash.kfsm.StateMachine
import app.cash.kfsm.Value
import app.cash.quiver.extensions.failure
import app.cash.quiver.extensions.success
import arrow.core.raise.result
import xyz.block.domainapi.DomainApi
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.DomainApiError.UnsupportedHurdleResultCode
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction
import xyz.block.domainapi.UserInteraction.Hurdle

@Suppress("TooManyFunctions")
abstract class InfoCollectionController<
  ID,
  STATE : State<ID, T, STATE>,
  T : Value<ID, T, STATE>,
  R
>(
  private val pendingCollectionState: STATE,
  stateMachine: StateMachine<ID, T, STATE>
) : Controller<ID, STATE, T, R>(stateMachine) {
  override fun processInputs(
    value: T,
    inputs: List<Input<R>>,
    operation: Operation
  ): Result<ProcessingState<T, R>> =
    result {
      processInputsFromOperation(value, inputs, operation).bind()
        ?: if (inputs.all { it is Input.HurdleResponse }) {
          val hurdleResponses = inputs.map { it as Input.HurdleResponse }
          when (value.state) {
            pendingCollectionState -> processPendingCollectionState(value, hurdleResponses).bind()
            else ->
              raise(
                IllegalStateException("State should be $pendingCollectionState but was ${value.state}")
              )
          }
        } else {
          raise(IllegalArgumentException("Inputs should be hurdle responses"))
        }
    }

  /**
   * Provides a mechanism to react differently depending on the operation that invoked this
   * controller. Does nothing by default.
   */
  open fun processInputsFromOperation(
    value: T,
    inputs: List<Input<R>>,
    operation: Operation
  ): Result<ProcessingState<T, R>?> = Result.success(null)

  /** When all the requirements are satisfied, this function is called to transition the process to the next state. */
  abstract fun transition(value: T): Result<T>

  /**
   * Finds any missing requirements for this controller by inspecting the process value.
   *
   * @param value The process value to inspect.
   * @return A set of missing requirements.
   */
  abstract fun findMissingRequirements(value: T): Result<List<R>>

  /**
   * Updates the process value with the result of a requirement. The requirement result might just
   * trigger a state transition in which case there will be nothing to update in the process value.
   *
   * @param value The process value to update.
   * @param hurdleResponse The response to a hurdle sent by the server.
   * @return The updated process value.
   */
  abstract fun updateValue(
    value: T,
    hurdleResponse: Input.HurdleResponse<R>
  ): Result<T>

  /** Called if the process is cancelled. */
  abstract fun onCancel(value: T): Result<T>

  /**
   * Returns a hurdle for the given requirement ID. If previous hurdles are modified then new
   * versions of these are returned.
   *
   * @param requirementId The ID of the requirement.
   * @param value The process value.
   * @param previousHurdles Any hurdles that have been generated so far.
   * @return A list of hurdles that contains the hurdle for the given requirement ID, if one was
   * generated, and any previous hurdles that were modified.
   */
  abstract fun getHurdlesForRequirementId(
    requirementId: R,
    value: T,
    previousHurdles: List<Hurdle<R>>
  ): Result<List<Hurdle<R>>>

  /**
   * Called when something goes wrong and gets passed in the most recent version of the process.
   *
   * @param failure The failure that happened.
   * @param value The current value.
   * @return The updated value if failing implies, for example, transitioning to a failed state.
   */
  abstract fun fail(
    failure: Throwable,
    value: T
  ): Result<T>

  /**
   * The info collection controller might need to end by showing a notification. For example, if the
   * user is shown a scam warning, and they decide to cancel the withdrawal, then the system might
   * will want to return a final notification to indicate to the client that a final screen should
   * be shown to the user and that no additional user input is expected.
   *
   * @param value The process value.
   * @return A final hurdle for the given process value, if required.
   */
  open fun getFinalNotification(value: T): Result<UserInteraction.Notification<R>?> = null.success()

  @Suppress("CognitiveComplexMethod")
  private fun processPendingCollectionState(
    value: T,
    hurdleResponses: List<Input.HurdleResponse<R>>
  ): Result<ProcessingState<T, R>> =
    result {
      // Otherwise we need to check the hurdle results to see if everything that is required has been sent to us
      // But first we need to check if the process was cancelled - in this case there is no need to continue
      // processing
      checkCancelled(value, hurdleResponses).bind()

      // Find any requirements that are missing to be able to transition to the next state
      val missingRequirements = findMissingRequirements(value).bind()

      // If all requirements are complete then we call the onComplete function
      if (missingRequirements.isEmpty()) {
        val updatedValue = transition(value).bind()
        val finalNotification = getFinalNotification(updatedValue).bind()
        if (finalNotification != null) {
          ProcessingState.UserInteractions(listOf(finalNotification), null)
        } else {
          ProcessingState.Complete(updatedValue)
        }
      } else {
        // Updating results and, if complete, transitioning to the finished collection state should be done in a single
        // transaction
        val (updatedMissingRequirements, updatedValue) =
          processResultsAndMaybeTransition(
            value,
            hurdleResponses,
            missingRequirements
          ).bind()
        if (updatedMissingRequirements.isEmpty()) {
          val finalNotification = getFinalNotification(updatedValue).bind()
          if (finalNotification != null) {
            ProcessingState.UserInteractions(listOf(finalNotification), null)
          } else {
            ProcessingState.Complete(updatedValue)
          }
        } else {
          // This is likely to involve RPC calls so it's done outside the transaction
          val hurdles =
            updatedMissingRequirements.fold(emptyList<Hurdle<R>>()) {
              acc,
              requirementId
              ->
              val newHurdles = getHurdlesForRequirementId(requirementId, updatedValue, acc).bind()
              mergeHurdles(acc, newHurdles)
            }
          ProcessingState.UserInteractions(
            hurdles = hurdles,
            nextEndpoint =
              if (hurdles.any { requiresSecureEndpoint(it.id) }) {
                DomainApi.Endpoint.SECURE_EXECUTE
              } else {
                DomainApi.Endpoint.EXECUTE
              }
          )
        }
      }
    }

  private fun mergeHurdles(
    current: List<Hurdle<R>>,
    updates: List<Hurdle<R>>
  ): List<Hurdle<R>> {
    val updateMap = updates.associateBy { it.id }
    val updatedCurrent =
      current.map { hurdle ->
        updateMap[hurdle.id] ?: hurdle
      }
    val currentIds = current.map { it.id }.toSet()
    val newHurdles = updates.filterNot { it.id in currentIds }
    return updatedCurrent + newHurdles
  }

  abstract fun requiresSecureEndpoint(requirement: R): Boolean

  open fun goBack(
    value: T,
    hurdleResponse: Input.HurdleResponse<R>
  ): Result<T> =
    UnsupportedHurdleResultCode(
      value.id.toString(),
      ResultCode.BACK
    ).failure()

  private fun processResultsAndMaybeTransition(
    value: T,
    hurdleResponses: List<Input.HurdleResponse<R>>,
    missingRequirements: List<R>
  ) = result {
    val (_, updatedValue) =
      processHurdleResponses(value, hurdleResponses, missingRequirements).bind()
    val updatedMissingRequirements = findMissingRequirements(updatedValue).bind()
    val newValue =
      if (updatedMissingRequirements.isEmpty()) {
        transition(updatedValue).bind()
      } else {
        updatedValue
      }
    Pair(updatedMissingRequirements, newValue)
  }

  /** Checks if the process was canceled and calls the onCancel function. */
  private fun checkCancelled(
    value: T,
    requirementResults: List<Input<R>>
  ): Result<Unit> =
    result {
      requirementResults
        .find { it.result == ResultCode.CANCELLED }
        ?.let {
          onCancel(value).bind()
          raise(DomainApiError.ProcessWasCancelled(it.id.toString()))
        }
    }

  private fun processHurdleResponses(
    value: T,
    hurdleResponses: List<Input.HurdleResponse<R>>,
    missingRequirements: List<R>
  ) = result {
    hurdleResponses
      .fold(Pair(missingRequirements, value).success()) {
        accumulator: Result<Pair<List<R>, T>>,
        hurdleResponse
        ->
        // These are the current requirements that are still missing
        val (currentMissingRequirements, currentValue) = accumulator.bind()

        // If the hurdle result is in the missing set then we process it
        if (currentMissingRequirements.contains(hurdleResponse.id)) {
          // Process the requirement response
          val updatedValue =
            when (hurdleResponse.result) {
              ResultCode.CLEARED, ResultCode.FAILED -> updateValue(currentValue, hurdleResponse)
              ResultCode.SKIPPED -> currentValue.success()
              ResultCode.BACK -> goBack(value, hurdleResponse)
              else ->
                IllegalArgumentException(
                  "Unexpected hurdle response code ${hurdleResponse.result}"
                ).failure()
            }.onFailure { fail(it, currentValue).bind() }.bind()

          // Once the result has been processed successfully then we remove from the set of missing requirements
          Result.success(Pair(currentMissingRequirements - hurdleResponse.id, updatedValue))
        } else {
          // If it is not we fail because we want to be strict about the results we accept
          val failure =
            DomainApiError.InvalidRequirementResult(
              value.id.toString(),
              hurdleResponse.id.toString()
            )
          fail(failure, currentValue).bind()
          raise(failure)
        }
      }.bind()
  }
}
