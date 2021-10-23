package se.wingez.emulator

import java.lang.Exception

open class EmulatorRuntimeError(message: String) : Exception(message)
class EmulatorCyclesExceeded(message: String) : EmulatorRuntimeError(message)
class EmulatorInstructionError(message: String) : EmulatorRuntimeError(message)

class Emulator {
}