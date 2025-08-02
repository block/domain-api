package xyz.block.domainapi.util

import app.cash.kfsm.State
import app.cash.kfsm.States
import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.Value
import app.cash.kfsm.guice.annotations.TransitionDefinition
import app.cash.kfsm.guice.annotations.TransitionerDefinition
import xyz.block.domainapi.Input
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction

@TransitionDefinition
class IsProcessing : Transition<String, TestValue, TestState>(States(TestState.Initial), TestState.Processing)

@TransitionDefinition
class IsComplete : Transition<String, TestValue, TestState>(States(TestState.Processing), TestState.Complete)

@TransitionDefinition
class IsFinal : Transition<String, TestValue, TestState>(States(TestState.Complete), TestState.Final)

@TransitionerDefinition
class TestTransitioner : Transitioner<String, Transition<String, TestValue, TestState>, TestValue, TestState>()

sealed class TestState(to: () -> Set<TestState>) : State<String, TestValue, TestState>(to) {
  data object Initial : TestState({ setOf(Processing) })

  data object Processing : TestState({ setOf(Complete) })

  data object Complete : TestState({ setOf(Final) })

  data object Final : TestState({ emptySet() })
}

data class TestValue(override val id: String, override val state: TestState, val data: String = "") :
  Value<String, TestValue, TestState> {
  override fun update(newState: TestState): TestValue = copy(state = newState)
}

enum class TestRequirement {
  REQ1,
  REQ2,
}

data class TestHurdle(
  private val requirement: TestRequirement
) : UserInteraction.Hurdle<TestRequirement>(requirement) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TestHurdle) return false
    return requirement == other.requirement
  }

  override fun hashCode(): Int {
    return requirement.hashCode()
  }
}

class TestRequirementResult(id: TestRequirement, resultCode: ResultCode) :
  Input.HurdleResponse<TestRequirement>(id, resultCode)
