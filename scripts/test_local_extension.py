"""Contracts for local cache replacement (no SDK or real Morphe cache needed)."""
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location('local_extension',
    Path(__file__).with_name('build-local-extension.py'))
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class CacheReplacementTest(unittest.TestCase):
    def test_preserves_other_entries_and_backs_up_original_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cache, dex = root / 'cache.mpp', root / 'classes.dex'
            dex.write_bytes(b'dex\nnew extension')
            with ZipFile(cache, 'w') as bundle:
                bundle.comment = b'bundle comment'
                bundle.writestr('classes.dex', b'patch bytecode')
                bundle.writestr('extensions/tiktok.mpe', b'old extension')
                bundle.writestr('resources/example', b'unchanged resource')
            before = cache.read_bytes()
            with contextlib.redirect_stdout(io.StringIO()):
                builder.update_cache(cache, dex)
            backups = list(root.glob('*.backup-*'))
            self.assertEqual(1, len(backups))
            self.assertEqual(before, backups[0].read_bytes())
            with ZipFile(cache) as bundle:
                self.assertEqual(b'dex\nnew extension', bundle.read('extensions/tiktok.mpe'))
                self.assertEqual(b'patch bytecode', bundle.read('classes.dex'))
                self.assertEqual(b'unchanged resource', bundle.read('resources/example'))
                self.assertEqual(b'bundle comment', bundle.comment)
                self.assertIsNone(bundle.testzip())

    def test_signed_or_missing_extension_bundle_is_left_untouched(self):
        for signed in [False, True]:
            with self.subTest(signed=signed), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                cache, dex = root / 'cache.mpp', root / 'classes.dex'
                dex.write_bytes(b'dex\nnew extension')
                with ZipFile(cache, 'w') as bundle:
                    if signed:
                        bundle.writestr('extensions/tiktok.mpe', b'old extension')
                        bundle.writestr('META-INF/SIGNATURE.SF', b'signature')
                    else:
                        bundle.writestr('other', b'no extension')
                before = cache.read_bytes()
                with self.assertRaises(ValueError):
                    builder.update_cache(cache, dex)
                self.assertEqual(before, cache.read_bytes())
                self.assertFalse(list(root.glob('*.backup-*')))


if __name__ == '__main__':
    unittest.main()
