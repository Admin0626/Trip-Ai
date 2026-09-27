"""Report integrity tests: temporary evidence only, no database/network access."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class ReportIntegrityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.script = Path(__file__).with_name('render_batch2_report.py')
        original = self.script.parents[2] / 'docs/dev/evidence'
        self.evidence = self.folder / 'evidence'
        for name in ['baseline/responses.json', 'regression/responses.json', 'browser-regression/results.json']:
            target = self.evidence / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(original / name, target)
        self.output = self.folder / 'report.md'

    def change(self, relative, modify):
        path = self.evidence / relative
        document = json.loads(path.read_text(encoding='utf-8'))
        modify(document)
        path.write_text(json.dumps(document, ensure_ascii=False), encoding='utf-8')

    def render(self):
        return subprocess.run([sys.executable, str(self.script), '--evidence-dir', str(self.evidence),
                               '--output', str(self.output)], capture_output=True)

    def test_saved_passing_evidence_is_counted(self):
        self.assertEqual(self.render().returncode, 0)
        report = self.output.read_text(encoding='utf-8')
        self.assertIn('111 项检查，111 项通过，0 项未达预期', report)
        self.assertIn('27 项检查，27 项通过，0 项未达预期', report)

    def test_failed_cases_are_not_reported_as_all_passed(self):
        self.change('regression/responses.json', lambda d: d['cases'][0].update(passed=False))
        self.change('browser-regression/results.json', lambda d: d['rows'][0].update(passed=False))
        self.assertEqual(self.render().returncode, 0)
        report = self.output.read_text(encoding='utf-8')
        self.assertIn('111 项检查，110 项通过，1 项未达预期', report)
        self.assertIn('27 项检查，26 项通过，1 项未达预期', report)
        self.assertNotIn('检查全部通过', report)

    def test_empty_evidence_preserves_existing_report(self):
        self.output.write_text('existing report', encoding='utf-8')
        self.change('regression/responses.json', lambda d: d.update(cases=[]))
        self.assertNotEqual(self.render().returncode, 0)
        self.assertEqual(self.output.read_text(encoding='utf-8'), 'existing report')

    def test_non_boolean_flag_is_rejected(self):
        self.change('browser-regression/results.json', lambda d: d['rows'][0].update(passed='false'))
        self.assertNotEqual(self.render().returncode, 0)
        self.assertFalse(self.output.exists())

    def test_browser_errors_are_visible_even_if_checks_passed(self):
        self.change('browser-regression/results.json', lambda d: d.update(errors=['synthetic test error']))
        self.assertEqual(self.render().returncode, 0)
        self.assertIn('运行时异常 1 条', self.output.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
