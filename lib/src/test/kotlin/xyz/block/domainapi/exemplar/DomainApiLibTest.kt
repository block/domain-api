package xyz.block.domainapi.exemplar

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import xyz.block.domainapi.CompareOperator
import xyz.block.domainapi.CompareValue
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.Input.ResumeResult
import xyz.block.domainapi.ProcessInfo
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.SearchParameter

class DomainApiLibTest {

  @Test
  fun `happy path`() {
    val api = PizzaDomainApi()
    val orderId = "pizzaOrder1"
    val initialResponse = api.create(orderId, InitialRequest(pizzaSize = PizzaSize.LARGE))

    initialResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    val executeResponse =
      api.execute(
        orderId,
        listOf(
          ToppingsHurdleResult(toppings = listOf("pepperoni", "mushrooms"),
            code = ResultCode.CLEARED),
          DeliverySpeedHurdleResult(speed = DeliverySpeed.STANDARD,
            code = ResultCode.CLEARED),
        ),
      )
    executeResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions shouldBe emptyList()
    }

    // Test current state is correct
    api.get(orderId) shouldBeSuccess {
      it.shouldBeInstanceOf<ProcessInfo<AttributeId, PizzaOrder>>()
      it.process.state shouldBe PizzaOrderState.MAKING
    }

    // Update delivery speed
    api.update(
      orderId,
      AttributeId.DELIVERY_SPEED,
      listOf(
        DeliverySpeedHurdleResult(DeliverySpeed.STRAIGHT_TO_YOUR_DOOR, ResultCode.CLEARED)),
    ).getOrThrow()

    // Test new speed is correct
    api.get(orderId) shouldBeSuccess {
      it.shouldBeInstanceOf<ProcessInfo<AttributeId, PizzaOrder>>()
      it.process.deliverySpeed shouldBe DeliverySpeed.STRAIGHT_TO_YOUR_DOOR
    }

    // Call resume when pizza is ready and check state is correct
    api.resume(orderId, ResumeResult(RequirementId.PIZZA_READY)).getOrThrow()
    api.get(orderId) shouldBeSuccess {
      it.shouldBeInstanceOf<ProcessInfo<AttributeId, PizzaOrder>>()
      it.process.state shouldBe PizzaOrderState.READY_FOR_DELIVERY
    }

    api.resume(orderId, ResumeResult(RequirementId.PIZZA_OUT_FOR_DELIVERY)).getOrThrow()
    api.get(orderId) shouldBeSuccess {
      it.shouldBeInstanceOf<ProcessInfo<AttributeId, PizzaOrder>>()
      it.process.state shouldBe PizzaOrderState.DELIVERY_IN_PROGRESS
    }

    api.resume(orderId, ResumeResult(RequirementId.PIZZA_DELIVERED)).getOrThrow()
    api.get(orderId) shouldBeSuccess {
      it.shouldBeInstanceOf<ProcessInfo<AttributeId, PizzaOrder>>()
      it.process.state shouldBe PizzaOrderState.DELIVERED
    }
  }

  @Test
  fun `searching via state returns all matching pizza orders`() {
    val api = PizzaDomainApi()
    val orderId = "pizzaOrder1"
    api.create("pizzaOrder1", InitialRequest(pizzaSize = PizzaSize.LARGE)) shouldBeSuccess {
      it.id shouldBe "pizzaOrder1"
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    api.execute(
      orderId,
      listOf(
        ToppingsHurdleResult(toppings = listOf("pepperoni", "mushrooms"),
          code = ResultCode.CLEARED),
        DeliverySpeedHurdleResult(speed = DeliverySpeed.STANDARD,
          code = ResultCode.CLEARED),
      ),
    ) shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions shouldBe emptyList()
    }

    api.create("pizzaOrder2", InitialRequest(null)) shouldBeSuccess {
      it.id shouldBe "pizzaOrder2"
      it.interactions.shouldContain(SizeHurdle())
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    api.create("pizzaOrder3", InitialRequest(null)) shouldBeSuccess {
      it.id shouldBe "pizzaOrder3"
      it.interactions.shouldContain(SizeHurdle())
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    api
      .search(
        parameter =
          SearchParameter.ParameterExpression(
            SearchParameterType.STATE,
            CompareOperator.EQUALS,
            CompareValue.StringValue("ORDERING"),
          ),
        limit = 5,
      )
      .shouldBeSuccess { it.results.size shouldBe 2 }

    api
      .search(
        parameter =
          SearchParameter.ParameterExpression(
            SearchParameterType.STATE,
            CompareOperator.EQUALS,
            CompareValue.StringValue("MAKING"),
          ),
        limit = 5,
      )
      .shouldBeSuccess { it.results.size shouldBe 1 }

    api
      .search(
        parameter =
          SearchParameter.ParameterExpression(
            SearchParameterType.STATE,
            CompareOperator.EQUALS,
            CompareValue.StringValue("DELIVERY_IN_PROGRESS"),
          ),
        limit = 5,
      )
      .shouldBeSuccess { it.results.size shouldBe 0 }

    api
      .search(
        parameter =
          SearchParameter.ParameterExpression(
            SearchParameterType.STATE,
            CompareOperator.EQUALS,
            CompareValue.StringValue(
              "DISPATCHED"
            ), // DISPATCHED is not a valid state for pizza orders so no results will be returned
          ),
        limit = 5,
      )
      .shouldBeSuccess { it.results.size shouldBe 0 }
  }

  @Test
  fun `attempting to create an existing pizza order fails`() {
    val api = PizzaDomainApi()
    val orderId = "pizzaOrder1"
    val initialResponse = api.create(orderId, InitialRequest(pizzaSize = PizzaSize.LARGE))

    initialResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    api.create(orderId, InitialRequest(pizzaSize = null)) shouldBeFailure
      {
        it.shouldBeInstanceOf<DomainApiError.ProcessAlreadyExists>()
      }
  }

  @Test
  fun `sending partial hurdle results produces a response with the missing hurdles`() {
    val api = PizzaDomainApi()
    val orderId = "pizzaOrder1"
    val initialResponse = api.create(orderId, InitialRequest(pizzaSize = PizzaSize.LARGE))

    initialResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    val executeResponse =
      api.execute(
        orderId,
        listOf(
          DeliverySpeedHurdleResult(speed = DeliverySpeed.STRAIGHT_TO_YOUR_DOOR,
            code = ResultCode.CLEARED)
        ),
      )
    executeResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions.shouldContain(ToppingsHurdle)
    }
  }

  @Test
  fun `sending hurdle results that have already been resolved results in an error`() {
    val api = PizzaDomainApi()
    val orderId = "pizzaOrder1"
    val initialResponse = api.create(orderId, InitialRequest(pizzaSize = PizzaSize.LARGE))

    initialResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    val executeResponse =
      api.execute(
        orderId,
        listOf(
          ToppingsHurdleResult(toppings = listOf("pepperoni", "mushrooms"),
            code = ResultCode.CLEARED),
          DeliverySpeedHurdleResult(speed = DeliverySpeed.STRAIGHT_TO_YOUR_DOOR,
            code = ResultCode.CLEARED),
        ),
      )
    executeResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions shouldBe emptyList()
    }

    api.execute(
      orderId,
      listOf(DeliverySpeedHurdleResult(speed = DeliverySpeed.STRAIGHT_TO_YOUR_DOOR,
        code = ResultCode.CLEARED)),
    ) shouldBeFailure { it.shouldBeInstanceOf<DomainApiError.HurdleResultAlreadyProcessed>() }
  }

  @Test
  fun `resuming a process in the wrong state results in an error`() {
    val api = PizzaDomainApi()
    val orderId = "pizzaOrder1"
    val initialResponse = api.create(orderId, InitialRequest(pizzaSize = PizzaSize.LARGE))

    initialResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    val executeResponse =
      api.execute(
        orderId,
        listOf(
          ToppingsHurdleResult(toppings = listOf("pepperoni", "mushrooms"),
            code = ResultCode.CLEARED),
          DeliverySpeedHurdleResult(speed = DeliverySpeed.STRAIGHT_TO_YOUR_DOOR,
            code = ResultCode.CLEARED),
        ),
      )
    executeResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions shouldBe emptyList()
    }

    // Pizza is making now, so it can't go straight to delivery
    api.resume(orderId, ResumeResult(RequirementId.PIZZA_OUT_FOR_DELIVERY)) shouldBeFailure
      {
        it.shouldBeInstanceOf<DomainApiError.InvalidProcessState>()
      }
  }

  @Test
  fun `attempting to update an attribute in an invalid state results in an error`() {
    val api = PizzaDomainApi()
    val orderId = "pizzaOrder1"
    val initialResponse = api.create(orderId, InitialRequest(pizzaSize = PizzaSize.LARGE))

    initialResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    val executeResponse =
      api.execute(
        orderId,
        listOf(
          ToppingsHurdleResult(toppings = listOf("pepperoni", "mushrooms"),
            code = ResultCode.CLEARED),
          DeliverySpeedHurdleResult(speed = DeliverySpeed.STANDARD,
            code = ResultCode.CLEARED),
        ),
      )
    executeResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions shouldBe emptyList()
    }

    // Test current state is correct
    api.get(orderId) shouldBeSuccess {
      it.shouldBeInstanceOf<ProcessInfo<AttributeId, PizzaOrder>>()
      it.process.state shouldBe PizzaOrderState.MAKING
    }

    // Call resume when pizza is ready and check state is correct
    api.resume(orderId, ResumeResult(RequirementId.PIZZA_READY)).getOrThrow()
    api.get(orderId) shouldBeSuccess {
      it.shouldBeInstanceOf<ProcessInfo<AttributeId, PizzaOrder>>()
      it.process.state shouldBe PizzaOrderState.READY_FOR_DELIVERY
    }

    api.resume(orderId, ResumeResult(RequirementId.PIZZA_OUT_FOR_DELIVERY)).getOrThrow()
    api.get(orderId) shouldBeSuccess {
      it.shouldBeInstanceOf<ProcessInfo<AttributeId, PizzaOrder>>()
      it.process.state shouldBe PizzaOrderState.DELIVERY_IN_PROGRESS
    }

    // Cannot update delivery speed once the pizza is out for delivery
    api.update(
      orderId,
      AttributeId.DELIVERY_SPEED,
      listOf(
        DeliverySpeedHurdleResult(DeliverySpeed.STRAIGHT_TO_YOUR_DOOR, ResultCode.CLEARED)),
    ) shouldBeFailure { it.shouldBeInstanceOf<DomainApiError.CannotUpdateProcess>() }
  }

  @Test
  fun `retrieving a process that doesn't exist results in an error`() {
    val api = PizzaDomainApi()
    api.get("mypizza") shouldBeFailure { it.shouldBeInstanceOf<DomainApiError.ProcessNotFound>() }
  }

  @Test
  fun `a search request with unsupported parameters results in an error`() {
    val api = PizzaDomainApi()
    api
      .search(
        parameter =
          SearchParameter.ParameterExpression(
            SearchParameterType.STATE,
            CompareOperator.IN,
            CompareValue.StringValue("MAKING"),
            CompareValue.StringValue("DELIVERY_IN_PROGRESS"),
          ),
        limit = 5,
      )
      .shouldBeFailure<DomainApiError.InvalidSearchParameter>()
  }

  @Test
  fun `any cancelled hurdle result cancels the order`() {
    val api = PizzaDomainApi()
    val orderId = "pizzaOrder1"
    val initialResponse = api.create(orderId, InitialRequest(pizzaSize = PizzaSize.LARGE))

    initialResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions.shouldContain(ToppingsHurdle)
      it.interactions.shouldContain(DeliverySpeedHurdle())
    }

    val executeResponse =
      api.execute(
        orderId,
        listOf(
          ToppingsHurdleResult(toppings = listOf("pepperoni", "mushrooms"),
            code = ResultCode.CLEARED),
          DeliverySpeedHurdleResult(speed = DeliverySpeed.STANDARD,
            code = ResultCode.CANCELLED),
        ),
      )
    executeResponse shouldBeSuccess {
      it.id shouldBe orderId
      it.interactions shouldBe emptyList()
    }

    // Test current state is correct
    api.get(orderId) shouldBeSuccess {
      it.shouldBeInstanceOf<ProcessInfo<AttributeId, PizzaOrder>>()
      it.process.state shouldBe PizzaOrderState.CANCELLED
    }
  }
}
