"""Synthetic host-only harness regressions. NEVER invoke a physical ADB device."""
import os
import unittest
import tempfile
import json
import inspect
import io
from pathlib import Path
from unittest.mock import patch
with patch.dict(os.environ, {"POCKET_TABLET": "HOST_TEST_DOUBLE"}):
    import tablet_ui as ui


class SelectorTests(unittest.TestCase):
    def test_int64_oracle_never_rounds_through_float(self):
        self.assertTrue(hasattr(ui, "int64_matches"), "Float64 tolerance cannot leak into Int64 acceptance")
        self.assertTrue(ui.int64_matches("9007199254740993", 9007199254740993))
        self.assertFalse(ui.int64_matches("9007199254740992", 9007199254740993))
        for text in ("9.007199254740993e15", "1.0", "9223372036854775808", "NaN"):
            self.assertFalse(ui.int64_matches(text, 1))

    def test_float64_oracle_accepts_numeric_equivalence_not_rendering_precision(self):
        self.assertTrue(hasattr(ui, "float_matches"), "Numeric decoding is not a 17-digit string contract")
        for text in ("1", "1.0", "1.0000000000000000", "1e0", "+1E+0"):
            self.assertTrue(ui.float_matches(text, 1.0), text)
        for text in ("NaN", "Infinity", "1e999", "1oops", "1,0", "1.1"):
            self.assertFalse(ui.float_matches(text, 1.0), text)
        self.assertTrue(ui.float_matches("0.30000000000000004", 0.3))

    def test_qa_frame_decode_keeps_exact_counters_and_rejects_duplicate_frames(self):
        self.assertTrue(hasattr(ui, "parse_qa"), "09 metadata requires a bounded framed decoder, not UI count inference")
        synthetic = {"schemaVersion": 1, "applicationId": ui.PKG, "debuggable": True, "pid": 123, "valid": True,
                     "watches": [{"slot": i, "count": 9007199254740993 + i} for i in range(4)]}
        frame = "ROS_POCKET_QA_V1_BEGIN\n" + json.dumps(synthetic) + "\nROS_POCKET_QA_V1_END"
        decoded = ui.parse_qa("Android header\n" + frame + "\n")
        self.assertEqual(decoded["watches"][0]["count"], 9007199254740993)
        for malformed in (frame + "\n" + frame, frame.replace('"schemaVersion": 1', '"schemaVersion": true'), frame.replace('9007199254740993', 'true'), frame.replace('9007199254740993', '1e9999'), frame.replace('"pid": 123', '"pid": 123, "pid": 456')):
            with self.subTest(malformed=malformed[:40]), self.assertRaises(RuntimeError):
                ui.parse_qa(malformed)

    def test_exact_resource_identity_wins_over_foreign_or_text_lookalikes(self):
        foreign = {"package": "com.example.foreign", "resource-id": ui.PKG + ":id/connect"}
        label = {"package": ui.PKG, "text": "connect"}
        target = {"package": ui.PKG, "resource-id": ui.PKG + ":id/connect"}
        self.assertIs(ui.node_by([foreign, label, target], "connect"), target)
        with self.assertRaises(RuntimeError):
            ui.node_by([target, dict(target)], "connect")

    def test_topic_identifiers_are_never_casefolded_as_button_cosmetics(self):
        button = {"package": ui.PKG, "text": "LER · /HOST_ONLY/TOPIC"}
        with self.assertRaises(RuntimeError):
            ui.node_by([button], "Ler · /host_only/topic")
        row = {"package": ui.PKG, "content-desc": "Tópico /host_only/topic"}
        self.assertIs(ui.node_by([button, row], "Tópico /host_only/topic"), row)

    def test_android_allcaps_button_text_does_not_hide_exact_target(self):
        button = {"package": ui.PKG, "text": "CONECTAR", "bounds": "[10,20][210,80]", "clickable": "true"}
        try:
            actual = ui.node_by([button], "Conectar")
        except RuntimeError as exc:
            self.fail(f"Exact target rejected only because Android uses AllCaps: {exc}")
        self.assertIs(actual, button)


class TraceAdb:
    """Explicit synthetic transport; no subprocess or real device access."""
    def __init__(self):
        self.calls = []
        self.timeouts = []
        self.focused = "com.example.foreign/.Main"
        self.user = "0"
        self.packages = "package:" + ui.PKG
        self.dump_error = False
        self.ime_reads = [False]
        self.backs = 0
        self.taps = 0
        self.swipes = []
        self.after_swipe = None
        self.uid = "10042"
        self.owner = "10042"
        self.qa_text = "ROS_POCKET_QA_V1_BEGIN\n" + json.dumps({"schemaVersion": 1, "applicationId": ui.PKG, "debuggable": True, "pid": 123, "valid": False, "watches": []}) + "\nROS_POCKET_QA_V1_END\n"
        self.xml = '<hierarchy><node package="' + ui.PKG + '" text="CONNECT" resource-id="' + ui.PKG + ':id/connect" bounds="[10,20][210,80]" clickable="true" enabled="true"/></hierarchy>'

    def __call__(self, args, timeout):
        self.calls.append(list(args))
        self.timeouts.append(timeout)
        command = args[3:]
        if command == ["get-state"]: return "device\n"
        if command == ["shell", "getprop", "ro.product.model"]: return "HOST_FIXTURE_MODEL\n"
        if command == ["shell", "am", "get-current-user"]: return self.user + "\n"
        if command[:4] == ["shell", "pm", "list", "packages"]:
            return self.packages + (" uid:" + self.uid if "-U" in command else "") + "\n"
        if command == ["shell", "dumpsys", "window"]:
            return "  mCurrentFocus=Window{fixture u0 " + self.focused + "}\n  Window #0 Window{fixture u0 " + self.focused + "}:\n    mOwnerUid=" + self.owner + "\n"
        if command[:3] == ["shell", "mkdir", "-m"] and command[3]=="700": return ""
        if command[:2] == ["shell", "rmdir"]: return ""
        if command[:3] == ["shell", "rm", "-f"]: return ""
        if command[:3] == ["shell", "uiautomator", "dump"]:
            return "ERROR: could not get idle state." if self.dump_error else "UI hierchary dumped to: " + command[-1] + "\n"
        if command[:2] == ["shell", "cat"]: return self.xml
        if command == ["shell", "dumpsys", "activity", ui.PKG + "/com.lamggm.rospocket.MainActivity", "--pocket-qa-v1"]: return self.qa_text
        if command == ["shell", "pidof", ui.PKG]: return "123\n"
        if command == ["shell", "dumpsys", "input_method"]:
            value = self.ime_reads.pop(0) if len(self.ime_reads) > 1 else self.ime_reads[0]
            return "mInputShown=" + str(value).lower() + "\n"
        if command == ["shell", "input", "keyevent", "4"]: self.backs += 1; return ""
        if command == ["shell", "input", "keycombination", "113", "29"]: return ""
        if command[:3] == ["shell", "input", "text"] or command == ["shell", "input", "keyevent", "67"]:
            import xml.etree.ElementTree as ET
            root = ET.fromstring(self.xml)
            root.find("node").set("text", command[-1].replace("%s", " ") if command[2] == "text" else "")
            self.xml = ET.tostring(root, encoding="unicode")
            return ""
        if command[:3] == ["shell", "input", "tap"]:
            self.taps += 1
            if self.focused.startswith("PopupWindow:"): self.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
            return ""
        if command[:3] == ["shell", "input", "swipe"]:
            self.swipes.append(list(map(int, command[3:])))
            if self.after_swipe is not None: self.xml = self.after_swipe
            return ""
        raise AssertionError("Unexpected synthetic transport command: " + repr(command))


class PostwriteAdb(TraceAdb):
    """Synthetic View flow, exercising the real subprocess result boundary."""
    def __init__(self):
        super().__init__()
        self.focused = ui.PKG + '/com.lamggm.rospocket.MainActivity'
        self.dump_codes = [137, 0]
        self.dump_paths = []

    def __call__(self, args, timeout):
        result = super().__call__(args, timeout)
        if args[3:6] == ['shell', 'uiautomator', 'dump']:
            self.dump_paths.append(args[-1])
            code = self.dump_codes.pop(0)
            # Concrete CompletedProcess, not a physical ADB observation.
            completed = ui.subprocess.CompletedProcess(args, code, result.encode(), b'')
            with patch.object(ui.subprocess, 'run', return_value=completed):
                return ui.Client._process(args, timeout)
        return result


class CaptureRecoveryTests(unittest.TestCase):
    def test_boundary_classifies_only_exact_postwrite137_without_output_leak(self):
        path = '/data/local/tmp/ros-pocket-' + 'a' * 32 + '/hierarchy.xml'
        args = [ui.ADB, '-s', 'HOST_TEST_DOUBLE', 'shell', 'uiautomator', 'dump', path]
        stdout = ('UI hierchary dumped to: ' + path + '\n').encode()
        completed = ui.subprocess.CompletedProcess(args, 137, stdout, b'')
        with patch.object(ui.subprocess, 'run', return_value=completed) as run:
            with self.assertRaises(ui._PostwriteDump137) as failure:
                ui.Client._process(args, 25)
            run.assert_called_once_with(args, capture_output=True, timeout=25)
        self.assertEqual(failure.exception.remote, path)
        self.assertEqual(str(failure.exception), 'ADB command failed (exit 137)')
        self.assertNotIn('UI hierchary', repr(failure.exception))
        self.assertFalse(hasattr(failure.exception, 'stdout'))
        self.assertFalse(hasattr(failure.exception, 'stderr'))
        negatives = []
        for code in (-9, -137, 1, 9, 134, 139):
            negatives.append(('code ' + str(code), args, code, stdout, b''))
        for text in (b'', stdout.rstrip(b'\n'), stdout.replace(b'\n', b'\r\n'),
                     b'prefix\n' + stdout, stdout + b'suffix\n', stdout + stdout,
                     stdout.replace(b'hierchary', b'hierarchy'), stdout.replace(b'a' * 32, b'b' * 32),
                     b'HOST_SECRET_SENTINEL', stdout + b'\xff'):
            negatives.append(('stdout ' + repr(text), args, 137, text, b''))
        for stderr in (b' ', b'\n', b'HOST_SECRET_SENTINEL', b'\xff'):
            negatives.append(('stderr ' + repr(stderr), args, 137, stdout, stderr))
        commands = [args + ['extra'], args[:-1], [ui.ADB, '-s', 'HOST_TEST_DOUBLE', 'shell', 'cat', path],
                    args[:3] + ['shell', 'uiautomator', 'dump', '--compressed', path],
                    args[:3] + ['shell', ' '.join(args[3:])],
                    ['NOT_THE_CONFIGURED_ADB'] + args[1:],
                    args[:1] + ['-d'] + args[2:], args[:2] + [''] + args[3:],
                    args[:2] + ['HOST bad serial'] + args[3:]]
        for command in commands:
            negatives.append(('command ' + repr(command), command, 137, stdout, b''))
        for other in (path.replace('a' * 32, 'A' * 32), path.replace('a' * 32, 'a' * 31),
                      path.replace('a' * 32, 'a' * 33), path.replace('a' * 32, 'g' * 32),
                      path.replace('/hierarchy.xml', '/../hierarchy.xml'), path + '\n',
                      '/sdcard/hierarchy.xml', path.replace('/hierarchy.xml', '/other.xml')):
            command = args[:-1] + [other]
            negatives.append(('path ' + other, command, 137, ('UI hierchary dumped to: ' + other + '\n').encode(), b''))
        for label, command, code, output, stderr in negatives:
            with self.subTest(negative=label):
                completed = ui.subprocess.CompletedProcess(command, code, output, stderr)
                with patch.object(ui.subprocess, 'run', return_value=completed):
                    with self.assertRaises(RuntimeError) as failure:
                        ui.Client._process(command, 25)
                self.assertIs(type(failure.exception), RuntimeError)
                self.assertNotIn('HOST_SECRET_SENTINEL', str(failure.exception))
                self.assertNotIn('UI hierchary', str(failure.exception))

    def test_nonqualifying_dump_failures_never_retry_or_read_xml(self):
        for reason in ('negative_sigkill', 'other_exit', 'stderr', 'wrong_path', 'incomplete',
                       'timeout', 'oserror', 'runtime_error'):
            with self.subTest(reason=reason), tempfile.TemporaryDirectory() as directory:
                transport = TraceAdb()
                transport.focused = ui.PKG + '/com.lamggm.rospocket.MainActivity'
                def trace(args, timeout):
                    output = transport(args, timeout)
                    if args[3:6] != ['shell', 'uiautomator', 'dump']: return output
                    if reason == 'runtime_error': raise RuntimeError('ADB command failed (exit 137)')
                    error = (ui.subprocess.TimeoutExpired(args, timeout) if reason == 'timeout'
                             else OSError('HOST fixture error') if reason == 'oserror' else None)
                    code = -9 if reason == 'negative_sigkill' else 139 if reason == 'other_exit' else 137
                    stdout = (output.replace('hierarchy.xml', 'other.xml') if reason == 'wrong_path'
                              else output.rstrip('\n') if reason == 'incomplete' else output).encode()
                    result = ui.subprocess.CompletedProcess(args, code, stdout, b'HOST_SECRET_SENTINEL' if reason == 'stderr' else b'')
                    with patch.object(ui.subprocess, 'run', side_effect=error, return_value=result):
                        return ui.Client._process(args, timeout)
                client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=trace)
                with self.assertRaises(RuntimeError) as failure: client.dump('negative')
                self.assertNotIn('HOST_SECRET_SENTINEL', str(failure.exception))
                self.assertEqual(len([c for c in transport.calls if c[3:6] == ['shell', 'uiautomator', 'dump']]), 1)
                self.assertFalse(any(c[3:5] == ['shell', 'cat'] for c in transport.calls))
                records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
                self.assertEqual([r['status'] for r in records], ['failed'])
                self.assertNotIn('failureClassification', records[0])
                self.assertEqual(list(client.out.glob('*.xml')), [])
                self.assertTrue(all('HOST_SECRET_SENTINEL' not in p.read_text() for p in client.out.iterdir()))

    def test_direct_adb_nonzero_never_becomes_stdout_or_generic_transport_retry(self):
        transport = PostwriteAdb()
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=transport)
            with self.assertRaises(ui._PostwriteDump137):
                client.adb('shell', 'uiautomator', 'dump', '/data/local/tmp/ros-pocket-' + 'c' * 32 + '/hierarchy.xml')
            self.assertEqual(len(transport.dump_paths), 1)
            self.assertEqual(list(client.out.iterdir()), [])

    def test_second_postwrite137_is_terminal_with_two_failed_cleaned_attempts(self):
        transport = PostwriteAdb(); transport.dump_codes = [137, 137, 0]
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=transport)
            with self.assertRaisesRegex(RuntimeError, 'exit 137'): client.dump('twice')
            self.assertEqual(len(set(transport.dump_paths)), 2)
            self.assertEqual(transport.dump_codes, [0])
            self.assertFalse(any(c[3:5] == ['shell', 'cat'] for c in transport.calls))
            records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
            self.assertEqual([r['status'] for r in records], ['failed', 'failed'])
            first = next(r for r in records if 'recoveryOf' not in r)
            second = next(r for r in records if 'recoveryOf' in r)
            self.assertEqual(second['recoveryOf'], first['attempt'])
            self.assertEqual(list(client.out.glob('*.xml')), [])
            for path in transport.dump_paths:
                self.assertIn([ui.ADB, '-s', client.serial, 'shell', 'rm', '-f', path], transport.calls)
                self.assertIn([ui.ADB, '-s', client.serial, 'shell', 'rmdir', str(Path(path).parent)], transport.calls)

    def test_first_cleanup_failure_preserves_original137_and_blocks_recovery(self):
        for stage in ('rm', 'rmdir'):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as directory:
                transport = PostwriteAdb()
                def trace(args, timeout):
                    output = transport(args, timeout)
                    if args[3:5] == ['shell', stage]: raise RuntimeError('HOST cleanup blocked')
                    return output
                client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=trace)
                with self.assertRaisesRegex(RuntimeError, 'exit 137') as failure: client.dump('cleanup')
                self.assertNotIn('cleanup blocked', str(failure.exception))
                self.assertEqual(len(transport.dump_paths), 1)
                self.assertFalse(any(c[3:5] == ['shell', 'cat'] for c in transport.calls))
                records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
                self.assertEqual(len(records), 1)
                self.assertTrue(records[0]['cleanupFailed'])
                self.assertEqual(records[0]['status'], 'failed')
                self.assertIn('exit 137', records[0]['error'])

    def test_foreign_focus_between_attempts_stops_before_new_directory_or_capture(self):
        transport = PostwriteAdb()
        def trace(args, timeout):
            output = transport(args, timeout)
            if args[3:5] == ['shell', 'rmdir']: transport.focused = 'com.example.foreign/.Main'
            return output
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=trace)
            with self.assertRaisesRegex(RuntimeError, 'focus'): client.dump('focus-change')
            self.assertEqual(len(transport.dump_paths), 1)
            self.assertEqual(len([c for c in transport.calls if c[3:6] == ['shell', 'mkdir', '-m']]), 1)
            self.assertFalse(any(c[3:5] == ['shell', 'cat'] for c in transport.calls))
            records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
            self.assertEqual([r['status'] for r in records], ['failed', 'failed'])
            self.assertTrue(any('focus' in r.get('error', '') for r in records))

    def test_recapture_retains_fresh_pre_and_post_read_focus_guards(self):
        for phase in ('before_read', 'after_read'):
            with self.subTest(phase=phase), tempfile.TemporaryDirectory() as directory:
                transport = PostwriteAdb()
                def trace(args, timeout):
                    output = transport(args, timeout)
                    if ((phase == 'before_read' and args[3:6] == ['shell', 'uiautomator', 'dump'])
                            or (phase == 'after_read' and args[3:5] == ['shell', 'cat'])):
                        transport.focused = 'com.example.foreign/.Main'
                    return output
                client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=trace)
                with self.assertRaisesRegex(RuntimeError, 'focus'): client.dump('read-focus')
                self.assertEqual(len(transport.dump_paths), 2)
                self.assertEqual(list(client.out.glob('*.xml')), [])
                calls = [c[3:] for c in transport.calls]
                dumps = [i for i, c in enumerate(calls) if c[:3] == ['shell', 'uiautomator', 'dump']]
                focuses = [i for i, c in enumerate(calls) if c == ['shell', 'dumpsys', 'window']]
                self.assertTrue(any(dumps[0] < i < dumps[1] for i in focuses))

    def test_popup_permission_remains_explicit_through_recapture(self):
        for allowed, owner, succeeds in ((False, '10042', False), (True, '10042', True), (True, '99999', False)):
            with self.subTest(allowed=allowed, owner=owner), tempfile.TemporaryDirectory() as directory:
                transport = PostwriteAdb(); transport.focused = 'PopupWindow:fixture'; transport.owner = owner
                client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=transport)
                with patch.object(client, 'focus', wraps=client.focus) as focus:
                    if succeeds: self.assertTrue(client.dump('popup', allow_popup=allowed))
                    else:
                        with self.assertRaisesRegex(RuntimeError, 'focus'): client.dump('popup', allow_popup=allowed)
                    self.assertTrue(focus.call_args_list)
                    self.assertTrue(all(call.kwargs == {'allow_popup': allowed} for call in focus.call_args_list))
                    self.assertEqual(focus.call_count, 3 if succeeds else 1)
                self.assertEqual(len(transport.dump_paths), 2 if succeeds else 0)

    def test_invalid_hierarchy_never_triggers_recovery_or_a_third_attempt(self):
        for recapturing in (False, True):
            for xml in ('', '<broken>', '<hierarchy/>', '<hierarchy><node package="com.example.foreign" text="HOST_SECRET_SENTINEL"/></hierarchy>',
                        '<hierarchy><node package="' + ui.PKG + '"/><node package="com.example.foreign" text="HOST_SECRET_SENTINEL"/></hierarchy>',
                        'X' * (2 * 1024 * 1024 + 1)):
                with self.subTest(recapturing=recapturing, xml=xml[:80]), tempfile.TemporaryDirectory() as directory:
                    transport = PostwriteAdb(); transport.xml = xml
                    transport.dump_codes = [137, 0, 0] if recapturing else [0, 0]
                    client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=transport)
                    with self.assertRaises(RuntimeError): client.dump('invalid-xml')
                    self.assertEqual(len(transport.dump_paths), 2 if recapturing else 1)
                    self.assertEqual(list(client.out.glob('*.xml')), [])
                    self.assertTrue(all('HOST_SECRET_SENTINEL' not in p.read_text() for p in client.out.iterdir()))
                    records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
                    self.assertTrue(all(r['status'] == 'failed' for r in records))

    def test_137_on_read_is_not_a_capture_retry(self):
        transport = PostwriteAdb(); transport.dump_codes = [0, 0]
        def trace(args, timeout):
            output = transport(args, timeout)
            if args[3:5] == ['shell', 'cat']:
                completed = ui.subprocess.CompletedProcess(args, 137, output.encode(), b'')
                with patch.object(ui.subprocess, 'run', return_value=completed): return ui.Client._process(args, timeout)
            return output
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=trace)
            with self.assertRaisesRegex(RuntimeError, 'exit 137'): client.dump('read-failure')
            self.assertEqual(len(transport.dump_paths), 1)
            self.assertEqual(list(client.out.glob('*.xml')), [])

    def test_recapture_requires_exact_explicit_success_confirmation(self):
        for scenario in ('missing', 'truncated', 'prefix', 'suffix', 'old_path'):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as directory:
                transport = PostwriteAdb()
                def trace(args, timeout):
                    output = transport(args, timeout)
                    if args[3:6] == ['shell', 'uiautomator', 'dump']:
                        if scenario == 'missing': return ''
                        if scenario == 'truncated': return output.rstrip('\n')
                        if scenario == 'prefix': return 'UNTRUSTED\n' + output
                        if scenario == 'suffix': return output + 'UNTRUSTED\n'
                        if scenario == 'old_path': return 'UI hierchary dumped to: ' + transport.dump_paths[0] + '\n'
                    return output
                client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=trace)
                with self.assertRaisesRegex(RuntimeError, 'Fresh'):
                    client.dump('recapture-confirmation')
                self.assertEqual(len(transport.dump_paths), 2)
                self.assertFalse(any(c[3:5] == ['shell', 'cat'] for c in transport.calls))
                self.assertEqual(list(client.out.glob('*.xml')), [])
                records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
                self.assertEqual([r['status'] for r in records], ['failed', 'failed'])

    def test_repeated_private_uuid_fails_closed_instead_of_reusing_the_failed_path(self):
        transport = PostwriteAdb()
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=transport)
            uuids = [ui.uuid.UUID(hex=value * 32) for value in ('1', '2', '3', '2')]
            with patch.object(ui.uuid, 'uuid4', side_effect=uuids):
                with self.assertRaisesRegex(RuntimeError, 'directory repeated'):
                    client.dump('uuid-collision')
            self.assertEqual(len(transport.dump_paths), 1)
            self.assertEqual(len([c for c in transport.calls if c[3:6] == ['shell', 'mkdir', '-m']]), 1)
            self.assertFalse(any(c[3:5] == ['shell', 'cat'] for c in transport.calls))
            records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
            self.assertEqual([r['status'] for r in records], ['failed', 'failed'])
            self.assertTrue(any(r.get('failureClassification') == 'postwrite_uiautomator_137' for r in records))

    def test_missing_new_xml_cannot_reuse_the_failed_attempts_complete_tree(self):
        transport = PostwriteAdb()
        files = {}
        def trace(args, timeout):
            if args[3:6] == ['shell', 'uiautomator', 'dump'] and not transport.dump_paths:
                files[args[-1]] = transport.xml
            output = transport(args, timeout)
            if args[3:5] == ['shell', 'cat']: return files.get(args[-1], '')
            return output
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=trace)
            with self.assertRaises(RuntimeError): client.dump('stale-file')
            first, second = transport.dump_paths
            self.assertIn(first, files)  # Complete but failed original remains unusable.
            self.assertNotIn(second, files)
            self.assertEqual([c[-1] for c in transport.calls if c[3:5] == ['shell', 'cat']], [second])
            self.assertEqual(list(client.out.glob('*.xml')), [])
            records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
            self.assertEqual([r['status'] for r in records], ['failed', 'failed'])

    def test_recapture_cleanup_failure_cannot_return_a_successful_observation(self):
        transport = PostwriteAdb()
        def trace(args, timeout):
            output = transport(args, timeout)
            if len(transport.dump_paths) == 2 and args[3:5] == ['shell', 'rmdir']:
                raise RuntimeError('HOST recovery cleanup blocked')
            return output
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=trace)
            with self.assertRaisesRegex(RuntimeError, 'cleanup failed'): client.dump('recovery-cleanup')
            self.assertEqual(len(transport.dump_paths), 2)
            records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
            self.assertEqual([r['status'] for r in records], ['failed', 'failed'])
            recovered = next(r for r in records if 'recoveryOf' in r)
            self.assertTrue(recovered['cleanupFailed'])

    def test_postwrite137_discards_failed_xml_then_returns_only_fresh_nodes(self):
        transport = PostwriteAdb()
        original = transport.xml
        client = None
        def trace(args, timeout):
            if args[3:6] == ['shell', 'uiautomator', 'dump'] and transport.dump_paths:
                failed_records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
                self.assertEqual([r['status'] for r in failed_records], ['failed'])
                self.assertEqual(list(client.out.glob('*.xml')), [])
                transport.xml = original.replace('CONNECT', 'FRESH_ONLY')
            return transport(args, timeout)
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client('HOST_TEST_DOUBLE', out=directory, runner=trace)
            try:
                nodes = client.dump('recapture')
            except RuntimeError as exc:
                self.fail('Proven postwrite137 must permit one fresh capture: ' + str(exc))
            self.assertEqual([n['text'] for n in nodes], ['FRESH_ONLY'])
            self.assertEqual(len(set(transport.dump_paths)), 2)
            for path in transport.dump_paths:
                self.assertRegex(path, r'^/data/local/tmp/ros-pocket-[0-9a-f]{32}/hierarchy\.xml$')
            calls = [c[3:] for c in transport.calls]
            first, second = transport.dump_paths
            self.assertNotIn(['shell', 'cat', first], calls)
            self.assertLess(calls.index(['shell', 'rm', '-f', first]), calls.index(['shell', 'uiautomator', 'dump', second]))
            self.assertLess(calls.index(['shell', 'rmdir', str(Path(first).parent)]), calls.index(['shell', 'mkdir', '-m', '700', str(Path(second).parent)]))
            records = [json.loads(p.read_text()) for p in client.out.glob('*.json')]
            failed = next(r for r in records if r['status'] == 'failed')
            passed = next(r for r in records if r['status'] == 'passed')
            self.assertEqual(failed['failureClassification'], 'postwrite_uiautomator_137')
            self.assertEqual(passed['recoveryOf'], failed['attempt'])
            self.assertEqual(len(records), 2)
            self.assertTrue(all(r['kind'] == 'synthetic_transport_test' for r in records))
            xml = list(client.out.glob('*.xml'))
            self.assertEqual([p.stem for p in xml], [passed['attempt']])
            self.assertIn('FRESH_ONLY', xml[0].read_text())


class GuardTests(unittest.TestCase):
    def test_repeated_summary_never_overwrites_an_earlier_attempt(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); marker = root / "same-label.json"
            marker.write_text("KEEP_EARLIER_EVIDENCE")
            client = ui.Client("HOST_TEST_DOUBLE", out=root, runner=transport)
            with patch.object(ui, "_default", client), patch.object(ui, "OUT", root), patch("sys.stdout", new_callable=io.StringIO):
                ui.summary("same-label"); ui.summary("same-label")
            self.assertEqual(marker.read_text(), "KEEP_EARLIER_EVIDENCE")
            self.assertEqual(len(list(client.out.glob("*summary*.json"))), 2)

    def test_field_rejects_remote_shell_syntax_and_reads_back_exact_text(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        transport.xml = '<hierarchy><node package="' + ui.PKG + '" class="android.widget.EditText" text="" resource-id="' + ui.PKG + ':id/explorer_search" bounds="[10,20][210,80]"/></hierarchy>'
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            self.assertTrue(hasattr(client, "field"), "Field typing requires guarded input and readback")
            with self.assertRaises(RuntimeError): client.field("explorer_search", "/foo;am force-stop")
            self.assertEqual(transport.calls, [])
            client.field("explorer_search", "/host_only/topic")
            self.assertEqual(ui.node_by(client.dump(), "explorer_search")["text"], "/host_only/topic")
            client.field("explorer_search", "")
            self.assertEqual(ui.node_by(client.dump(), "explorer_search")["text"], "")

    def test_qa_read_uses_exact_local_component_without_reusing_last_frame(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            self.assertTrue(hasattr(client, "qa"), "Harness must consume the existing debug Activity dump")
            self.assertIs(client.qa()["valid"], False)
            index = next(i for i, c in enumerate(transport.calls) if "--pocket-qa-v1" in c)
            self.assertLessEqual(transport.timeouts[index], 5)
            transport.qa_text = "ROS Pocket QA unavailable"
            with self.assertRaises(RuntimeError): client.qa()
            records = [json.loads(p.read_text()) for p in client.out.glob("*.json")]
            self.assertEqual(sorted(r["status"] for r in records), ["failed", "passed"])
            self.assertFalse(any("qa" in r for r in records if r["status"] == "failed"))

    def test_foreign_focus_stops_before_any_ui_read_or_gesture(self):
        self.assertTrue(hasattr(ui, "Client"), "Harness needs a transport-injected guarded client")
        transport = TraceAdb()
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with self.assertRaisesRegex(RuntimeError, "focus"):
                client.focus()
        self.assertFalse(any("input" in c or "uiautomator" in c for c in transport.calls))

    def test_failed_dump_cannot_reuse_old_xml_and_attempts_remain_distinct(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            self.assertTrue(hasattr(client, "dump"), "Client must reject a stale hierarchy even when adb exits zero")
            transport.dump_error = True
            with self.assertRaisesRegex(RuntimeError, "Fresh"):
                client.dump("same-label")
            self.assertFalse(any(c[3:5] == ["shell", "cat"] for c in transport.calls))
            transport.dump_error = False
            self.assertEqual(client.dump("same-label")[0]["text"], "CONNECT")
            records = [json.loads(p.read_text()) for p in client.out.glob("*.json")]
            self.assertEqual(sorted(r["status"] for r in records), ["failed", "passed"])
            self.assertTrue(all(r["kind"] == "synthetic_transport_test" for r in records))
            self.assertEqual(len(list(client.out.glob("*.xml"))), 1)

    def test_public_tap_uses_the_same_guarded_transport(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with patch.object(ui, "_default", client, create=True), patch.object(ui.subprocess, "run", side_effect=AssertionError("Legacy entry bypasses guarded transport")):
                ui.tap("connect")
        self.assertEqual(transport.taps, 1)

    def test_popup_tap_requires_exact_focused_window_owner_uid(self):
        transport = TraceAdb(); transport.focused = "PopupWindow:fixture"
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with self.assertRaisesRegex(RuntimeError, "focus"):
                client.focus()
            self.assertIn("popup", inspect.signature(client.tap).parameters, "Popup exception must be explicit and package-UID scoped")
            client.tap("connect", popup=True)
            self.assertEqual(transport.taps, 1)
            transport.focused = "PopupWindow:fixture"; transport.owner = "99999"
            before = len([c for c in transport.calls if "uiautomator" in c])
            with self.assertRaisesRegex(RuntimeError, "focus"):
                client.tap("connect", popup=True)
            self.assertEqual(len([c for c in transport.calls if "uiautomator" in c]), before)
            self.assertEqual(transport.taps, 1)

    def test_find_reads_the_post_swipe_tree_and_bounds_missing_targets(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        transport.xml = '<hierarchy><node package="' + ui.PKG + '" class="android.widget.ScrollView" bounds="[0,0][1200,800]"/></hierarchy>'
        transport.after_swipe = transport.xml.replace('/></hierarchy>', '><node package="' + ui.PKG + '" resource-id="' + ui.PKG + ':id/plot_chart" bounds="[20,100][1100,500]"/></node></hierarchy>')
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            self.assertTrue(hasattr(client, "find"), "Visible target search must observe the actual scroll result")
            nodes = client.find("plot_chart", max_swipes=2)
            self.assertEqual(ui.node_by(nodes, "plot_chart")["bounds"], "[20,100][1100,500]")
            self.assertEqual(len(transport.swipes), 1)
            with self.assertRaisesRegex(RuntimeError, "target"):
                client.find("missing-target", max_swipes=2)
            self.assertEqual(len(transport.swipes), 3)

    def test_scroll_stays_inside_current_scrollview_after_ime_hides(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        transport.ime_reads = [True, False]
        transport.xml = '<hierarchy><node package="' + ui.PKG + '" class="android.widget.LinearLayout" bounds="[0,0][1200,800]"><node package="' + ui.PKG + '" class="android.widget.ScrollView" scrollable="true" bounds="[200,200][900,600]"/></node></hierarchy>'
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with patch.object(ui, "_default", client):
                ui.scroll("down")
        self.assertEqual(transport.backs, 1)
        self.assertEqual(len(transport.swipes), 1)
        x1, y1, x2, y2, duration = transport.swipes[0]
        self.assertTrue(200 < x1 == x2 < 900)
        self.assertTrue(200 < y2 < y1 < 600)
        self.assertLess(y1 - y2, 200, "Overlapping swipes must not skip short controls")
        self.assertGreaterEqual(duration, 600)

    def test_tap_waits_for_ime_transition_without_second_back(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        transport.ime_reads = [True, True, False]
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            self.assertTrue(hasattr(client, "tap"), "Guarded tap must await IME disappearance")
            client.tap("connect")
        self.assertEqual(transport.backs, 1)
        self.assertEqual(transport.taps, 1)
        self.assertEqual(transport.ime_reads, [False])


class BodyViewportAdb(TraceAdb):
    """One body pane per frame; fixed status never changes scroll position."""
    # MainActivity: diagnostics, Explorer, watch editor, counts/message, nodes.
    # ExplorerView places its details text ABOVE the topic rows/buttons.
    panes = ('diagnostics', 'explorer_search', 'explorer_namespace', 'explorer_type',
             'explorer_favorites', 'explorer_details', 'topic', 'details',
             'watch_select_0', 'qos_reliable', 'counts', 'message', 'nodes')

    def __init__(self, position=0, topic=lambda: '/pocket_test/chatter'):
        super().__init__()
        self.position, self.topic = position, topic
        self.focused = ui.PKG + '/com.lamggm.rospocket.MainActivity'
        self.reliable = False
        self.transitions = []

    def __call__(self, args, timeout):
        import xml.etree.ElementTree as ET
        command = args[3:]
        if command[:2] == ['shell', 'cat']:
            root = ET.Element('hierarchy')
            def node(ident, text='', parent=root, **attrs):
                return ET.SubElement(parent, 'node', dict({
                    'package': ui.PKG, 'resource-id': ui.PKG + ':id/' + ident,
                    'text': text, 'bounds': '[20,300][980,370]',
                    'class': 'android.widget.TextView', 'checked': 'false',
                    'enabled': 'true', 'visible-to-user': 'true'}, **attrs))
            node('connect', 'CONECTAR', bounds='[600,20][980,70]')
            node('status', 'CONECTADO · Wi-Fi 192.0.2.17 · domínio 67', bounds='[20,140][980,190]')
            body = node('host_scroll', parent=root, bounds='[0,200][1000,1000]',
                        **{'class': 'android.widget.ScrollView'})
            pane = self.panes[self.position]
            topic = self.topic()
            text = {
                'diagnostics': 'Interface: Wi-Fi · domínio 67 · DDS peers: 1 · Nós ROS: 1',
                'topic': topic + '\nstd_msgs/msg/String\n1 pub · 0 sub',
                'details': ('Detalhes / QoS · ' + topic).upper(),
                'explorer_details': topic + '\nstd_msgs/msg/String\nQoS anunciado:\n\nPUBLICADOR (oferecido)\nGID: ' + 'ab'*16 + '\nNó(s) por GID: /pocket_fixture_' + topic.split('/')[2].removeprefix('harness_') + '\nreliability: best_effort',
                'counts': 'AGUARDANDO · 0 msgs\n0 publicador(es) compatível(is) · idade —',
            }.get(pane, '')
            attrs = {'content-desc': 'Tópico ' + topic} if pane == 'topic' else {}
            if pane == 'qos_reliable':
                attrs['checked'] = str(self.reliable).lower()
                node('qos_transient', parent=body, bounds='[500,300][980,370]')
            node(pane, text, body, **attrs)
            self.xml = ET.tostring(root, encoding='unicode')
        result = super().__call__(args, timeout)
        if command[:3] == ['shell', 'input', 'swipe']:
            old = self.position
            step = 1 if int(command[4]) > int(command[6]) else -1
            self.position = max(0, min(len(self.panes)-1, old+step))
            self.transitions.append((self.panes[old], self.panes[self.position]))
        if command[:3] == ['shell', 'input', 'tap'] and self.panes[self.position] == 'qos_reliable':
            self.reliable = not self.reliable
        return result


class PublicScriptTests(unittest.TestCase):
    def test_already_connected_acceptance_starts_from_bottom_body(self):
        import importlib
        module = importlib.import_module('tablet_accept')
        for initial in (0, len(BodyViewportAdb.panes)-1):
            with self.subTest(initial_viewport=initial), tempfile.TemporaryDirectory() as directory:
                transport = BodyViewportAdb(position=initial)
                client = ui.Client('HOST_VIEWPORT_DOUBLE', out=Path(directory), runner=transport)
                # The fixed status is already visible even at the bottom. A
                # successful status search is not a ScrollView reset.
                client.find('status', direction='up')
                self.assertEqual(transport.position, initial)
                self.assertEqual(transport.swipes, [])
                with patch.object(ui, '_default', client), \
                     patch.dict(os.environ, {'POCKET_EXPECT_WIFI_IPV4': '192.0.2.17'}), \
                     patch.object(ui.subprocess, 'run', side_effect=AssertionError('External process forbidden')):
                    try: result = module.run(); error = None
                    except RuntimeError as exc: error = exc
                self.assertIsNone(error, 'already-connected discovery navigation failed: ' + str(error))
                self.assertTrue(result['passed'])
                self.assertEqual(result['kind'], 'synthetic_transport_test')
                self.assertEqual(transport.panes[transport.position], 'topic')
                if initial:
                    self.assertIn(('explorer_search', 'diagnostics'), transport.transitions)
                self.assertIn(('diagnostics', 'explorer_search'), transport.transitions)
                report = json.loads(next(client.out.glob('accept-result-*.json')).read_text())
                self.assertEqual(report, result)

    def test_public_qos_returns_from_watch_editor_to_explorer(self):
        import importlib
        from contextlib import ExitStack
        module = importlib.import_module('tablet_regression')
        actual_check = module.check
        class QosBoundary(RuntimeError): pass
        gates = (
            'peer return diagnostics connected',
            'QoS control visible after diagnostics',
            'Reliable checkbox confirms selection',
            'Best-effort writer remains announced',
            'QoS details control is visible',
            'DDS discovery confirms owned writer offers best_effort',
            'Reliable stays unmatched while fixture publishes',
        )
        seen = []
        def check(name, fn, **kwargs):
            if name not in gates:
                return {'message': 'pocket-real-jazzy seq=10'}
            if name == gates[-1]:
                # Stop before the separate publication/QA oracle, but exercise
                # the real check/Client transition from details down to counts.
                actual_check(name, lambda ns: module.waiting_for_match(ns), **kwargs)
                seen.append(name)
                raise QosBoundary('HOST stop before publication/QA observation')
            result = actual_check(name, fn, **kwargs)
            seen.append(name)
            return result
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            transport = BodyViewportAdb(topic=lambda: module.fixture_topic('chatter'))
            client = ui.Client('HOST_VIEWPORT_DOUBLE', out=Path(directory), runner=transport)
            stack.enter_context(patch.object(ui, '_default', client))
            stack.enter_context(patch.object(module, 'check', side_effect=check))
            stack.enter_context(patch.object(ui.subprocess, 'run', side_effect=AssertionError('External process forbidden')))
            for name in ('launch', 'field', 'adb', 'fixture_start', 'owned_fixture_stop', 'disconnect_app'):
                stack.enter_context(patch.object(module, name))
            stack.enter_context(patch.object(module.time, 'sleep'))
            stack.enter_context(patch('sys.stdout', new_callable=io.StringIO))
            try: module.run(); error = None
            except RuntimeError as exc: error = exc
            self.assertIsInstance(error, QosBoundary, 'public QoS navigation failed: ' + str(error))
            self.assertEqual(seen, list(gates))
            self.assertIn(('qos_reliable', 'watch_select_0'), transport.transitions)
            self.assertEqual(transport.panes[transport.position], 'counts')
            self.assertTrue(transport.reliable)
            report = json.loads(next(client.out.glob('regression-results-*.json')).read_text())
            self.assertFalse(report['passed'])  # bounded synthetic run, never qualification

    def test_initial_explorer_fields_across_separate_viewports(self):
        import importlib
        import xml.etree.ElementTree as ET
        from contextlib import ExitStack
        module=importlib.import_module('tablet_regression')
        class SetupBoundary(RuntimeError):pass
        class ViewportAdb(TraceAdb):
            # Source order: MainActivity's first body card, then ExplorerView's
            # three vertically stacked inputs. Root controls never scroll.
            panes=('diagnostics','explorer_search','explorer_namespace','explorer_type','explorer_favorites','watch_select_0','nodes')
            def __init__(self,position):
                super().__init__();self.position=position
                self.focused=ui.PKG+'/com.lamggm.rospocket.MainActivity'
            def __call__(self,args,timeout):
                command=args[3:]
                if command[:2]==['shell','cat']:
                    root=ET.Element('hierarchy')
                    def node(ident,text,bounds,cls='android.widget.TextView',parent=root):
                        return ET.SubElement(parent,'node',{'package':ui.PKG,'resource-id':ui.PKG+':id/'+ident,'text':text,'bounds':bounds,'class':cls,'checked':'false','enabled':'true','visible-to-user':'true'})
                    node('connect','CONECTAR','[600,20][980,70]','android.widget.Button')
                    node('domain','67','[20,20][200,70]','android.widget.EditText')
                    node('peer','','[20,80][980,130]','android.widget.EditText')
                    node('status','Desconectado','[20,140][980,190]')
                    body=node('host_scroll','','[0,200][1000,1000]','android.widget.ScrollView')
                    ident=self.panes[self.position]
                    node(ident,'','[20,300][980,370]','android.widget.EditText' if ident.startswith('explorer_') and ident!='explorer_favorites' else 'android.widget.TextView',body)
                    self.xml=ET.tostring(root,encoding='unicode')
                result=super().__call__(args,timeout)
                if command[:3]==['shell','input','swipe']:
                    step=1 if int(command[4])>int(command[6]) else -1
                    self.position=max(0,min(len(self.panes)-1,self.position+step))
                return result
        for initial in (0,6):
            with self.subTest(initial_viewport=initial),tempfile.TemporaryDirectory() as directory:
                transport=ViewportAdb(initial)
                client=ui.Client('HOST_VIEWPORT_DOUBLE',out=Path(directory),runner=transport)
                self.assertNotEqual(client.kind,'adb_observation')
                writes=[]
                def field(ident,value):
                    # Field I/O is not under test: require the REAL client to
                    # have navigated to a fresh frame where this input exists.
                    ui.node_by(client.dump('visible-before-field'),ident)
                    writes.append((ident,value))
                with ExitStack() as stack:
                    stack.enter_context(patch.object(ui,'_default',client))
                    stack.enter_context(patch.object(module,'field',side_effect=field))
                    stack.enter_context(patch.object(module,'select_topic',side_effect=SetupBoundary('HOST setup complete')))
                    for name in ('launch','fixture_start','owned_fixture_stop','disconnect_app'):stack.enter_context(patch.object(module,name))
                    stack.enter_context(patch('sys.stdout',new_callable=io.StringIO))
                    try:module.run();failure=None
                    except RuntimeError as exc:failure=exc
                self.assertIsInstance(failure,SetupBoundary,'initial navigation failed: '+str(failure))
                self.assertEqual(writes[-3:],[('explorer_search',module.fixture_topic('chatter')),('explorer_namespace',''),('explorer_type','')])
                self.assertEqual(transport.position,5)
                self.assertGreater(len(transport.swipes),0)
                result=json.loads(next(client.out.glob('regression-results-*.json')).read_text())
                self.assertFalse(result['passed']) # intentional stop, not physical qualification


    def test_qos_restoration_finds_the_control_above_the_counts_viewport(self):
        import importlib
        from types import SimpleNamespace
        module=importlib.import_module('tablet_regression')
        ns=[{'package':ui.PKG,'resource-id':ui.PKG+':id/qos_reliable','text':'Reliable'}]
        def find(ident,**kwargs):
            self.assertEqual(ident,'qos_reliable');self.assertEqual(kwargs.get('direction'),'up');return ns
        with patch.object(ui,'client',return_value=SimpleNamespace(find=find)),patch.object(ui,'tap') as act:
            module.tap('qos_reliable');act.assert_called_once_with('qos_reliable')


    def test_launch_or_navigation_failure_attempts_guarded_app_cleanup(self):
        import importlib
        from types import SimpleNamespace
        from contextlib import ExitStack
        from unittest.mock import Mock
        module=importlib.import_module('tablet_regression')
        for phase in ('launch','navigation'):
            with self.subTest(phase=phase),tempfile.TemporaryDirectory() as directory:
                client=SimpleNamespace(kind='synthetic_cleanup_control',out=Path(directory),verify=Mock(),find=Mock(side_effect=RuntimeError('HOST navigation failed')))
                with ExitStack() as stack:
                    stack.enter_context(patch.object(ui,'client',return_value=client))
                    launch=stack.enter_context(patch.object(module,'launch',side_effect=RuntimeError('HOST launch failed') if phase=='launch' else None))
                    cleanup=stack.enter_context(patch.object(module,'disconnect_app',side_effect=RuntimeError('HOST cleanup safely blocked by focus guard')))
                    fixture=stack.enter_context(patch.object(module,'owned_fixture_stop'))
                    with self.assertRaisesRegex(RuntimeError,'HOST '+phase+' failed'):module.run()
                    cleanup.assert_called_once();fixture.assert_not_called();launch.assert_called_once();client.verify.assert_called_once()
                result=json.loads(next(Path(directory).glob('regression-results-*.json')).read_text())
                self.assertFalse(result['passed']);self.assertIn('HOST '+phase+' failed',result['error'])
                self.assertEqual(result['cleanupErrors'],[{'stage':'app','error':'HOST cleanup safely blocked by focus guard'}])


    def test_reconnect_finds_the_fixture_topic_below_the_explorer_summary(self):
        self._exercise_public_navigation_gate('correct domain rediscovers')


    def test_publisher_loss_finds_counts_above_the_message_viewport(self):
        self._exercise_public_navigation_gate('publisher loss marks data stale')


    def _exercise_public_navigation_gate(self, gate):
        import importlib
        from types import SimpleNamespace
        from unittest.mock import Mock
        from contextlib import ExitStack
        module=importlib.import_module('tablet_regression');actual_check=module.check
        class HostBoundary(RuntimeError):pass
        base=[{'package':ui.PKG,'resource-id':ui.PKG+':id/'+i,'text':text,'checked':'false'} for i,text in [('connect','CONECTAR'),('status','Desconectado · nenhum leitor DDS ativo'),('diagnostics','HOST connection diagnostics'),('counts','SEM DADOS RECENTES · 10 msgs'),('qos_reliable','Reliable'),('qos_transient','Transient local'),('explorer_search',''),('explorer_namespace',''),('explorer_type',''),('explorer_favorites','')]]
        extra=[]
        def find(ident,**kwargs):
            if ident=='counts' and gate=='publisher loss marks data stale' and kwargs.get('direction')!='up':
                raise ui.MissingTarget('HOST viewport: counts is above the message')
            if ident.startswith('Tópico '):
                self.assertEqual(ident,'Tópico /pocket_test/harness_'+module.FIXTURE_OWNER+'/chatter')
                if kwargs.get('direction')!='down':raise ui.MissingTarget('HOST viewport: topic is below the Explorer summary')
                extra[:]=[{'package':ui.PKG,'content-desc':ident}]
            ui.node_by(base+extra,ident);return base+extra
        target_seen=[]
        def check(name,fn,**kwargs):
            if name==gate:
                target_seen.append(name);return actual_check(name,fn,**kwargs)
            if target_seen:raise HostBoundary('HOST boundary after target gate')
            return {'message':'pocket-real-jazzy seq=10','value':'1.0'}
        def select(topic):
            if target_seen:raise HostBoundary('HOST boundary after target gate')
        with tempfile.TemporaryDirectory() as directory:
            client=SimpleNamespace(kind='synthetic_public_flow',out=Path(directory),verify=Mock(),find=find,hide_ime=Mock())
            with ExitStack() as stack:
                for obj,name,kwargs in [(ui,'client',{'return_value':client}),(module,'dump',{'side_effect':lambda *a:base+extra}),(module,'check',{'side_effect':check}),(module,'select_topic',{'side_effect':select}),(module,'disconnect_app',{'return_value':True})]:stack.enter_context(patch.object(obj,name,**kwargs))
                for name in ('launch','field','tap','adb','fixture_start','owned_fixture_stop'):stack.enter_context(patch.object(module,name))
                try:module.run();error=None
                except RuntimeError as exc:error=exc
            self.assertIsInstance(error,HostBoundary,'unexpected public-flow error: '+str(error))
            self.assertEqual(target_seen,[gate])


    def test_app_cleanup_requires_confirmed_disconnected_ui(self):
        import importlib
        from types import SimpleNamespace
        from unittest.mock import Mock
        module=importlib.import_module('tablet_regression')
        self.assertTrue(callable(getattr(module,'disconnect_app',None)))
        def nodes(button,status):return [{'package':ui.PKG,'resource-id':ui.PKG+':id/connect','text':button},{'package':ui.PKG,'resource-id':ui.PKG+':id/status','text':status}]
        connected=nodes('DESCONECTAR','CONECTADO · domínio 67')
        disconnected=nodes('CONECTAR','Desconectado · nenhum leitor DDS ativo')
        for scenario in ('confirmed','already_disconnected','ignored_tap','unknown_button'):
            with self.subTest(scenario=scenario):
                client=SimpleNamespace(find=Mock())
                frames=[connected,disconnected] if scenario=='confirmed' else None
                static=disconnected if scenario=='already_disconnected' else nodes('APLICAR','unknown') if scenario=='unknown_button' else connected
                with patch.object(ui,'client',return_value=client),patch.object(module,'dump',side_effect=frames,return_value=static),patch.object(module,'tap') as tap,patch.object(module.time,'monotonic',side_effect=range(100)),patch.object(module.time,'sleep'):
                    if scenario in ('ignored_tap','unknown_button'):
                        with self.assertRaisesRegex(RuntimeError,'disconnect'):module.disconnect_app(timeout=3)
                    else:self.assertTrue(module.disconnect_app(timeout=3))
                    self.assertEqual(tap.call_count,1 if scenario in ('confirmed','ignored_tap') else 0)


    def test_discovery_requires_connected_state_in_the_topic_observation(self):
        import importlib
        from types import SimpleNamespace
        module=importlib.import_module('tablet_accept')
        for final_state,accepted in (('RECONECTANDO',False),('DESCONECTADO',False),('CONECTADO',True)):
            with self.subTest(final_state=final_state),tempfile.TemporaryDirectory() as directory:
                def nodes(state):return [{'package':ui.PKG,'resource-id':ui.PKG+':id/diagnostics','text':'HOST diagnostics'},{'package':ui.PKG,'resource-id':ui.PKG+':id/status','text':state+' · Wi-Fi 192.0.2.1 · domínio 67'},{'package':ui.PKG,'content-desc':'Tópico /pocket_test/chatter'}]
                def find(ident,**kw):
                    ns=nodes('CONECTADO' if ident=='diagnostics' else final_state);ui.node_by(ns,ident);return ns
                client=SimpleNamespace(kind='synthetic_host_control',out=Path(directory),find=find)
                with patch.object(ui,'client',return_value=client),patch.dict(os.environ,{'POCKET_EXPECT_WIFI_IPV4':'192.0.2.1'}):
                    if accepted:self.assertTrue(module.run()['passed'])
                    else:
                        with self.assertRaisesRegex(RuntimeError,'connected'):module.run()


    def test_domain_empty_oracle_uses_the_complete_application_text(self):
        import importlib
        module=importlib.import_module('tablet_regression')
        full='Nenhum tópico corresponde aos filtros (ou descoberta vazia).'
        self.assertTrue(module.fixture_topics_absent([{'package':ui.PKG,'text':full}]))
        self.assertFalse(module.fixture_topics_absent([{'package':ui.PKG,'text':'Nenhum tópico corresponde.'}]))


    def test_empty_fields_accept_only_their_actual_hint_as_text(self):
        import xml.etree.ElementTree as ET
        hints={'peer':'Peer IPv4 opcional — deixe vazio para descoberta automática','explorer_search':'Buscar tópico, tipo ou nó','explorer_namespace':'Namespace (ex.: /robot)','explorer_type':'Filtro de tipo (ex.: std_msgs/msg)'}
        class HintAdb(TraceAdb):
            def __call__(self,args,timeout):
                result=super().__call__(args,timeout)
                if args[3:]==['shell','input','keyevent','67']:
                    root=ET.fromstring(self.xml);root.find('node').set('text',self.displayed_hint);self.xml=ET.tostring(root,encoding='unicode')
                return result
        for ident,hint in hints.items():
            with self.subTest(ident=ident),tempfile.TemporaryDirectory() as directory:
                transport=HintAdb();transport.focused=ui.PKG+'/com.lamggm.rospocket.MainActivity';transport.displayed_hint=hint
                transport.xml='<hierarchy><node package="'+ui.PKG+'" class="android.widget.EditText" text="old value" resource-id="'+ui.PKG+':id/'+ident+'" bounds="[10,20][210,80]"/></hierarchy>'
                client=ui.Client('HOST_TEST_DOUBLE',out=Path(directory),runner=transport)
                try:client.field(ident,'');accepted=True
                except RuntimeError:accepted=False
                self.assertTrue(accepted,'the actual unchanged app hint must count as empty')
                transport.displayed_hint='ARBITRARY STALE TEXT'
                with self.assertRaisesRegex(RuntimeError,'readback'):client.field(ident,'')


    def test_regression_import_does_not_launch_device_or_fixture_work(self):
        import importlib
        transport = TraceAdb()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            client = ui.Client("HOST_TEST_DOUBLE", out=root, runner=transport)
            with patch.object(ui, "_default", client), patch.object(ui, "OUT", root), patch.dict(os.environ, {"POCKET_ROS_IMAGE": "HOST_TEST_IMAGE_NEVER_RUN"}), patch.object(ui.time, "monotonic", side_effect=[0., 100.]), patch("sys.stdout", new_callable=io.StringIO):
                try:
                    module = importlib.import_module("tablet_regression")
                except AssertionError as exc:
                    self.fail("Regression import executed device work: " + str(exc))
                self.assertEqual(transport.calls, [])
                self.assertTrue(callable(getattr(module, "run", None)))

    def test_regression_does_not_retry_foreign_focus_as_a_product_timeout(self):
        import importlib
        module = importlib.import_module("tablet_regression")
        with patch.object(module, "dump", side_effect=RuntimeError("focus changed")), patch.object(module, "results", []), patch.object(module.time, "monotonic", side_effect=range(100)), patch.object(module.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "focus changed"):
                module.check("host-focus-negative", lambda nodes: True)
            self.assertEqual(module.results[-1]["classification"], "harness_blocked")

    def test_connected_state_is_not_a_disconnected_substring(self):
        import importlib
        module=importlib.import_module("tablet_regression")
        self.assertTrue(callable(getattr(module,"state_is",None)))
        for state,expected in (("DESCONECTADO",False),("RECONECTANDO",False),("CONECTADO",True)):
            ns=[{"package":ui.PKG,"resource-id":ui.PKG+":id/status","text":state+" · domínio 67"}]
            self.assertEqual(module.state_is(ns,'status','CONECTADO'),expected)

    def test_zero_matched_publishers_is_not_a_ten_publishers_substring(self):
        import importlib
        module=importlib.import_module("tablet_regression")
        self.assertTrue(callable(getattr(module,"waiting_for_match",None)))
        for count,accepted in ((10,False),(100,False),(0,True)):
            text='AGUARDANDO · 0 msgs\n'+str(count)+' publicador(es) compatível(is) · idade —'
            ns=[{"package":ui.PKG,"resource-id":ui.PKG+":id/counts","text":text}]
            self.assertEqual(bool(module.waiting_for_match(ns)),accepted)

    def test_qos_readback_and_applied_request_require_reliable(self):
        import importlib
        from test_tablet_qos import waiting_qa,waiting_nodes
        module=importlib.import_module('tablet_regression')
        def nodes(checked):return [{'package':ui.PKG,'resource-id':ui.PKG+':id/qos_reliable','checked':checked}]
        self.assertTrue(module.reliable_checkbox_selected(nodes('true')))
        self.assertFalse(module.reliable_checkbox_selected(nodes('false')))
        self.assertFalse(module.reliable_checkbox_selected([]))
        good=waiting_qa()
        self.assertTrue(module.reliable_unmatched_sample(waiting_nodes(),good))
        for field,value in (('requestedReliable',False),('pending',True),('count',1),('matched',1),('hasError',True),('readerActive',False)):
            sample=waiting_qa();sample['watches'][0][field]=value
            self.assertFalse(module.reliable_unmatched_sample(waiting_nodes(),sample))

    def test_best_effort_writer_requires_fresh_exact_topic_endpoint(self):
        import importlib
        module=importlib.import_module('tablet_regression')
        topic='/pocket_test/harness_TEST/chatter'
        def row(name=topic,pubs=1,package=ui.PKG):
            return {'package':package,'content-desc':'Tópico '+name,'text':f'{name}\nstd_msgs/msg/String\n{pubs} pub · 0 sub'}
        self.assertTrue(module.best_effort_writer_visible([row()],topic))
        self.assertFalse(module.best_effort_writer_visible([row(pubs=0)],topic))
        self.assertFalse(module.best_effort_writer_visible([row(name='/pocket_test/chatter')],topic))
        self.assertFalse(module.best_effort_writer_visible([row(package='com.example.foreign')],topic))

    def test_domain_isolation_requires_an_explicit_empty_topic_list(self):
        import importlib
        module=importlib.import_module("tablet_regression")
        self.assertTrue(callable(getattr(module,"fixture_topics_absent",None)))
        self.assertFalse(module.fixture_topics_absent([]))
        empty={"package":ui.PKG,"text":"Nenhum tópico corresponde aos filtros (ou descoberta vazia)."}
        self.assertTrue(module.fixture_topics_absent([empty]))
        self.assertFalse(module.fixture_topics_absent([empty,{"package":ui.PKG,"content-desc":"Tópico /pocket_test/chatter"}]))
        self.assertFalse(module.fixture_topics_absent([dict(empty,package='com.example.foreign')]))

    def test_raw_dumps_never_use_shared_external_storage(self):
        transport=TraceAdb();transport.focused=ui.PKG+'/com.lamggm.rospocket.MainActivity'
        with tempfile.TemporaryDirectory() as directory:
            client=ui.Client('HOST_TEST_DOUBLE',out=Path(directory),runner=transport)
            client.dump()
        targets=[c[-1] for c in transport.calls if c[3:6]==['shell','uiautomator','dump']]
        self.assertTrue(targets)
        self.assertTrue(all(path.startswith('/data/local/tmp/ros-pocket-') for path in targets))
        self.assertTrue(any(c[3:6]==['shell','mkdir','-m'] and c[6]=='700' for c in transport.calls))

    def test_private_dump_directory_is_removed_after_capture(self):
        transport=TraceAdb();transport.focused=ui.PKG+'/com.lamggm.rospocket.MainActivity'
        with tempfile.TemporaryDirectory() as directory:
            ui.Client('HOST_TEST_DOUBLE',out=Path(directory),runner=transport).dump()
        created=[c[-1] for c in transport.calls if c[3:6]==['shell','mkdir','-m']]
        removed=[c[-1] for c in transport.calls if c[3:5]==['shell','rmdir']]
        self.assertTrue(created)
        self.assertEqual(removed,created)

    def test_mixed_package_hierarchy_is_not_admitted_as_a_scoped_dump(self):
        transport=TraceAdb();transport.focused=ui.PKG+'/com.lamggm.rospocket.MainActivity'
        transport.xml=transport.xml.replace('</hierarchy>','<node package="com.example.foreign" text="HOST_SECRET_SENTINEL"/></hierarchy>')
        with tempfile.TemporaryDirectory() as directory:
            client=ui.Client('HOST_TEST_DOUBLE',out=Path(directory),runner=transport)
            with self.assertRaisesRegex(RuntimeError,'outside the target package'):client.dump()
            self.assertEqual(list(client.out.glob('*.xml')),[])
            self.assertTrue(all('HOST_SECRET_SENTINEL' not in p.read_text() for p in client.out.iterdir()))

    def test_fixture_publications_use_a_run_specific_observable_namespace(self):
        import importlib
        from types import SimpleNamespace
        module=importlib.import_module('tablet_regression');calls=[]
        with patch.object(module,'FIXTURE_NAME','HOST_NAME'),patch.object(module,'FIXTURE_OWNER','HOST_RUN_NONCE'),patch.object(module,'ROS_IMAGE','HOST_IMAGE'),patch.object(module.subprocess,'run',side_effect=lambda args,**kw:calls.append(args) or SimpleNamespace(returncode=0,stdout='HOST_CONTAINER')):
            module.fixture_start()
        command=next(c[-1] for c in calls if c[:2]==['docker','run'])
        self.assertIn('/pocket_test/chatter:=/pocket_test/harness_HOST_RUN_NONCE/chatter',command)
        self.assertIn('/pocket_test/value:=/pocket_test/harness_HOST_RUN_NONCE/value',command)
        self.assertIn('__node:=pocket_fixture_HOST_RUN_NONCE',command)

    def test_observation_gate_accepts_a_visible_exact_text_selector(self):
        import importlib
        from types import SimpleNamespace
        from unittest.mock import Mock
        module=importlib.import_module('tablet_regression')
        nodes=[{'package':ui.PKG,'text':'Nenhum tópico corresponde aos filtros (ou descoberta vazia).'}]
        client=SimpleNamespace(find=Mock(return_value=nodes))
        with patch.object(module.ui,'client',return_value=client),patch.object(module,'dump',return_value=nodes),patch.object(module,'scroll',side_effect=AssertionError('unexpected scroll')):
            try:actual=module.check('HOST_TEXT_SELECTOR',lambda ns:'explicit empty',view='Nenhum tópico corresponde aos filtros (ou descoberta vazia).')
            except AssertionError:actual=False
        self.assertEqual(actual,'explicit empty')

    def test_restoration_requires_a_strictly_new_string_sample(self):
        import importlib
        module=importlib.import_module('tablet_regression')
        self.assertTrue(callable(getattr(module,'string_after',None)))
        def nodes(value,state='RECEBENDO'):
            return [{'package':ui.PKG,'resource-id':ui.PKG+':id/counts','text':state+' · 10 msgs'}, {'package':ui.PKG,'resource-id':ui.PKG+':id/message','text':value}]
        self.assertFalse(module.string_after(nodes('pocket-real-jazzy seq=10'),10))
        self.assertFalse(module.string_after(nodes('pocket-real-jazzy seq=9'),10))
        self.assertFalse(module.string_after(nodes('pocket-real-jazzy seq=11junk'),10))
        self.assertFalse(module.string_after(nodes('pocket-real-jazzy seq=11','NÃO RECEBENDO'),10))
        self.assertEqual(module.string_after(nodes('pocket-real-jazzy seq=11'),10)['sequence'],11)

    def test_numeric_resume_requires_new_finite_data_not_a_cosmetic_reformat(self):
        import importlib
        module=importlib.import_module('tablet_regression')
        self.assertTrue(callable(getattr(module,'numeric_after',None)))
        def nodes(value,state='RECEBENDO'):
            return [{'package':ui.PKG,'resource-id':ui.PKG+':id/counts','text':state+' · 10 msgs'},{'package':ui.PKG,'resource-id':ui.PKG+':id/message','text':value}]
        for value in ('100','1e2','100.00000000000000','NaN','Infinity'):
            self.assertFalse(module.numeric_after(nodes(value),100))
        self.assertFalse(module.numeric_after(nodes('100.1','SEM DADOS RECENTES'),100))
        self.assertEqual(module.numeric_after(nodes('100.1'),100)['value'],100.1)

    def test_dump_records_the_exact_private_directory_for_cleanup_readback(self):
        transport=TraceAdb();transport.focused=ui.PKG+'/com.lamggm.rospocket.MainActivity'
        with tempfile.TemporaryDirectory() as directory:
            client=ui.Client('HOST_TEST_DOUBLE',out=Path(directory),runner=transport);client.dump()
            record=json.loads(next(client.out.glob('*.json')).read_text())
            created=next(c[-1] for c in transport.calls if c[3:6]==['shell','mkdir','-m'])
            self.assertEqual(record.get('remoteDirectory'),created)

    def test_wifi_acceptance_compares_the_entire_ipv4_address(self):
        import importlib
        from types import SimpleNamespace
        module=importlib.import_module("tablet_accept")
        for address,accepted in (("192.0.2.17",False),("192.0.2.1",True)):
            with self.subTest(address=address), tempfile.TemporaryDirectory() as directory:
                nodes=[{"package":ui.PKG,"resource-id":ui.PKG+":id/diagnostics","text":"HOST diagnostics"},{"package":ui.PKG,"resource-id":ui.PKG+":id/status","text":"CONECTADO · Wi-Fi "+address+" · domínio 67"},{"package":ui.PKG,"content-desc":"Tópico /pocket_test/chatter"}]
                client=SimpleNamespace(kind="synthetic_host_control",out=Path(directory),find=lambda ident,**k:(ui.node_by(nodes,ident),nodes)[1])
                with patch.object(ui,"client",return_value=client),patch.dict(os.environ,{"POCKET_EXPECT_WIFI_IPV4":"192.0.2.1"}):
                    if accepted:self.assertTrue(module.run()["passed"])
                    else:
                        with self.assertRaisesRegex(RuntimeError,"expected tablet LAN address"):module.run()

    def test_initial_topic_filter_precedes_offscreen_watch_navigation(self):
        import importlib
        from types import SimpleNamespace
        module = importlib.import_module("tablet_regression")
        events = []
        nodes = [{"package":ui.PKG,"resource-id":ui.PKG+":id/connect","text":"CONECTAR"}, {"package":ui.PKG,"resource-id":ui.PKG+":id/explorer_favorites","checked":"false"}, {"package":ui.PKG,"resource-id":ui.PKG+":id/status","text":"Desconectado. Use o mesmo Domain ID e Wi-Fi do ROS 2."}]
        class StopBeforeReader(RuntimeError): pass
        def tap(ident):
            events.append(("tap",ident))
            if ident == "watch_select_0": raise StopBeforeReader("synthetic setup boundary")
        with tempfile.TemporaryDirectory() as directory:
            client = SimpleNamespace(kind="synthetic_host_control",out=Path(directory),verify=lambda:None,find=lambda *a,**k:nodes,hide_ime=lambda:None)
            with patch.object(ui,"client",return_value=client),patch.object(module,"launch"),patch.object(module,"dump",return_value=nodes),patch.object(module,"tap",side_effect=tap),patch.object(module,"field",side_effect=lambda *args:events.append(("field",*args))),patch.object(module,"fixture_start"),patch.object(module,"owned_fixture_stop"):
                with self.assertRaises(StopBeforeReader): module.run()
        filtered = ("field","explorer_search","/pocket_test/harness_"+module.FIXTURE_OWNER+"/chatter")
        self.assertIn(filtered,events)
        self.assertLess(events.index(filtered),events.index(("tap","watch_select_0")))

    def test_view_navigation_does_not_consume_observation_timeout(self):
        import importlib
        from types import SimpleNamespace
        module = importlib.import_module("tablet_regression")
        clock = [0.0]
        node = {"package": ui.PKG, "resource-id": ui.PKG + ":id/nodes", "text": "/pocket_fixture"}
        def navigate(*args, **kwargs):
            clock[0] += 30.0
            return [node]
        from unittest.mock import Mock
        navigator = SimpleNamespace(find=Mock(side_effect=navigate))
        def nodes(*args): return [node] if clock[0] >= 30.0 else []
        with patch.object(module, "results", []), patch.object(ui, "client", return_value=navigator), patch.object(module, "dump", side_effect=nodes), patch.object(module, "scroll", side_effect=navigate), patch.object(module.time, "monotonic", side_effect=lambda: clock[0]):
            self.assertEqual(module.check("synthetic-navigation-budget", lambda ns: module.content(ns, "nodes"), timeout=25, view="nodes"), "/pocket_fixture")
        navigator.find.assert_called_once_with("nodes", direction="down")

    def test_preflight_failure_never_attempts_fixture_or_ui_cleanup(self):
        import importlib
        from types import SimpleNamespace
        module = importlib.import_module("tablet_regression")
        with tempfile.TemporaryDirectory() as directory:
            client = SimpleNamespace(kind="synthetic_host_control", out=Path(directory), verify=lambda: (_ for _ in ()).throw(RuntimeError("synthetic preflight blocked")))
            with patch.object(ui, "client", return_value=client), patch.object(module, "owned_fixture_stop") as cleanup, patch.object(module, "launch") as launch:
                with self.assertRaisesRegex(RuntimeError, "synthetic preflight blocked"):
                    module.run()
            cleanup.assert_not_called(); launch.assert_not_called()
            reports = list(client.out.glob("regression-results-*.json"))
            self.assertEqual(len(reports), 1)
            report = json.loads(reports[0].read_text())
            self.assertFalse(report["passed"])
            self.assertNotIn("cleanupError", report)

    def test_cyclone_fixture_uses_only_explicit_verified_interface_configuration(self):
        import importlib
        from types import SimpleNamespace
        module = importlib.import_module("tablet_regression"); calls = []
        with patch.object(module, "FIXTURE_NAME", "HOST_FIXTURE_NEVER_RUN"), patch.object(module, "FIXTURE_OWNER", "HOST_ONLY_OWNER"), patch.object(module, "ROS_IMAGE", "HOST_IMAGE_NEVER_RUN"), patch.dict(os.environ, {"POCKET_FIXTURE_INTERFACE": "HOST_TEST_IF"}), patch.object(module.subprocess, "run", side_effect=lambda args, **kw: calls.append(args) or SimpleNamespace(returncode=0, stdout="HOST_SYNTHETIC_CONTAINER")):
            module.fixture_start('rmw_cyclonedds_cpp', True)
        args = next(c for c in calls if c[:2] == ['docker','run'])
        uris = [a.split('=',1)[1] for a in args if a.startswith('CYCLONEDDS_URI=')]
        self.assertEqual(len(uris), 1, "Multi-NIC Cyclone configuration belongs only to the owned fixture")
        import xml.etree.ElementTree as ET
        self.assertEqual(ET.fromstring(uris[0]).find('.//NetworkInterface').get('name'), 'HOST_TEST_IF')

    def test_fixture_start_is_uniquely_owned_bounded_and_never_pulls(self):
        import importlib
        from types import SimpleNamespace
        module = importlib.import_module("tablet_regression"); calls = []
        def synthetic_docker(args, **kwargs):
            calls.append((args, kwargs))
            return SimpleNamespace(returncode=0, stdout="HOST_SYNTHETIC_CONTAINER")
        with patch.object(module, "FIXTURE_NAME", "HOST_FIXTURE_NEVER_RUN"), patch.object(module, "FIXTURE_OWNER", "HOST_ONLY_OWNER"), patch.object(module, "ROS_IMAGE", "HOST_IMAGE_NEVER_RUN"), patch.object(module.subprocess, "run", side_effect=synthetic_docker):
            module.fixture_start()
        args, options = next(c for c in calls if c[0][:2] == ["docker", "run"])
        self.assertIn("--pull=never", args)
        self.assertEqual(args[args.index("--name") + 1], "HOST_FIXTURE_NEVER_RUN")
        self.assertIn("io.lamggm.pocket.harness-owner=HOST_ONLY_OWNER", args)
        self.assertLessEqual(options.get("timeout", float("inf")), 45)

    def test_app_cleanup_is_attempted_even_when_fixture_cleanup_fails(self):
        import importlib
        from types import SimpleNamespace
        from unittest.mock import Mock
        module=importlib.import_module("tablet_regression")
        def connection(text):return [{"package":ui.PKG,"resource-id":ui.PKG+":id/connect","text":text},{"package":ui.PKG,"resource-id":ui.PKG+":id/status","text":'Desconectado · nenhum leitor DDS ativo' if text=='CONECTAR' else 'CONECTADO · domínio 67'}]
        with tempfile.TemporaryDirectory() as directory:
            client=SimpleNamespace(kind="synthetic_host_control",out=Path(directory),verify=lambda:None,find=Mock(return_value=connection('CONECTAR')))
            with patch.object(ui,'client',return_value=client),patch.object(module,'launch'),patch.object(module,'dump',side_effect=[connection('CONECTAR'),connection('DESCONECTAR'),connection('CONECTAR')]),patch.object(module,'field'),patch.object(module,'tap') as taps,patch.object(module,'fixture_start',side_effect=RuntimeError('synthetic startup blocked')),patch.object(module,'owned_fixture_stop',side_effect=RuntimeError('synthetic cleanup unavailable')):
                with self.assertRaisesRegex(RuntimeError,'synthetic startup blocked'):module.run()
            self.assertEqual(taps.call_count,2,'start connection and guarded final disconnect must both be attempted')
            self.assertEqual(client.find.call_count,2)
            report=json.loads(next(client.out.glob('regression-results-*.json')).read_text())
            self.assertFalse(report['passed'])
            self.assertEqual([e['stage'] for e in report['cleanupErrors']],['fixture'])

    def test_fixture_cleanup_never_calls_a_daemon_error_successful_removal(self):
        import importlib
        from types import SimpleNamespace
        module=importlib.import_module("tablet_regression");cid="2"*64
        metadata={"Id":cid,"Mounts":[{"Source":str(ui.ROOT/'tests/fixture.py')}],"Config":{"Env":["ROS_DOMAIN_ID=67"],"Labels":{"io.lamggm.pocket.harness-owner":"HOST_OWNER"}},"State":{"Running":True}}
        replies=[SimpleNamespace(returncode=0,stdout=json.dumps([metadata]),stderr=""),SimpleNamespace(returncode=0,stdout=cid,stderr=""),SimpleNamespace(returncode=1,stdout="",stderr="Cannot connect to the Docker daemon")]
        with patch.object(module,"FIXTURE_NAME","HOST_NAME"),patch.object(module,"FIXTURE_OWNER","HOST_OWNER"),patch.object(module.subprocess,"run",side_effect=replies):
            with self.assertRaisesRegex(RuntimeError,"removal"):module.owned_fixture_stop()

    def test_fixture_cleanup_stops_the_verified_immutable_container_id(self):
        import importlib
        from types import SimpleNamespace
        module=importlib.import_module("tablet_regression");cid="1"*64;calls=[]
        metadata={"Id":cid,"Mounts":[{"Source":str(ui.ROOT/'tests/fixture.py')}],"Config":{"Env":["ROS_DOMAIN_ID=67"],"Labels":{"io.lamggm.pocket.harness-owner":"HOST_OWNER"}},"State":{"Running":True}}
        def docker(args,**kwargs):
            calls.append(args)
            if len(calls)==1:return SimpleNamespace(returncode=0,stdout=json.dumps([metadata]),stderr="")
            if args[:2]==['docker','stop']:return SimpleNamespace(returncode=0,stdout=cid,stderr="")
            return SimpleNamespace(returncode=1,stdout="[]",stderr="Error: No such object: "+cid)
        with patch.object(module,"FIXTURE_NAME","HOST_MUTABLE_NAME"),patch.object(module,"FIXTURE_OWNER","HOST_OWNER"),patch.object(module.subprocess,"run",side_effect=docker):
            module.owned_fixture_stop()
        self.assertEqual([args[-1] for args in calls if args[:2]==['docker','stop']],[cid])
        self.assertEqual(calls[-1],['docker','inspect',cid])

    def test_fixture_cleanup_refuses_an_unowned_same_domain_container(self):
        import importlib
        from types import SimpleNamespace
        module = importlib.import_module("tablet_regression")
        metadata = {"Mounts": [{"Source": str(ui.ROOT / "tests/fixture.py")}], "Config": {"Env": ["ROS_DOMAIN_ID=67"], "Labels": {"io.lamggm.task": "UNRELATED_OWNER"}}, "State": {"Running": True}}
        calls = []
        def synthetic_docker(args, **kwargs):
            calls.append(args)
            if args[:2] == ["docker", "stop"]:
                self.fail("Cleanup attempted to stop an unrelated container")
            return SimpleNamespace(returncode=0, stdout=json.dumps([metadata]))
        with patch.object(module, "FIXTURE_NAME", "HOST_FIXTURE_NEVER_RUN", create=True), patch.object(module, "FIXTURE_OWNER", "HOST_ONLY_OWNER", create=True), patch.object(module.subprocess, "run", side_effect=synthetic_docker):
            with self.assertRaisesRegex(RuntimeError, "ownership"):
                module.owned_fixture_stop()
        self.assertFalse(any(c[:2] == ["docker", "stop"] for c in calls))

    def test_acceptance_import_has_no_actions_and_run_uses_guarded_client(self):
        import importlib
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        transport.xml = '<hierarchy><node package="' + ui.PKG + '" text="HOST diagnostics" resource-id="' + ui.PKG + ':id/diagnostics"/><node package="' + ui.PKG + '" text="CONECTADO · Wi-Fi 192.0.2.17" resource-id="' + ui.PKG + ':id/status"/><node package="' + ui.PKG + '" content-desc="Tópico /pocket_test/chatter"/></hierarchy>'
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with patch.object(ui, "_default", client), patch.dict(os.environ, {"POCKET_EXPECT_WIFI_IPV4": "192.0.2.17"}), patch("sys.stdout", new_callable=io.StringIO):
                module = importlib.import_module("tablet_accept")
                self.assertEqual(transport.calls, [], "Importing acceptance must never automate a device")
                result = module.run()
            self.assertEqual(result["kind"], "synthetic_transport_test")
            self.assertTrue(result["passed"])


class NegativeGuardTests(unittest.TestCase):
    """Additional negative controls for the guarded behaviors above."""
    def test_invalid_serial_never_selects_a_default_device(self):
        for serial in (None, "", "a b", "device\nother"):
            with self.subTest(serial=serial), tempfile.TemporaryDirectory() as directory:
                transport = TraceAdb()
                with self.assertRaises(RuntimeError): ui.Client(serial, out=Path(directory), runner=transport)
                self.assertEqual(transport.calls, [])

    def test_nonprimary_user_stops_before_hierarchy(self):
        transport = TraceAdb(); transport.user = "10"
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with self.assertRaisesRegex(RuntimeError, "user 0"): client.dump()
            self.assertFalse(any("uiautomator" in c for c in transport.calls))

    def test_package_substring_cannot_authorize_another_install(self):
        transport = TraceAdb(); transport.packages += ".other"
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with self.assertRaisesRegex(RuntimeError, "Exact DEV"): client.dump()
            self.assertFalse(any("uiautomator" in c for c in transport.calls))

    def test_ime_timeout_sends_one_back_and_no_tap(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"; transport.ime_reads = [True]
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with patch.object(ui.time, "monotonic", side_effect=[0., 5.]), self.assertRaisesRegex(RuntimeError, "one Back"):
                client.tap("connect")
        self.assertEqual(transport.backs, 1); self.assertEqual(transport.taps, 0)

    def test_lifecycle_commands_require_an_explicit_primary_user(self):
        for args in (("force-stop",ui.PKG),("start","-n",ui.PKG+"/.MainActivity"),("force-stop","--user","all",ui.PKG)):
            with self.subTest(args=args), tempfile.TemporaryDirectory() as directory:
                client=ui.Client("HOST_TEST_DOUBLE",out=Path(directory));client.verified=True
                with patch.object(client,"_raw",return_value="0\n") as raw:
                    with self.assertRaisesRegex(RuntimeError,"user 0"):
                        client.adb("shell","am",*args)
                self.assertFalse(any(call.args[:3]==("shell","am",args[0]) for call in raw.call_args_list))

    def test_transport_timeout_cannot_return_cached_success(self):
        import subprocess
        with patch.object(ui.subprocess, "run", side_effect=subprocess.TimeoutExpired("synthetic-adb", 1)):
            with self.assertRaisesRegex(RuntimeError, "transport failed"):
                ui.Client._process(["SYNTHETIC_NEVER_EXECUTED"], 1)

    def test_evidence_label_rejects_path_traversal_before_adb(self):
        transport = TraceAdb()
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with self.assertRaises(RuntimeError): client.dump("../escape")
            self.assertEqual(transport.calls, [])

    def test_foreign_nodes_are_not_saved_even_inside_an_owned_hierarchy(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"
        transport.xml = transport.xml.replace("</hierarchy>", '<node package="com.example.foreign" text="DO_NOT_RETAIN_FOREIGN_TEXT"/></hierarchy>')
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with self.assertRaisesRegex(RuntimeError,"outside the target package"):client.dump()
            self.assertTrue(all("DO_NOT_RETAIN" not in p.read_text() for p in client.out.iterdir()))

    def test_malformed_xml_remains_a_failed_attempt(self):
        transport = TraceAdb(); transport.focused = ui.PKG + "/com.lamggm.rospocket.MainActivity"; transport.xml = "<broken>"
        with tempfile.TemporaryDirectory() as directory:
            client = ui.Client("HOST_TEST_DOUBLE", out=Path(directory), runner=transport)
            with self.assertRaises(RuntimeError): client.dump()
            records = [json.loads(p.read_text()) for p in client.out.glob("*.json")]
            self.assertEqual([r["status"] for r in records], ["failed"])
            self.assertEqual(list(client.out.glob("*.xml")), [])


if __name__ == "__main__":
    unittest.main()
