"""Pin Java's portability contract for transcendental functions."""
import re
import unittest
from pathlib import Path

class JavaStrictMathContract(unittest.TestCase):
    def test_transcendentals_use_strict_math(self):
        source = Path(__file__).resolve().parents[1] / 'java/src'
        pattern = r'(?<![A-Za-z])Math\.(exp|expm1|log|log1p|log10|tanh|sinh|cosh|pow|sqrt|cbrt|sin|cos|tan|asin|acos|atan|atan2|hypot)\('
        offenders = [str(p.relative_to(source)) for p in source.rglob('*.java') if re.search(pattern, p.read_text(encoding='utf-8'))]
        self.assertEqual(offenders, [])
