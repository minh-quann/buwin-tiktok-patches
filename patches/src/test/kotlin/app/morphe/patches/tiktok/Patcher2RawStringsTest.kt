package app.morphe.patches.tiktok

import app.morphe.Fixtures
import app.morphe.patcher.Fingerprint
import app.morphe.takes
import org.junit.Assert.assertEquals
import org.junit.Test

/** These anchors must keep resolving when another bundle selects patcher 2.0 string rules. */
class Patcher2RawStringsTest {
    @Test
    fun `raw string anchors resolve under exact comparison on the declared APK`() {
        val expected = mapOf(
            "captchapopup.CaptchaPopupFingerprint" to 1,
            "captchapopup.LegacyCaptchaPopupFingerprint" to 1,
            "interaction.antirecording.CircleSearchBlockFingerprint" to 1,
            "interaction.looping.Availability" to 1,
            "interaction.searchfeatures.NonPersonalizedSearchGateFingerprint" to 1,
            "misc.commenttools.CommentMenuItemsFingerprint" to 1,
        ).mapKeys { (name, _) ->
            val owner = Class.forName("app.morphe.patches.tiktok.$name")
            val instance = owner.getDeclaredField("INSTANCE").also { it.isAccessible = true }
            instance.get(null) as Fingerprint
        }
        Fixtures.forEachDeclared { apk ->
            val container = Fixtures.dexContainer(apk)
            val found = expected.keys.associateWith { 0 }.toMutableMap()
            for (entry in container.dexEntryNames) {
                for (owner in container.getEntry(entry)!!.dexFile.classes) {
                    for (method in owner.methods) {
                        for (fingerprint in expected.keys) {
                            if (fingerprint.takes(method, owner, exactStrings = true)) {
                                found[fingerprint] = found.getValue(fingerprint) + 1
                            }
                        }
                    }
                }
            }
            for ((fingerprint, count) in expected) {
                assertEquals("${apk.name}: $fingerprint under patcher 2.0 rules", count,
                    found.getValue(fingerprint))
            }
        }
    }
}
