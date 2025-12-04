package xyz.block.domainapi

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Interface of a service that executes a business process, for example, withdraw Bitcoin on-chain. It has the following
 * parametrised types:
 * - [INITIAL_REQUEST] The initial parameters required to start the process.
 * - [PROCESS_ID] The id of the entity in the process.
 * - [REQUIREMENT_ID] The id of a requirement that needs to be fulfilled in order to execute the process. This is
 *   typically an enumeration. An example of a requirement is a confirmation from the end user to proceed with a
 *   withdrawal.
 * - [ATTRIBUTE_ID] The id of an attribute of the process that can be updated. This is typically an enumeration. An
 *   example of an updatable attribute is the speed of an on-chain withdrawal. The concept of hurdles is also used to
 *   capture the necessary information to update the attribute, but an attribute does not necessarily map one-to-one
 *   with a requirement. There might be multiple requirements that need to be fulfilled in order to update an attribute.
 * - [PROCESS] The model of the process.
 */
interface DomainApi<INITIAL_REQUEST, PROCESS_ID, REQUIREMENT_ID, ATTRIBUTE_ID, PROCESS> {
  /**
   * Creates a new instance of a business process.
   *
   * @param id The id of the process instance. This id should not exist in the system. Typically,
   * this is a UUID.
   * @param initialRequest The initial parameters required to start the process.
   * @param hurdleGroupId The id of an optional [xyz.block.domainapi.util.HurdleGroup] that
   * specifies how hurdles should be returned.
   * @return The result of the executing the process or hurdles in there are requirements yet to be
   * fulfilled.
   */
  fun create(
    id: PROCESS_ID,
    initialRequest: INITIAL_REQUEST,
    hurdleGroupId: String? = null
  ): Result<ExecuteResponse<PROCESS_ID, REQUIREMENT_ID>>

  /**
   * Attempts to execute the business process if there are additional requirements that need to be
   * fulfilled after calling [create].
   *
   * @param id The id of the process instance.
   * @param hurdleResponses The results of any hurdles that had been sent previously to the client.
   * @param hurdleGroupId The id of an optional [xyz.block.domainapi.util.HurdleGroup] that
   * specifies how hurdles should be returned.
   * @return The result of the executing the process or hurdles in there are requirements yet to be
   * fulfilled.
   */
  fun execute(
    id: PROCESS_ID,
    hurdleResponses: List<Input.HurdleResponse<REQUIREMENT_ID>>,
    hurdleGroupId: String? = null
  ): Result<ExecuteResponse<PROCESS_ID, REQUIREMENT_ID>>

  /**
   * Represents endpoints that a client can call.
   */
  enum class Endpoint {
    CREATE,
    EXECUTE,
    SECURE_EXECUTE
  }

  /**
   * Attempts to resume the business process, if the process started but was interrupted while waiting for an external
   * result. Resuming is typically initiated by other systems, not by the end user. When handled asynchronously, the
   * handler for the event that triggers the resumption of the process will call this endpoint. For example, a Bitcoin
   * on-chain withdrawal may be interrupted if a manual risk check is necessary. Once the risk check is finalised then
   * the risk system will emit an event. The event's handler will then call this endpoint to resume the process.
   *
   * @param id The id of the process instance that is waiting to be resumed.
   * @param resumeResult The result of the requirement needed to resume the process.
   * @return Unit if resuming was successful or a failure otherwise.
   */
  fun resume(id: PROCESS_ID, resumeResult: Input.ResumeResult<REQUIREMENT_ID>): Result<Unit>

  /**
   * Updates an attribute of a business process instance. For example, the selected speed of a Bitcoin on-chain
   * withdrawal can be upgraded if the user decides to pay a higher fee to process the withdrawal more quickly.
   *
   * @param id The id of the process instance to be updated.
   * @param attributeId The attribute to be updated.
   * @param hurdleResponses The results of any hurdles sent previously to the client in order to update the selected
   *   attribute.
   * @return The result of updating the process or a failure otherwise.
   */
  fun update(
    id: PROCESS_ID,
    attributeId: ATTRIBUTE_ID,
    hurdleResponses: List<Input.HurdleResponse<REQUIREMENT_ID>>,
  ): Result<UpdateResponse<REQUIREMENT_ID, ATTRIBUTE_ID>>

  /**
   * Returns information about an instance of a process.
   *
   * @param id The id of the process instance.
   */
  fun get(id: PROCESS_ID): Result<ProcessInfo<ATTRIBUTE_ID, PROCESS>>

  /**
   * Searches for process instances that match the given search parameters.
   *
   * @param parameter The search parameters.
   * @param limit The maximum number of results to return per page.
   */
  fun search(parameter: SearchParameter, limit: Int): Result<SearchResult<ATTRIBUTE_ID, PROCESS>>
}

/**
 * Represents the result of attempting to execute a business process. If there are still
 * requirements that need to be fulfilled then the corresponding [UserInteraction.Hurdle]s will be
 * returned. Typically, when the process has been executed successfully or there is nothing more
 * that the user needs to do, the server will return a [UserInteraction.Notification] to the client.
 *
 * @param id The id of the process that was executed.
 * @param interactions The user interaction elements that still need to be resolved in order to
 * execute the process.
 * @param nextEndpoint Indicates which endpoint to call if there is more work to be done.
 */
data class ExecuteResponse<PROCESS_ID, REQUIREMENT_ID>(
  val id: PROCESS_ID,
  val interactions: List<UserInteraction<REQUIREMENT_ID>>,
  val nextEndpoint: DomainApi.Endpoint?
)

/**
 * Represents the result of attempting to update an attribute of a business process instance. The
 * first time that the [DomainApi.update] endpoint is called, the service returns
 * [UserInteraction.Hurdle]s to the client to capture the necessary information to update the
 * attribute. Typically, when an update is successful, the server will return a
 * [UserInteraction.Notification] to the client.
 *
 * @param id The id of the process instance that is being updated.
 * @param attributeId The attribute that is being updated.
 * @param interactions The hurdles that have to be overcome to update the attribute. Once the
 * attribute has been updated successfully, this list will be empty.
 */
data class UpdateResponse<REQUIREMENT_ID, ATTRIBUTE_ID>(
  val id: String,
  val attributeId: ATTRIBUTE_ID,
  val interactions: List<UserInteraction<REQUIREMENT_ID>>,
)

/**
 * Represent information sent from the server to the client.
 */
@Serializable
sealed class UserInteraction<REQUIREMENT_ID> {
  /**
   * An indication sent from the server to the client indicating that user action is required, e.g.,
   * fill out a form.
   *
   * @param id The id of the requirement. The parametrised type is typically an enumeration.
   */
  @Serializable
  open class Hurdle<REQUIREMENT_ID>(val id: REQUIREMENT_ID) : UserInteraction<REQUIREMENT_ID>()

  /**
   * A notification sent from the server to the client that does not require any action from the
   * user.
   *
   *  @param id The id of the requirement. The parametrised type is typically an enumeration.
   */
  @Serializable
  open class Notification<REQUIREMENT_ID>(val id: REQUIREMENT_ID) :
    UserInteraction<REQUIREMENT_ID>()
}

/**
 * Represents an input into the system for a requirement.
 */
@Serializable
sealed class Input<REQUIREMENT_ID>(val id: REQUIREMENT_ID, val result: ResultCode) {
  /**
   * Represents the response to a server sent to the client.
   *
   * @param id The id of the requirement. This is typically an enumeration.
   * @param code The result of attempting to overcome the hurdle.
   */
  @Serializable
  open class HurdleResponse<REQUIREMENT_ID>(id: REQUIREMENT_ID, code: ResultCode) :
    Input<REQUIREMENT_ID>(id, code)

  /**
   * A result sent to the business process as part of a resume operation. If there is specific data
   * that needs to be passed on to the process then it can be added in a subclass. For example, if
   * the process was suspended to do a manual risk review, the call to resume the process should
   * include the result of the review.
   *
   * @param id The id of the requirement whose result is needed to resume the process.
   */
  @Serializable
  open class ResumeResult<REQUIREMENT_ID>(id: REQUIREMENT_ID) :
    Input<REQUIREMENT_ID>(id, ResultCode.CLEARED)
}

/** The possible results of attempting to overcome a hurdle. */
enum class ResultCode {
  /** The result was cleared successfully. */
  CLEARED,

  /** There was an error when attempting to clear the result. */
  FAILED,

  /** The caller canceled the operation. */
  CANCELLED,

  /** The result was skipped. This is only relevant for optional hurdles. */
  SKIPPED,

  /** The caller decided to go back. This is only relevant for some hurdles. The service decides where to go back to. */
  BACK,

  /** This is a terminal result, and the process ended successfully. */
  FINISHED_OK,

  /** This is a terminal result, and the process ended with an error. */
  FINISHED_ERROR,
}

/**
 * Represents information about a process instance. Subclasses can be created to add additional information.
 *
 * @param id The id of the process instance.
 * @param process The process instance.
 * @param updatableAttributes The attributes if the process instance that can be updated, if any.
 */
class ProcessInfo<ATTRIBUTE_ID, PROCESS>(
  val id: String,
  val process: PROCESS,
  val updatableAttributes: List<ATTRIBUTE_ID>,
)

/**
 * Represents a parameter used to search for process instances. Search parameters can be either logical expressions (AND
 * or OR) or parameter expressions. The following example shows how to search for all Bitcoin on-chain withdrawals
 * updated in the last 24 hours that are in `PENDING_BLOCKCHAIN_CONFIRMATION` or `CONFIRMED_ON_CHAIN` state:
 * ```
 * val results = api.search(
 *   param = And(
 *     operands = listOf(
 *       ParameterExpression(
 *         parameter = UPDATED_AT,
 *         compareOperator = GREATER_THAN,
 *         values = DateValue(LocalDateTime.now().minus(Duration.ofHours(24))
 *       ),
 *       ParameterExpression(
 *         parameter = STATE,
 *         compareOperator = IN,
 *         values = PENDING_BLOCKCHAIN_CONFIRMATION, CONFIRMED_ON_CHAIN
 *       )
 *     )
 *   )
 * )
 * ```
 */
sealed class SearchParameter {
  sealed class LogicalExpression(open val operands: List<SearchParameter>) : SearchParameter() {
    data class And(override val operands: List<SearchParameter>) : LogicalExpression(operands)

    data class Or(override val operands: List<SearchParameter>) : LogicalExpression(operands)
  }

  data class ParameterExpression<T>(
    val parameter: T,
    val compareOperator: CompareOperator,
    val values: List<CompareValue>,
  ) : SearchParameter() {
    constructor(
      parameter: T,
      compareOperator: CompareOperator,
      vararg values: CompareValue,
    ) : this(parameter, compareOperator, values.toList())
  }
}

/** Represents a comparison operator in a search expression. */
enum class CompareOperator {
  EQUALS,
  NOT_EQUALS,
  GREATER_THAN,
  LESS_THAN,
  GREATER_THAN_OR_EQUAL,
  LESS_THAN_OR_EQUAL,
  IN,
  NOT_IN,
}

/** Represents a value used in a search expression. */
sealed class CompareValue {
  data class StringValue(val value: String) : CompareValue()

  data class DateValue(val value: LocalDate) : CompareValue()

  data class LongValue(val value: Long) : CompareValue()
}

/**
 * The result of a search operation.
 *
 * @param limit The maximum number of results that can be returned, i.e., the page size.
 * @param thisStart The index of the first result in the current page.
 * @param prevStart The index of the first result in the previous page, if any.
 * @param nextStart The index of the first result in the next page, if any.
 * @results The results of the search.
 */
data class SearchResult<ATTRIBUTE_ID, PROCESS>(
  val limit: Int,
  val thisStart: Int,
  val prevStart: Int?,
  val nextStart: Int?,
  val results: List<ProcessInfo<ATTRIBUTE_ID, PROCESS>>,
)

/** Common errors returned by the domain API. */
sealed class DomainApiError : Exception() {

  /**
   * A generic error occurred.
   *
   * @param cause The cause of the error.
   */
  data class ServerError(override val cause: Throwable?) : DomainApiError()

  /**
   * A generic client error occurred.
   *
   * @param message The error message.
   */
  data class ClientError(override val message: String) : DomainApiError()

  /**
   * An attempt to resume the process was made but the process cannot be resumed.
   *
   * @param id The id of the process instance that cannot be resumed.
   */
  data class CannotResumeProcess(val id: String, override val message: String) :
    DomainApiError(),
    WarnOnly

  /**
   * An attempt to update an attribute of the process was made, but it failed.
   *
   * @param id The id of the process instance that could not be updated.
   */
  data class CannotUpdateProcess(val id: String, override val message: String) :
    DomainApiError(),
    WarnOnly

  /**
   * A process instance cannot be found.
   *
   * @param id The id of the process instance that cannot be found.
   */
  data class ProcessNotFound(val id: String) :
    DomainApiError(),
    WarnOnly

  /**
   * A search parameter is invalid.
   *
   * @param parameter The invalid search parameter.
   */
  data class InvalidSearchParameter(val parameter: SearchParameter) :
    DomainApiError(),
    InfoOnly

  /**
   * The create endpoint was called with an id that already exists in the system.
   *
   * @param id The id of the process instance that already exists.
   */
  data class ProcessAlreadyExists(val id: String) :
    DomainApiError(),
    InfoOnly

  /**
   * The execute endpoint received a hurdle result for a hurdle that has already been processed.
   *
   * @param id The id of the process instance.
   * @param requirement The requirement that has already been processed.
   */
  data class HurdleResultAlreadyProcessed(val id: String, val requirement: String) :
    DomainApiError(),
    InfoOnly

  /**
   * The service received an unsupported request to go back. It is not possible to go back from every requirement.
   *
   * @param id - The id of the process instance.
   * @param requirement - The requirement where the illegal request to go back originated.
   */
  data class GoingBackUnsupported(val id: String, val requirement: String) :
    DomainApiError(),
    WarnOnly

  /**
   * The service received an unsupported request to skip a requirement. Only certain requirements can be skipped.
   *
   * @param id The id of the process instance.
   * @param requirement The requirement the user tried to skip.
   */
  data class SkippingRequirementUnsupported(val id: String, val requirement: String) :
    DomainApiError(),
    WarnOnly

  /**
   * The service received an unsupported hurdle result code. Some hurdle result codes are not meant to be sent to the
   * backend, e.g., FINISHED_OK and FINISHED_ERROR.
   *
   * @param id The id of the process instance.
   * @param resultCode The unsupported hurdle result code.
   */
  data class UnsupportedHurdleResultCode(val id: String, val resultCode: ResultCode) :
    DomainApiError(),
    WarnOnly

  /** Used to indicate that a process instance was canceled, e.g., when the client sends a canceled hurdle result. */
  data class ProcessWasCancelled(val id: String) :
    DomainApiError(),
    InfoOnly

  /**
   * The service received an invalid result for a requirement, for example, a hurdle result that is not expected or
   * required.
   *
   * @param id The id of the process instance.
   * @param requirement The requirement that the result is for.
   */
  data class InvalidRequirementResult(val id: String, val requirement: String) :
    DomainApiError(),
    WarnOnly

  /**
   * The service is already processing an identical request.
   *
   * @param id The id of the process instance.
   */
  data class AlreadyProcessing(val id: String) :
    DomainApiError(),
    InfoOnly
}

interface InfoOnly
interface WarnOnly
