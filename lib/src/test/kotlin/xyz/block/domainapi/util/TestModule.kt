package xyz.block.domainapi.util

import app.cash.kfsm.guice.KfsmModule

class TestModule :
  KfsmModule<String, TestValue, TestState>(
    types = typeLiteralsFor(String::class.java, TestValue::class.java, TestState::class.java)
  )
