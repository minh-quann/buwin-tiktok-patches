package app.morphe.extension.tiktok.settings.preference;

import android.preference.PreferenceActivity;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.settings.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30,35}, manifest = Config.NONE)
@SuppressWarnings("deprecation")
public class SeekBarPersistenceTest {
    public static class Screen extends PreferenceActivity { }
    @Test public void saveZeroOverExistingStringAndReopenThenSaveNonzero() {
        try (var controller = Robolectric.buildActivity(Screen.class).setup()) {
            var activity = controller.get(); Utils.setContext(activity);
            var setting = Settings.BURN_IN_IDLE_LEVEL;
            setting.save(25);
            var manager = activity.getPreferenceManager();
            manager.getSharedPreferences().edit().putString(setting.key, "25").commit();
            var screen = manager.createPreferenceScreen(activity);
            activity.setPreferenceScreen(screen);
            var slider = new SeekBarPreference(activity, "Burn in", "", setting);
            screen.addPreference(slider);
            assertFalse(slider.isPersistent());
            slider.setValue(0);
            assertEquals(0, (int) setting.savedValue());
            assertEquals("0", Setting.preferences.preferences.getString(setting.key, "missing"));
            assertEquals(0, new SeekBarPreference(activity, "Burn in", "", setting).getValue());
            slider.setValueWithoutPersisting(100);
            assertEquals(0, (int) setting.savedValue());
            slider.setValue(15);
            assertEquals("15", Setting.preferences.preferences.getString(setting.key, "missing"));
        }
    }
}
