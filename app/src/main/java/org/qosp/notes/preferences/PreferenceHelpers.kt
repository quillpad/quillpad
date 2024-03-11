package org.qosp.notes.preferences

interface HasNameResource {
    val nameResource: Int
}

interface HasSupportRequirement {
    fun isSupported() = true
}