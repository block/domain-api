package xyz.block.domainapi.serialisation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import xyz.block.domainapi.Input.HurdleResponse
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction.Hurdle
import xyz.block.domainapi.exemplar.DeliverySpeed
import xyz.block.domainapi.exemplar.DeliverySpeedHurdle
import xyz.block.domainapi.exemplar.DeliverySpeedHurdleResult
import xyz.block.domainapi.exemplar.PizzaDomainApi
import xyz.block.domainapi.exemplar.PizzaSize
import xyz.block.domainapi.exemplar.RequirementId
import xyz.block.domainapi.exemplar.SizeHurdle
import xyz.block.domainapi.exemplar.SizeHurdleResult
import xyz.block.domainapi.exemplar.ToppingsHurdle
import xyz.block.domainapi.exemplar.ToppingsHurdleResult

class DomainApiSerialisersModuleBuilderTest : FunSpec({

  val pizzaDomainApi = PizzaDomainApi()

  val json = Json {
    serializersModule = pizzaDomainApi.serialisersModule
  }

  val hurdleSerialiser = PolymorphicSerializer(Hurdle::class)
  val hurdleResponseSerialiser = PolymorphicSerializer(HurdleResponse::class)

  context("Hurdle serialisation") {
    test("should serialise and deserialise SizeHurdle") {
      val hurdle: Hurdle<RequirementId> = SizeHurdle()

      val serialised = json.encodeToString(hurdleSerialiser, hurdle)
      serialised shouldContain """"type":"pizza.size""""

      val deserialised = json.decodeFromString(hurdleSerialiser, serialised)
      deserialised.shouldBeInstanceOf<SizeHurdle>()
      deserialised.availableSizes shouldBe listOf(PizzaSize.SMALL, PizzaSize.MEDIUM, PizzaSize.LARGE)
    }

    test("should serialise and deserialise ToppingsHurdle") {
      val hurdle: Hurdle<RequirementId> = ToppingsHurdle

      val serialised = json.encodeToString(hurdleSerialiser, hurdle)
      serialised shouldContain """"type":"pizza.toppings""""

      val deserialised = json.decodeFromString(hurdleSerialiser, serialised)
      deserialised.shouldBeInstanceOf<ToppingsHurdle>()
    }

    test("should serialise and deserialise DeliverySpeedHurdle") {
      val hurdle: Hurdle<RequirementId> = DeliverySpeedHurdle()

      val serialised = json.encodeToString(hurdleSerialiser, hurdle)
      serialised shouldContain """"type":"pizza.delivery_speed""""

      val deserialised = json.decodeFromString(hurdleSerialiser, serialised)
      deserialised.shouldBeInstanceOf<DeliverySpeedHurdle>()
      deserialised.availableSpeeds shouldBe listOf(DeliverySpeed.STANDARD, DeliverySpeed.STRAIGHT_TO_YOUR_DOOR)
    }
  }

  context("HurdleResponse serialisation") {
    test("should serialise and deserialise SizeHurdleResult") {
      val response: HurdleResponse<RequirementId> = SizeHurdleResult(PizzaSize.LARGE, ResultCode.CLEARED)

      val serialised = json.encodeToString(hurdleResponseSerialiser, response)
      serialised shouldContain """"type":"pizza.size_response""""
      serialised shouldContain """"size":"LARGE""""

      val deserialised = json.decodeFromString(hurdleResponseSerialiser, serialised)
      deserialised.shouldBeInstanceOf<SizeHurdleResult>()
      deserialised.size shouldBe PizzaSize.LARGE
      deserialised.code shouldBe ResultCode.CLEARED
    }

    test("should serialise and deserialise ToppingsHurdleResult") {
      val response: HurdleResponse<RequirementId> = ToppingsHurdleResult(
        listOf("pepperoni", "mushrooms"),
        ResultCode.CLEARED
      )

      val serialised = json.encodeToString(hurdleResponseSerialiser, response)
      serialised shouldContain """"type":"pizza.toppings_response""""
      serialised shouldContain "\"toppings\":[\"pepperoni\",\"mushrooms\"]"

      val deserialised = json.decodeFromString(hurdleResponseSerialiser, serialised)
      deserialised.shouldBeInstanceOf<ToppingsHurdleResult>()
      deserialised.toppings shouldBe listOf("pepperoni", "mushrooms")
    }

    test("should serialise and deserialise DeliverySpeedHurdleResult") {
      val response: HurdleResponse<RequirementId> = DeliverySpeedHurdleResult(
        DeliverySpeed.STRAIGHT_TO_YOUR_DOOR,
        ResultCode.CLEARED
      )

      val serialised = json.encodeToString(hurdleResponseSerialiser, response)
      serialised shouldContain """"type":"pizza.delivery_speed_response""""
      serialised shouldContain """"speed":"STRAIGHT_TO_YOUR_DOOR""""

      val deserialised = json.decodeFromString(hurdleResponseSerialiser, serialised)
      deserialised.shouldBeInstanceOf<DeliverySpeedHurdleResult>()
      deserialised.speed shouldBe DeliverySpeed.STRAIGHT_TO_YOUR_DOOR
    }
  }

  context("List serialisation") {
    test("should serialise and deserialise a list of hurdles") {
      val hurdles: List<Hurdle<RequirementId>> = listOf(
        SizeHurdle(),
        ToppingsHurdle,
        DeliverySpeedHurdle()
      )

      val listSerialiser = ListSerializer(hurdleSerialiser)
      val serialised = json.encodeToString(listSerialiser, hurdles)
      val deserialised = json.decodeFromString(listSerialiser, serialised)

      deserialised.size shouldBe 3
      deserialised[0].shouldBeInstanceOf<SizeHurdle>()
      deserialised[1].shouldBeInstanceOf<ToppingsHurdle>()
      deserialised[2].shouldBeInstanceOf<DeliverySpeedHurdle>()
    }
  }

  context("Serialisation verification utility") {
    test("verifySerialisationSupport should pass for correctly configured module") {
      val result = verifySerialisationSupport(
        format = json,
        hurdles = listOf(
          SizeHurdle(),
          ToppingsHurdle,
          DeliverySpeedHurdle()
        ),
        hurdleResponses = listOf(
          SizeHurdleResult(PizzaSize.LARGE, ResultCode.CLEARED),
          ToppingsHurdleResult(listOf("pepperoni"), ResultCode.CLEARED),
          DeliverySpeedHurdleResult(DeliverySpeed.STANDARD, ResultCode.CLEARED)
        )
      )

      result shouldBe SerialisationVerificationResult.Success
    }

    test("verifySerialisationSupport should fail for unregistered types") {
      val emptyModule = domainApiSerialisersModule<RequirementId> {
        // Intentionally empty - no types registered
      }
      val emptyJson = Json { serializersModule = emptyModule }

      val result = verifySerialisationSupport(
        format = emptyJson,
        hurdles = listOf(SizeHurdle())
      )

      result.shouldBeInstanceOf<SerialisationVerificationResult.Failure>()
      result.failures.size shouldBe 1
      result.failures[0].phase shouldBe SerialisationFailure.Phase.SERIALISATION
    }
  }
})
