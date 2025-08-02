package xyz.block.domainapi

/**
 * Represents the state of processing for a domain operation.
 *
 * @param T The type of the completed value
 * @param R The type of requirement/hurdle
 */
sealed class ProcessingState<T, R> {
  /** Processing is complete with a result value. */
  data class Complete<T, R>(val value: T) : ProcessingState<T, R>()

  /** Processing is waiting for an external event with no specific hurdles. */
  data class Waiting<T, R>(val value: T) : ProcessingState<T, R>()

  /** Processing requires specific hurdles to be overcome. */
  data class UserInteractions<T, R>(
    val hurdles: List<UserInteraction<R>>,
    val nextEndpoint: DomainApi.Endpoint?
  ) : ProcessingState<T, R>()
}
