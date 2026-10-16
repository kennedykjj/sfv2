package sfv2.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom

/** Daemon-generated proof that a request really comes from the owner. */
class OwnerProof private constructor() {

    fun matches(candidate: String?): Boolean = false

    companion object {
        fun create(path: Path, random: SecureRandom): OwnerProof {
            Files.writeString(path, "stub")
            return OwnerProof()
        }
    }
}
