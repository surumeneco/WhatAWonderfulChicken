rootProject.name = "WhatAWonderfulChicken"

val wglDirectory = file("../WonderfulGenomeLib")
check(wglDirectory.isDirectory) {
    "WonderfulGenomeLib must be checked out next to WhatAWonderfulChicken: ${wglDirectory.absolutePath}"
}

includeBuild(wglDirectory)

// WCL is a separate runtime plugin; composite build resolves compile-only API.
val wclDirectory = file("../WonderfulCoreLibrary")
check(wclDirectory.isDirectory) { "WonderfulCoreLibrary must be checked out alongside the consumer" }
includeBuild(wclDirectory)
