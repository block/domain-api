package xyz.block.domainapi.util

import app.cash.kfsm.State
import app.cash.kfsm.Value
import app.cash.kfsm.fsm
import xyz.block.domainapi.Input
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction

sealed class TestState(
  to: () -> Set<TestState>
) : State<String, TestValue, TestState>(to)

data object Initial : TestState({ setOf(Processing) })

data object Processing : TestState({ setOf(Complete) })

data object Complete : TestState({ setOf(Final) })

data object Final : TestState({ emptySet() })

data class TestValue(
  override val id: String,
  override val state: TestState,
  val data: String = ""
) : Value<String, TestValue, TestState> {
  override fun update(newState: TestState): TestValue = copy(state = newState)
}

val stateMachine =
  fsm<String, TestValue, TestState> {
    Initial becomes {
      Processing via { it }
    }
    Processing becomes {
      Complete via { it }
    }
    Complete becomes {
      Final via { it }
    }
  }.getOrThrow()

enum class TestRequirement {
  REQ1,
  REQ2
}

data class TestHurdle(
  private val requirement: TestRequirement
) : UserInteraction.Hurdle<TestRequirement>(requirement) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TestHurdle) return false
    return requirement == other.requirement
  }

  override fun hashCode(): Int = requirement.hashCode()
}

class TestRequirementResult(
  id: TestRequirement,
  resultCode: ResultCode
) : Input.HurdleResponse<TestRequirement>(id, resultCode)
