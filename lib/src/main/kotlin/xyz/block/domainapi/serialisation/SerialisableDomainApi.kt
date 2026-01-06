package xyz.block.domainapi.serialisation

import kotlinx.serialization.modules.SerializersModule

/**
 * Marker interface for domain APIs that support polymorphic serialisation of their
 * [xyz.block.domainapi.UserInteraction.Hurdle], [xyz.block.domainapi.UserInteraction.Notification],
 * and [xyz.block.domainapi.Input.HurdleResponse] subclasses.
 *
 * Implementing this interface signals that the domain API provides a [SerializersModule]
 * configured with all necessary polymorphic serialisers, enabling frameworks to serialise/deserialise
 * hurdles and responses for state persistence, navigation restore, etc.
 *
 * Example implementation:
 * ```kotlin
 * class PizzaDomainApi : DomainApi<...>, SerialisableDomainApi {
 *   override val serialisersModule: SerializersModule = domainApiSerialisersModule<RequirementId> {
 *     hurdle(SizeHurdle::class, SizeHurdle.serializer())
 *     hurdle(ToppingsHurdle::class, ToppingsHurdle.serializer())
 *
 *     hurdleResponse(SizeHurdleResult::class, SizeHurdleResult.serializer())
 *     hurdleResponse(ToppingsHurdleResult::class, ToppingsHurdleResult.serializer())
 *   }
 *   // ... rest of implementation
 * }
 * ```
 *
 * Use [xyz.block.domainapi.testing.verifySerialisationSupport] in tests to validate that all hurdle and response types
 * can be correctly serialised and deserialised.
 */
interface SerialisableDomainApi {
  /**
   * The [SerializersModule] containing polymorphic serialiser registrations for all
   * hurdle, notification, and hurdle response types used by this domain API.
   *
   * This module should be used when configuring a Json (or other format) instance:
   * ```kotlin
   * val json = Json {
   *   serializersModule = myDomainApi.serialisersModule
   * }
   * ```
   */
  val serialisersModule: SerializersModule
}
