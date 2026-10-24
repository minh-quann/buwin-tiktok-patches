"""Guard the author's component against accidental replacement by native approximations."""
import hashlib,json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class OriginalKyantSourceTest(unittest.TestCase):
 def test_mebieco_customized_component_is_copied_without_effect_or_gesture_edits(self):
  manifest=json.loads((ROOT/'third-party/kyant0-backdrop/mebieco.sha256.json').read_text())
  for name,expected in manifest.items():
   with self.subTest(source=name):
    text=(ROOT/'extensions/tiktok/src/glass/kotlin/app/morphe/extension/tiktok/glass/mebieco'/name).read_text()
    text=text.split('\n',1)[1].replace('app.morphe.extension.tiktok.glass.mebieco','expo.modules.liquidglassnative')
    # The approved adapter parameter changes blur strength only; compare all other
    # effects/gestures against the original MEBIECO source bytes.
    text=text.replace('    blurRadiusDp: Float = 4f,\n','').replace('blur(blurRadiusDp.dp.toPx())','blur(4f.dp.toPx())')
    self.assertEqual(expected,hashlib.sha256(text.encode()).hexdigest())
 def test_original_component_effect_and_gesture_bodies_are_unchanged(self):
  manifest=json.loads((ROOT/'third-party/kyant0-backdrop/sources.sha256.json').read_text())
  for name,expected in manifest.items():
   with self.subTest(source=name):
    text=(ROOT/'extensions/tiktok/src/glass/kotlin/app/morphe/extension/tiktok/glass/upstream'/name).read_text()
    self.assertTrue(text.startswith('/* Kyant0 AndroidLiquidGlass'))
    text=text.split('\n',1)[1].replace('app.morphe.extension.tiktok.glass.upstream','com.kyant.backdrop.catalog')
    self.assertEqual(expected,hashlib.sha256(text.encode()).hexdigest())
if __name__=='__main__':unittest.main()
