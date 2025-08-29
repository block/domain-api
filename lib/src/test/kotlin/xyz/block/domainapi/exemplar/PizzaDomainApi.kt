package xyz.block.domainapi.exemplar

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.left
import arrow.core.raise.result
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import xyz.block.domainapi.CompareOperator
import xyz.block.domainapi.CompareValue
import xyz.block.domainapi.DomainApi
import xyz.block.domainapi.DomainApi.Endpoint.EXECUTE
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.Input.HurdleResponse
import xyz.block.domainapi.Input.ResumeResult
import xyz.block.domainapi.ProcessInfo
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.SearchParameter
import xyz.block.domainapi.SearchResult
import xyz.block.domainapi.UpdateResponse
import xyz.block.domainapi.UserInteraction.Hurdle

/** A simple domain API for ordering pizza. */
class PizzaDomainApi : DomainApi<InitialRequest, String, RequirementId, AttributeId, PizzaOrder> {

  private val pizzaOrdersMap: MutableMap<String, PizzaOrder> = mutableMapOf()

  /** Creates a new pizza order. */
  override fun create(
    id: String,
    initialRequest: InitialRequest,
    hurdleGroupId: String?
  ): Result<ExecuteResponse<String, RequirementId>> {
    if (!pizzaOrdersMap.containsKey(id)) {
      val newPizzaOrder = PizzaOrder(id, PizzaOrderState.ORDERING)
      val hurdles: MutableList<Hurdle<RequirementId>> = mutableListOf()
      // The initial request might contain the size. If it doesn't then we add a hurdle to the response to request it.
      if (initialRequest.pizzaSize == null) {
        hurdles.add(SizeHurdle())
        pizzaOrdersMap[id] = newPizzaOrder
      } else {
        pizzaOrdersMap[id] = newPizzaOrder.copy(size = initialRequest.pizzaSize)
      }
      hurdles.add(ToppingsHurdle)
      hurdles.add(DeliverySpeedHurdle())
      return Result.success(ExecuteResponse(id, hurdles, EXECUTE))
    } else {
      return Result.failure(DomainApiError.ProcessAlreadyExists(id))
    }
  }

  /**
   * The execute endpoint is called with the results of any hurdles that were sent to the client in previous calls.
   * However, the implementation should support the following:
   * - hurdle results for hurdles that haven't been sent yet
   * - partial hurdle results - in this case any unresolved hurdles should be returned
   *
   * If hurdle results for hurdles that have already been resolved are sent then the implementation should return an
   * error.
   */
  override fun execute(
    id: String,
    hurdleResponses: List<HurdleResponse<RequirementId>>,
    hurdleGroupId: String?
  ): Result<ExecuteResponse<String, RequirementId>> = result { doExecute(id, hurdleResponses, false).bind() }

  private fun doExecute(
    id: String,
    hurdleResults: List<HurdleResponse<RequirementId>>,
    allowUpdate: Boolean,
  ): Result<ExecuteResponse<String, RequirementId>> = result {
    val pizzaOrder = pizzaOrdersMap[id].toOption().toEither { DomainApiError.ProcessNotFound(id) }.bind()
    hurdleResults
      .fold(Result.success(PizzaOrderAndHurdles(pizzaOrder, emptyList()))) {
          accumulator: Result<PizzaOrderAndHurdles>,
          hurdleResult ->
        val current = accumulator.bind()
        val newPizzaOrder = processHurdleResult(current.pizzaOrder, hurdleResult, allowUpdate).bind()
        newPizzaOrder.fold(
          { Result.success(PizzaOrderAndHurdles(current.pizzaOrder, current.hurdles + it)) },
          {
            // If the state is cancelled then there is no point in sending hurdles back
            if (it.state == PizzaOrderState.CANCELLED) {
              Result.success(PizzaOrderAndHurdles(it, emptyList()))
            } else {
              Result.success(PizzaOrderAndHurdles(it, getHurdles(it)))
            }
          },
        )
      }
      .map { PizzaOrderAndHurdles(maybeUpdateState(it.pizzaOrder), it.hurdles) }
      .map { ExecuteResponse(id = id, interactions = it.hurdles, nextEndpoint = EXECUTE) }
      .bind()
  }

  override fun resume(id: String, resumeResult: ResumeResult<RequirementId>): Result<Unit> = result {
    val pizzaOrder = pizzaOrdersMap[id].toOption().toEither { DomainApiError.ProcessNotFound(id) }.bind()
    when (resumeResult.id) {
      RequirementId.PIZZA_READY -> {
        if (pizzaOrder.state == PizzaOrderState.MAKING) {
          pizzaOrdersMap[id] = pizzaOrder.copy(state = PizzaOrderState.READY_FOR_DELIVERY)
        } else {
          raise(DomainApiError.CannotResumeProcess(id, "Pizza is ready but it is not being made"))
        }
      }
      RequirementId.PIZZA_OUT_FOR_DELIVERY -> {
        if (pizzaOrder.state == PizzaOrderState.READY_FOR_DELIVERY) {
          pizzaOrdersMap[id] = pizzaOrder.copy(state = PizzaOrderState.DELIVERY_IN_PROGRESS)
        } else {
          raise(DomainApiError.CannotResumeProcess(id, "Pizza is out for delivery but it wasn't ready"))
        }
      }
      RequirementId.PIZZA_DELIVERED -> {
        if (pizzaOrder.state == PizzaOrderState.DELIVERY_IN_PROGRESS) {
          pizzaOrdersMap[id] = pizzaOrder.copy(state = PizzaOrderState.DELIVERED)
        } else {
          raise(DomainApiError.CannotResumeProcess(id, "Pizza was delivered but it was not out for delivery"))
        }
      }
      else -> raise(DomainApiError.InvalidRequirementResult(id, resumeResult.id.toString()))
    }
  }

  /**
   * This endpoint will typically be called first with no hurdle results. Once all hurdles are overcome then the
   * attribute will be updated.
   */
  override fun update(
    id: String,
    attributeId: AttributeId,
    hurdleResponses: List<HurdleResponse<RequirementId>>,
  ): Result<UpdateResponse<RequirementId, AttributeId>> = result {
    if (hurdleResponses.isEmpty()) {
      // No hurdle results submitted - this is the first call
      when (attributeId) {
        AttributeId.DELIVERY_SPEED -> UpdateResponse(id, attributeId, listOf(DeliverySpeedHurdle()))
      }
    } else {
      when (attributeId) {
        AttributeId.DELIVERY_SPEED -> {
          val pizzaOrder = pizzaOrdersMap[id].toOption().toEither { DomainApiError.ProcessNotFound(id) }.bind()
          when (pizzaOrder.state) {
            PizzaOrderState.ORDERING,
            PizzaOrderState.MAKING,
            PizzaOrderState.READY_FOR_DELIVERY ->
              doExecute(id, hurdleResponses, true).map { UpdateResponse(id, attributeId, it.interactions) }.bind()
            else ->
              raise(
                DomainApiError.CannotUpdateProcess(
                  id,
                  "Delivery speed can only be updated before pizza is out for delivery",
                )
              )
          }
        }
      }
    }
  }

  override fun get(id: String): Result<ProcessInfo<AttributeId, PizzaOrder>> = result {
    val pizzaOrder = pizzaOrdersMap[id].toOption().toEither { DomainApiError.ProcessNotFound(id) }.bind()
    ProcessInfo(id, pizzaOrder, getUpdatableAttributes(pizzaOrder))
  }

  /** In this exemplar we only support searching by state using the EQUALS operator, with no support for pagination. */
  override fun search(parameter: SearchParameter, limit: Int): Result<SearchResult<AttributeId, PizzaOrder>> = result {
    if (parameter !is SearchParameter.ParameterExpression<*> || parameter.parameter != SearchParameterType.STATE) {
      raise(DomainApiError.InvalidSearchParameter(parameter))
    }

    val values = parameter.values
    when (parameter.compareOperator) {
      CompareOperator.EQUALS ->
        if (values.size != 1) {
          raise(DomainApiError.InvalidSearchParameter(parameter))
        } else {
          when (values[0]) {
            is CompareValue.StringValue -> {
              val value = (values[0] as CompareValue.StringValue).value
              Result.runCatching {
                val targetState = PizzaOrderState.valueOf(value)
                val results = pizzaOrdersMap.values.filter { it.state == targetState }
                SearchResult(
                  limit = limit,
                  thisStart = 0,
                  prevStart = null,
                  nextStart = null,
                  results = results.map { ProcessInfo(it.id, it, listOf(AttributeId.DELIVERY_SPEED)) }.take(limit),
                )
              }
                .recover {
                  SearchResult(limit = limit, thisStart = 0, prevStart = null, nextStart = null, results = emptyList())
                }
                .bind()
            }
            else -> raise(DomainApiError.InvalidSearchParameter(parameter))
          }
        }
      else -> raise(DomainApiError.InvalidSearchParameter(parameter))
    }
  }

  private fun processHurdleResult(
    pizzaOrder: PizzaOrder,
    hurdleResult: HurdleResponse<RequirementId>,
    allowUpdate: Boolean,
  ): Result<Either<Hurdle<RequirementId>, PizzaOrder>> = result {
    processHurdleResultCode(pizzaOrder.id, hurdleResult)
      .map {
        it.fold(
          {
            when (hurdleResult.id) {
              RequirementId.PIZZA_SIZE ->
                processPizzaSizeHurdleResult(pizzaOrder, hurdleResult as SizeHurdleResult, allowUpdate)
              RequirementId.PIZZA_TOPPINGS ->
                processPizzaToppingsHurdleResult(pizzaOrder, hurdleResult as ToppingsHurdleResult, allowUpdate)
              RequirementId.DELIVERY_SPEED ->
                processDeliverySpeedHurdleResult(pizzaOrder, hurdleResult as DeliverySpeedHurdleResult, allowUpdate)
              RequirementId.PIZZA_READY,
              RequirementId.PIZZA_OUT_FOR_DELIVERY,
              RequirementId.PIZZA_DELIVERED ->
                Result.failure(
                  IllegalStateException("Unexpected hurdle result in execute endpoint: ${hurdleResult.id}")
                )
            }.bind()
          },
          { e -> e.left() },
        )
      }
      .recoverCatching {
        when (it) {
          is DomainApiError.ProcessWasCancelled -> {
            val newPizzaOrder = pizzaOrder.copy(state = PizzaOrderState.CANCELLED)
            pizzaOrdersMap[pizzaOrder.id] = newPizzaOrder
            newPizzaOrder.right()
          }
          else -> throw it
        }
      }
      .bind()
  }

  private fun processPizzaSizeHurdleResult(
    pizzaOrder: PizzaOrder,
    hurdleResult: SizeHurdleResult,
    allowUpdate: Boolean,
  ): Result<Either<Hurdle<RequirementId>, PizzaOrder>> =
    if (pizzaOrder.size == null || allowUpdate) {
      Result.success(pizzaOrder.copy(size = hurdleResult.size).right())
    } else {
      Result.failure(DomainApiError.HurdleResultAlreadyProcessed(pizzaOrder.id, RequirementId.PIZZA_SIZE.toString()))
    }

  private fun processPizzaToppingsHurdleResult(
    pizzaOrder: PizzaOrder,
    hurdleResult: ToppingsHurdleResult,
    allowUpdate: Boolean,
  ): Result<Either<Hurdle<RequirementId>, PizzaOrder>> =
    if (pizzaOrder.toppings.isEmpty() || allowUpdate) {
      Result.success(pizzaOrder.copy(toppings = hurdleResult.toppings).right())
    } else {
      Result.failure(
        DomainApiError.HurdleResultAlreadyProcessed(pizzaOrder.id, RequirementId.PIZZA_TOPPINGS.toString())
      )
    }

  private fun processDeliverySpeedHurdleResult(
    pizzaOrder: PizzaOrder,
    hurdleResult: DeliverySpeedHurdleResult,
    allowUpdate: Boolean,
  ): Result<Either<Hurdle<RequirementId>, PizzaOrder>> =
    if (pizzaOrder.deliverySpeed == null || allowUpdate) {
      Result.success(pizzaOrder.copy(deliverySpeed = hurdleResult.speed).right())
    } else {
      Result.failure(
        DomainApiError.HurdleResultAlreadyProcessed(pizzaOrder.id, RequirementId.DELIVERY_SPEED.toString())
      )
    }

  private fun processHurdleResultCode(
    id: String,
    hurdleResult: HurdleResponse<RequirementId>,
  ): Result<Option<Hurdle<RequirementId>>> = result {
    when (hurdleResult.result) {
      ResultCode.CLEARED -> None
      ResultCode.FAILED -> getHurdleForRequirement(hurdleResult.id).bind().some()
      ResultCode.CANCELLED -> raise(DomainApiError.ProcessWasCancelled(id))
      ResultCode.SKIPPED -> raise(DomainApiError.SkippingRequirementUnsupported(id, hurdleResult.id.toString()))
      ResultCode.BACK -> raise(DomainApiError.GoingBackUnsupported(id, hurdleResult.id.toString()))
      ResultCode.FINISHED_OK,
      ResultCode.FINISHED_ERROR -> raise(DomainApiError.UnsupportedHurdleResultCode(id, hurdleResult.result))
    }
  }

  private fun getHurdleForRequirement(requirementId: RequirementId): Result<Hurdle<RequirementId>> =
    when (requirementId) {
      RequirementId.PIZZA_SIZE -> Result.success(SizeHurdle())
      RequirementId.PIZZA_TOPPINGS -> Result.success(ToppingsHurdle)
      RequirementId.DELIVERY_SPEED -> Result.success(DeliverySpeedHurdle())
      RequirementId.PIZZA_READY,
      RequirementId.PIZZA_OUT_FOR_DELIVERY,
      RequirementId.PIZZA_DELIVERED ->
        Result.failure(IllegalArgumentException("Requirement $requirementId does not have a corresponding hurdle"))
    }

  private fun getHurdles(pizzaOrder: PizzaOrder): List<Hurdle<RequirementId>> {
    val hurdles = mutableListOf<Hurdle<RequirementId>>()
    if (pizzaOrder.size == null) {
      hurdles.add(SizeHurdle())
    }
    if (pizzaOrder.toppings.isEmpty()) {
      hurdles.add(ToppingsHurdle)
    }
    if (pizzaOrder.deliverySpeed == null) {
      hurdles.add(DeliverySpeedHurdle())
    }
    return hurdles
  }

  private fun maybeUpdateState(pizzaOrder: PizzaOrder): PizzaOrder =
    if (pizzaOrder.size != null && pizzaOrder.toppings.isNotEmpty() && pizzaOrder.deliverySpeed != null) {
      val updatedPizzaOrder = pizzaOrder.copy(state = PizzaOrderState.MAKING)
      pizzaOrdersMap[pizzaOrder.id] = updatedPizzaOrder
      updatedPizzaOrder
    } else {
      pizzaOrder
    }

  private fun getUpdatableAttributes(pizzaOrder: PizzaOrder): List<AttributeId> =
    when (pizzaOrder.state) {
      PizzaOrderState.ORDERING,
      PizzaOrderState.MAKING,
      PizzaOrderState.READY_FOR_DELIVERY -> listOf(AttributeId.DELIVERY_SPEED)
      PizzaOrderState.CANCELLED,
      PizzaOrderState.DELIVERY_IN_PROGRESS,
      PizzaOrderState.DELIVERED -> emptyList()
    }
}

data class InitialRequest(val pizzaSize: PizzaSize?)

enum class PizzaSize {
  SMALL,
  MEDIUM,
  LARGE,
}

enum class RequirementId {
  PIZZA_SIZE,
  PIZZA_TOPPINGS,
  DELIVERY_SPEED,
  PIZZA_READY,
  PIZZA_OUT_FOR_DELIVERY,
  PIZZA_DELIVERED,
}

enum class AttributeId {
  DELIVERY_SPEED
}

data class PizzaOrder(
  val id: String,
  val state: PizzaOrderState,
  val size: PizzaSize? = null,
  val toppings: List<String> = listOf(),
  val deliverySpeed: DeliverySpeed? = null,
)

enum class PizzaOrderState {
  ORDERING,
  MAKING,
  READY_FOR_DELIVERY,
  DELIVERY_IN_PROGRESS,
  DELIVERED,
  CANCELLED,
}

enum class DeliverySpeed {
  STRAIGHT_TO_YOUR_DOOR,
  STANDARD,
}

/** A hurdle that requires the user to select a pizza size. Available pizza sizes are sent back to the caller. */
data class SizeHurdle(
  val availableSizes: List<PizzaSize> = listOf(PizzaSize.SMALL, PizzaSize.MEDIUM, PizzaSize.LARGE)
) : Hurdle<RequirementId>(RequirementId.PIZZA_SIZE)

/**
 * A hurdle that gives the user the option to select pizza toppings. Toppings are just strings so no need to send
 * anything back to the caller.
 */
data object ToppingsHurdle : Hurdle<RequirementId>(RequirementId.PIZZA_TOPPINGS)

/**
 * A hurdle that requires the user to select a delivery speed. Available delivery speeds are sent back to the caller.
 */
data class DeliverySpeedHurdle(
  val availableSpeeds: List<DeliverySpeed> = listOf(DeliverySpeed.STANDARD, DeliverySpeed.STRAIGHT_TO_YOUR_DOOR)
) : Hurdle<RequirementId>(RequirementId.DELIVERY_SPEED)

data class SizeHurdleResult(val size: PizzaSize, val code: ResultCode) :
  HurdleResponse<RequirementId>(RequirementId.PIZZA_SIZE, code)

data class ToppingsHurdleResult(val toppings: List<String>, val code: ResultCode) :
  HurdleResponse<RequirementId>(RequirementId.PIZZA_TOPPINGS, code)

data class DeliverySpeedHurdleResult(val speed: DeliverySpeed, val code: ResultCode) :
  HurdleResponse<RequirementId>(RequirementId.DELIVERY_SPEED, code)

enum class SearchParameterType {
  STATE
}

internal data class PizzaOrderAndHurdles(val pizzaOrder: PizzaOrder, val hurdles: List<Hurdle<RequirementId>>)
