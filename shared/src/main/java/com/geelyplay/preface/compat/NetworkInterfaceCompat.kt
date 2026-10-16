package com.geelyplay.preface.compat

import java.net.NetworkInterface

/** NetworkInterface.getIndex was added in API 19. */
val NetworkInterface.indexCompat: Int
    get() = index
