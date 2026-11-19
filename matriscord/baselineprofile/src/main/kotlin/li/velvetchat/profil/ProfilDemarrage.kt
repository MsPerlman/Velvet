package li.velvetchat.profil

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * the journey the startup profile is recorded over
 *
 * ## what it covers, and what it does not
 *
 * the bench installs a fresh copy of velvet, with no session, so there is no conversation to open,
 * and the journey stops at the login screen. that is not a limit we suffer, it is the part that
 * counts: what costs on the first launch is loading the classes, booting compose, the theme and the
 * font, everything happening before a single screen shows. the timeline arrives when the app is
 * already warm
 *
 * we could cover more by logging a test account in from the test. that would be a username and a
 * password in a file in the repository, to gain on a screen we never reach cold any more: the deal
 * is not good
 *
 * ## why not `startActivityAndWait`
 *
 * it is the call intended for this, and it fails on this device: it confirms the launch by reading
 * `dumpsys gfxinfo … framestats`, which one ui does not fill for the first frame ("Unable to
 * confirm activity launch completion", measured on the s21). so we launch the activity ourselves
 * and wait until it is on screen, which records exactly the same interval without depending on a
 * counter the manufacturer does not keep
 */
@RunWith(AndroidJUnit4::class)
class ProfilDemarrage {

    @get:Rule
    val regle = BaselineProfileRule()

    @Test
    fun demarrage() = regle.collect(packageName = PAQUET) {
        pressHome()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(PAQUET, "li.velvetchat.app.VelvetActivity")
                // `CLEAR_TASK`: each iteration starts from a cold start, otherwise the second would
                // record the return to an activity already in memory
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        device.wait(Until.hasObject(By.pkg(PAQUET).depth(0)), ATTENTE_MS)
        // and until nothing moves any more: the login screen settles in two stages, the theme then
        // the font, and stopping at the first would leave half the profile out
        device.waitForIdle(ATTENTE_MS)
    }

    private companion object {
        /**
         * no suffix: the bench measures the unminified release variant, which carries the
         * production id. the `.debug` of the daily builds does not concern it
         */
        // The `applicationId`, which is deliberately not the Kotlin namespace — see
        // `app-android/build.gradle.kts`.
        //
        // It went stale twice without anyone noticing, and that is the point worth keeping:
        // this constant names an app to drive, so a wrong value does not fail to compile, it
        // drives nothing. The run still ends, the profile still writes, and it is empty. It
        // sat on a package that had not existed since 2026-09-25 until the 09-30 rename swept
        // past it. Anything measured in between measured nothing.
        const val PAQUET = "li.velvetchat"
        const val ATTENTE_MS = 20_000L
    }
}
