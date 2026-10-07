package com.shilapi.xcertplay.network

import android.util.AtomicFile
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Android's rename replaces an existing file; Windows File.renameTo does not.
 * Keep AtomicFile's real journal, fsync and readback paths, adapting only that OS primitive.
 */
@Implements(AtomicFile::class)
class PosixAtomicFileShadow {
    companion object {
        @JvmStatic
        @Implementation
        fun rename(source: File, target: File) {
            if (target.isDirectory) Files.delete(target.toPath())
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
