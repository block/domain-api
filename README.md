# Domain APIs

This library provides a framework for implementing domain-driven APIs that separate business logic from presentation concerns. This separation makes the code easier to test and reuse across different presentation layers.

## Design

There are multiple approaches to designing domain APIs. Through practical experience, we've developed patterns that work well for complex business processes.

### Resource-based Model

A common approach to designing domain APIs revolves around the concept of *resources*. The main issue with this approach is that most real-world scenarios extend far beyond the complexity of the CRUD model typically used when modeling an API around single resources. Instead, most real-world scenarios represent business processes with complex logic. Implementing a fine-grained API around basic resources has several drawbacks, including having to move the business logic to the clients, which hinders reusability.

For example, what might appear as a simple resource operation (like a financial transaction) is actually launching a complex business process that involves multiple steps, validations, and state changes. Such processes often have their own state machines and cannot be driven through standard REST operations.

### Business Process Model

Domain APIs are designed with the assumption that they expose a complex business process rather than a collection of resources. Based on this, we defined an API with the following operations:

#### Create
Creates a new instance of the business process. This call typically returns a collection of _hurdles_ that indicate which requirements need to be fulfilled before being able to successfully execute the process.

#### Execute
Attempts to execute a business process instance. This endpoint is called with any responses to hurdles sent by the client, either from hurdles returned by the `create` operation or by previous calls to `execute`. If there are still more requirements that are not met, then the service returns additional hurdles until all requirements are fulfilled.

#### Resume
A business process may be interrupted if it needs to wait for information that is not immediately available. Once the information is available, this endpoint can be called to resume the business process execution. This endpoint is typically called by other systems.

#### Update
Certain attributes of a business process might be updatable once the process is running. The `update` endpoint can be used to update specific attributes of a business process while it is in progress.

#### Get
Returns information about an instance of a business process based on its id, including which attributes are updatable.

#### Search
Allows searching for business process instances based on different criteria.

## Interface Definition

The Kotlin interface of the API can be found in [this file](src/main/kotlin/xyz/block/domainapi/DomainApi.kt).

### Hurdles

Hurdles are an important concept in this API design because they are the primary mechanism used to inform the client of any requirements that are missing to be able to successfully execute a business process.

A hurdle should encapsulate all the information needed by the client to perform whatever action is required to overcome the hurdle. The client should send back a hurdle response to indicate if it was successful and include any data that might be required by the service.

Here's an example of a hurdle definition structure:

```kotlin
enum class RequirementId {
    AMOUNT_REQUIRED,
}

open class Hurdle<RequirementId>(
    val id: RequirementId
)

data class AmountHurdle(
    val currentBalance: Amount,
    val minimumAmount: Amount,
    val displayUnits: DisplayUnits,
) : Hurdle<RequirementId>(AMOUNT_REQUIRED)
```

Each hurdle includes details specific to that hurdle type. In this example, the hurdle contains information about balance constraints and display formatting requirements.

The corresponding hurdle response would look like this:

```kotlin
open class HurdleResponse<RequirementId>(
    val id: RequirementId,
    val resultCode: ResultCode
)

data class AmountHurdleResponse(
    val amount: Amount,
    val resultCode: ResultCode,
) : HurdleResponse<RequirementId>(AMOUNT_REQUIRED, resultCode)
```

### Errors

Errors should be returned as a `Result.failure`. Several generic error types are provided by the framework, but services can define their own errors if needed.

## Implementation

There is a simple [exemplar](../exemplar) app that shows how to implement this style of domain API.
