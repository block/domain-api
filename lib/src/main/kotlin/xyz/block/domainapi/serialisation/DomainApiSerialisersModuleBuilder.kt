package xyz.block.domainapi.serialisation

import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import xyz.block.domainapi.Input
import xyz.block.domainapi.UserInteraction

/**
 * Builder DSL for creating a [SerializersModule] that registers all hurdle, notification, and
 * response subclasses for a domain API.
 *
 * Example usage:
 * ```kotlin
 * val module = domainApiSerialisersModule {
 *   hurdle(GetPizzaSizeHurdle::class, GetPizzaSizeHurdle.serializer())
 *   hurdle(GetPizzaToppingsHurdle::class, GetPizzaToppingsHurdle.serializer())
 *
 *   notification(OrderConfirmedNotification::class, OrderConfirmedNotification.serializer())
 *
 *   hurdleResponse(GetPizzaSizeHurdleResponse::class, GetPizzaSizeHurdleResponse.serializer())
 *   hurdleResponse(GetPizzaToppingsHurdleResponse::class, GetPizzaToppingsHurdleResponse.serializer())
 * }
 * ```
 *
 * @param REQUIREMENT_ID The type of requirement IDs used by the domain API.
 * @param block The builder block to configure the serialisers module.
 * @return A [SerializersModule] configured with all registered subclasses.
 */
inline fun <reified REQUIREMENT_ID : Any> domainApiSerialisersModule(
  block: DomainApiSerialisersModuleBuilder<REQUIREMENT_ID>.() -> Unit
): SerializersModule {
  val builder = DomainApiSerialisersModuleBuilder<REQUIREMENT_ID>()
  builder.block()
  return builder.build()
}

/**
 * Builder for configuring polymorphic serialisation of domain API types.
 *
 * @param REQUIREMENT_ID The type of requirement IDs used by the domain API.
 */
class DomainApiSerialisersModuleBuilder<REQUIREMENT_ID : Any> {
  private val hurdles =
    mutableListOf<Pair<KClass<out UserInteraction.Hurdle<REQUIREMENT_ID>>, KSerializer<out UserInteraction.Hurdle<REQUIREMENT_ID>>>>()
  private val notifications =
    mutableListOf<Pair<KClass<out UserInteraction.Notification<REQUIREMENT_ID>>, KSerializer<out UserInteraction.Notification<REQUIREMENT_ID>>>>()
  private val hurdleResponses =
    mutableListOf<Pair<KClass<out Input.HurdleResponse<REQUIREMENT_ID>>, KSerializer<out Input.HurdleResponse<REQUIREMENT_ID>>>>()

  /**
   * Registers a [UserInteraction.Hurdle] subclass for polymorphic serialisation.
   *
   * @param kClass The KClass of the hurdle subclass.
   * @param serialiser The serialiser for the hurdle subclass.
   */
  fun <T : UserInteraction.Hurdle<REQUIREMENT_ID>> hurdle(kClass: KClass<T>, serializer: KSerializer<T>) {
    hurdles.add(kClass to serializer)
  }

  /**
   * Registers a [UserInteraction.Notification] subclass for polymorphic serialisation.
   *
   * @param kClass The KClass of the notification subclass.
   * @param serialiser The serialiser for the notification subclass.
   */
  fun <T : UserInteraction.Notification<REQUIREMENT_ID>> notification(
    kClass: KClass<T>,
    serializer: KSerializer<T>
  ) {
    notifications.add(kClass to serializer)
  }

  /**
   * Registers an [Input.HurdleResponse] subclass for polymorphic serialisation.
   *
   * @param kClass The KClass of the hurdle response subclass.
   * @param serialiser The serialiser for the hurdle response subclass.
   */
  fun <T : Input.HurdleResponse<REQUIREMENT_ID>> hurdleResponse(
    kClass: KClass<T>,
    serializer: KSerializer<T>
  ) {
    hurdleResponses.add(kClass to serializer)
  }

  @PublishedApi
  internal fun build(): SerializersModule = SerializersModule {
    polymorphic(UserInteraction.Hurdle::class) {
      @Suppress("UNCHECKED_CAST")
      hurdles.forEach { (kClass, serializer) ->
        registerSubclass(
          kClass as KClass<UserInteraction.Hurdle<REQUIREMENT_ID>>,
          serializer as KSerializer<UserInteraction.Hurdle<REQUIREMENT_ID>>
        )
      }
    }

    polymorphic(UserInteraction.Notification::class) {
      @Suppress("UNCHECKED_CAST")
      notifications.forEach { (kClass, serializer) ->
        registerSubclass(
          kClass as KClass<UserInteraction.Notification<REQUIREMENT_ID>>,
          serializer as KSerializer<UserInteraction.Notification<REQUIREMENT_ID>>
        )
      }
    }

    polymorphic(Input.HurdleResponse::class) {
      @Suppress("UNCHECKED_CAST")
      hurdleResponses.forEach { (kClass, serializer) ->
        registerSubclass(
          kClass as KClass<Input.HurdleResponse<REQUIREMENT_ID>>,
          serializer as KSerializer<Input.HurdleResponse<REQUIREMENT_ID>>
        )
      }
    }
  }

  private fun <Base : Any, T : Base> PolymorphicModuleBuilder<Base>.registerSubclass(
    kClass: KClass<T>,
    serializer: KSerializer<T>
  ) {
    subclass(kClass, serializer)
  }
}
