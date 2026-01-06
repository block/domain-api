package xyz.block.domainapi.serialisation

import app.cash.quiver.extensions.mapFailure
import arrow.core.raise.result
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.StringFormat
import xyz.block.domainapi.Input
import xyz.block.domainapi.UserInteraction

/**
 * Verification result for serialisation testing.
 */
sealed class SerialisationVerificationResult {
  /**
   * All provided types were successfully serialised and deserialised.
   */
  data object Success : SerialisationVerificationResult()

  /**
   * One or more types failed serialisation verification.
   *
   * @param failures List of failure details.
   */
  data class Failure(val failures: List<SerialisationFailure>) : SerialisationVerificationResult()
}

/**
 * Details about a serialisation failure.
 *
 * @param type The type that failed serialisation.
 * @param instance The instance that was being serialised.
 * @param phase Whether the failure occurred during serialisation or deserialisation.
 * @param cause The exception that was thrown.
 */
data class SerialisationFailure(
  val type: String,
  val instance: Any,
  val phase: Phase,
  override val cause: Throwable
): Throwable() {
  enum class Phase { SERIALISATION, DESERIALISATION }
}

/**
 * Verifies that the provided [StringFormat] correctly handles polymorphic serialisation
 * for the given hurdles and hurdle responses.
 *
 * This function attempts to serialise each instance and deserialise it back,
 * verifying that the roundtrip works without errors. It does not verify equality of
 * the deserialised objects (as that depends on proper equals implementations).
 *
 * Example usage in tests:
 * ```kotlin
 * @Test
 * fun `verify serialisation support`() {
 *   val json = Json { serializersModule = pizzaDomainApi.serialisersModule }
 *   val result = verifySerialisationSupport(
 *     format = json,
 *     hurdles = listOf(SizeHurdle(), ToppingsHurdle, DeliverySpeedHurdle()),
 *     hurdleResponses = listOf(
 *       SizeHurdleResult(PizzaSize.LARGE, ResultCode.CLEARED),
 *       ToppingsHurdleResult(listOf("pepperoni"), ResultCode.CLEARED)
 *     )
 *   )
 *   result shouldBe SerialisationVerificationResult.Success
 * }
 * ```
 *
 * @param format The [StringFormat] (e.g., Json) configured with the domain API's serialisers module.
 * @param hurdles List of [UserInteraction.Hurdle] instances to test. Should include at least
 *   one instance of each hurdle type used by the domain API.
 * @param hurdleResponses List of [Input.HurdleResponse] instances to test. Should include at
 *   least one instance of each response type used by the domain API.
 * @param notifications List of [UserInteraction.Notification] instances to test. Defaults to
 *   empty if the domain API doesn't use notifications.
 * @return [SerialisationVerificationResult.Success] if all types serialise correctly, or
 *   [SerialisationVerificationResult.Failure] with details about what failed.
 */
fun verifySerialisationSupport(
  format: StringFormat,
  hurdles: List<UserInteraction.Hurdle<*>> = emptyList(),
  hurdleResponses: List<Input.HurdleResponse<*>> = emptyList(),
  notifications: List<UserInteraction.Notification<*>> = emptyList()
): SerialisationVerificationResult {
  val failures = mutableListOf<SerialisationFailure>()

  @Suppress("UNCHECKED_CAST")
  val hurdleSerialiser = PolymorphicSerializer(UserInteraction.Hurdle::class)
    as KSerializer<UserInteraction.Hurdle<*>>

  @Suppress("UNCHECKED_CAST")
  val responseSerialiser = PolymorphicSerializer(Input.HurdleResponse::class)
    as KSerializer<Input.HurdleResponse<*>>

  @Suppress("UNCHECKED_CAST")
  val notificationSerialiser = PolymorphicSerializer(UserInteraction.Notification::class)
    as KSerializer<UserInteraction.Notification<*>>

  hurdles.forEach { hurdle ->
    verifyRoundtrip(format, hurdle, hurdleSerialiser).onFailure { failures.add(it as SerialisationFailure) }
  }

  hurdleResponses.forEach { response ->
    verifyRoundtrip(format, response, responseSerialiser).onFailure { failures.add(it as SerialisationFailure) }
  }

  notifications.forEach { notification ->
    verifyRoundtrip(format, notification, notificationSerialiser).onFailure { failures.add(it as SerialisationFailure) }
  }

  return if (failures.isEmpty()) {
    SerialisationVerificationResult.Success
  } else {
    SerialisationVerificationResult.Failure(failures)
  }
}

private fun <T : Any> verifyRoundtrip(
  format: StringFormat,
  instance: T,
  serialiser: KSerializer<T>
): Result<Unit> = result {
  val typeName = instance::class.qualifiedName ?: instance::class.simpleName ?: "Unknown"

  val serialised = Result.runCatching {
    format.encodeToString(serialiser, instance)
  }.mapFailure {
    SerialisationFailure(
      type = typeName,
      instance = instance,
      phase = SerialisationFailure.Phase.SERIALISATION,
      cause = it
    )
  }.bind()

  Result.runCatching {
    format.decodeFromString(serialiser, serialised)
  }.mapFailure {
    SerialisationFailure(
      type = typeName,
      instance = instance,
      phase = SerialisationFailure.Phase.DESERIALISATION,
      cause = it
    )
  }.bind()
}
