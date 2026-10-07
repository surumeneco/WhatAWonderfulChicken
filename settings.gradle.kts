rootProject.name = "WhatAWonderfulChicken"

val wglDirectory = file("../WonderfulGenomeLib")
check(wglDirectory.isDirectory) {
    "WonderfulGenomeLib must be checked out next to WhatAWonderfulChicken: ${wglDirectory.absolutePath}"
}

includeBuild(wglDirectory)
