#!/usr/bin/env python3
"""Guarded plot acceptance helpers and opt-in tablet CLI. No import-time ADB.

Host oracle tests are synthetic controls, never physical acceptance. `run` uses
an already running run-owned fixture; it neither installs nor changes networking.
"""
import argparse
import hashlib
import ipaddress
import json
from pathlib import Path
import re
import time


def integer(value, minimum=0):
    return type(value) is int and minimum <= value < (1 << 63)


def validate_series(s):
    if type(s) is not dict:
        raise ValueError('Missing series')
    for key in ('slot', 'sessionGeneration', 'readerGeneration', 'pointCount',
                'finitePoints', 'gapPoints', 'breakBeforePoints'):
        if not integer(s.get(key)):
            raise ValueError('Invalid series integer: ' + key)
    n = s['pointCount']
    if s['slot'] > 3 or n > 600 or s['finitePoints'] + s['gapPoints'] != n or s['breakBeforePoints'] > n:
        raise ValueError('Invalid ring cardinality')
    if not isinstance(s.get('seriesId'), str) or not isinstance(s.get('fieldPath'), str):
        raise ValueError('Missing immutable field identity')
    oldest, newest = s.get('oldestReceiveNs'), s.get('newestReceiveNs')
    if n:
        if not integer(oldest) or not integer(newest) or newest - oldest < n - 1 or (n == 1 and newest != oldest):
            raise ValueError('Invalid receive-clock span')
        if newest - oldest > 30_000_000_000:
            raise ValueError('Ring exceeds receive-time window')
    elif oldest is not None or newest is not None:
        raise ValueError('Empty ring with timestamps')
    return s


def identity(s):
    return tuple(s[k] for k in ('seriesId', 'fieldPath', 'slot', 'sessionGeneration', 'readerGeneration'))


def paused_progress(before, after):
    try:
        if before['display'] != after['display'] or len(before['counts']) != len(after['counts']):
            return False
        if not all(integer(a) and integer(b) and b > a for a, b in zip(before['counts'], after['counts'])):
            return False
        latest = {identity(validate_series(s)): s for s in after['intake']}
        return bool(before['intake']) and all(
            identity(s) in latest and latest[identity(s)]['newestReceiveNs'] > s['newestReceiveNs']
            for s in before['intake'])
    except (KeyError, TypeError, ValueError):
        return False


def observation(qa, previous=None, *, slots=(0, 1), visible=True):
    """Fresh completed-draw provenance, not merely a successful dump or prepared frame."""
    if qa.get('valid') is not True or qa.get('foreground') is not True or qa.get('clock') != 'java.nanoTime':
        raise ValueError('Invalid worker capture')
    for key in ('pid', 'lifecycleGeneration', 'sessionGeneration', 'capturedMonotonicNs', 'exportedMonotonicNs'):
        if not integer(qa.get(key), 1):
            raise ValueError('Invalid capture identity/clock')
    capture, export = qa['capturedMonotonicNs'], qa['exportedMonotonicNs']
    if (not integer(qa.get('captureAgeNs')) or not 0 <= export - capture <= 2_000_000_000
            or qa['captureAgeNs'] != export - capture):
        raise ValueError('Expired or inconsistent capture')
    if previous:
        for key in ('pid', 'lifecycleGeneration', 'sessionGeneration'):
            if qa[key] != previous[key]:
                raise ValueError('Candidate process/session changed')
        if capture <= previous['exportedMonotonicNs'] or export <= previous['exportedMonotonicNs']:
            raise ValueError('Worker capture did not pass the prior export fence')
    watches = qa.get('watches')
    if type(watches) is not list or len(watches) != 4:
        raise ValueError('Missing four-slot projection')
    for slot, w in enumerate(watches):
        if type(w) is not dict or type(w.get('slot')) is not int or w['slot'] != slot or w.get('valid') is not True:
            raise ValueError('Invalid slot projection')
        if slot in slots:
            if any(w.get(k) is not v for k, v in [('pending', False), ('readerActive', True), ('hasError', False), ('freshAtExport', True)]):
                raise ValueError('Selected reader not live')
            if (not integer(w.get('count'), 1) or not integer(w.get('readerGeneration'))
                    or not integer(w.get('requestVersion')) or not integer(w.get('appliedVersion')) or w['requestVersion'] != w['appliedVersion']):
                raise ValueError('Reader identity/counter not applied')
    intake, display = qa['plotIntake'], qa['plotDisplay']
    if (type(intake.get('capacityPerSeries')) is not int or intake['capacityPerSeries'] != 600
            or type(intake.get('windowNs')) is not int or intake['windowNs'] != 30_000_000_000):
        raise ValueError('Changed ring budget')
    if (display.get('drawPending') is not False or not integer(display.get('preparedRevision'))
            or not integer(display.get('drawnRevision')) or display['preparedRevision'] != display['drawnRevision']):
        raise ValueError('Prepared frame is not a completed draw')
    observed, drawn = display.get('observedMonotonicNs'), display.get('lastDrawMonotonicNs')
    if (observed != export or not integer(observed) or not integer(drawn, 1)
            or not integer(display.get('drawAgeNs')) or not 0 <= observed - drawn <= 2_000_000_000
            or display['drawAgeNs'] != observed - drawn):
        raise ValueError('Stale draw')
    if visible and display.get('chartFullyVisible') is not True:
        raise ValueError('Chart not fully visible')
    if type(display.get('paused')) is not bool or display.get('drawnSeries') != display.get('series'):
        raise ValueError('Draw/prepared association differs')
    for collection in (intake.get('series'), display.get('series')):
        if type(collection) is not list or len(collection) > 4:
            raise ValueError('Invalid selected series')
        seen = set()
        for s in collection:
            validate_series(s)
            ident = identity(s)
            if ident in seen or s['sessionGeneration'] != qa['sessionGeneration'] or s['readerGeneration'] != watches[s['slot']]['readerGeneration']:
                raise ValueError('Retired, duplicate or unbound graph identity')
            seen.add(ident)
    current = {identity(s): s for s in intake['series']}
    if any(identity(s) not in current for s in display['series']):
        raise ValueError('Display is detached from selected intake')
    return {'display': display['series'], 'intake': intake['series'],
            'counts': [watches[i]['count'] for i in slots]}


class Navigator:
    """Source-derived selectors, bounded viewport search, inherited package guards."""
    def __init__(self, client, namespace):
        self.client, self.namespace = client, namespace

    def tap(self, selector, direction):
        self.client.find(selector, direction=direction)
        self.client.tap(selector)

    def watch(self, slot, suffix):
        self.client.find('diagnostics', direction='up')
        self.tap('watch_select_' + str(slot), 'down')
        # QoS may persist: read and explicitly set best-effort/volatile.
        import tablet_ui as ui
        for ident in ('qos_reliable', 'qos_transient'):
            nodes = self.client.find(ident, direction='down')
            if ui.node_by(nodes, ident).get('checked') == 'true':
                self.client.tap(ident)
                nodes = self.client.find(ident, direction='up')
            if ui.node_by(nodes, ident).get('checked') != 'false':
                raise RuntimeError('QoS readback failed')
        self.client.find('explorer_search', direction='up')
        self.client.field('explorer_search', self.namespace + '/' + suffix)
        self.client.hide_ime()
        # ExplorerView's exact topic content-description invokes listener.read.
        self.tap('Tópico ' + self.namespace + '/' + suffix, 'down')

    def series(self, slot, suffix, selected=True):
        import tablet_ui as ui
        caption = f'Watch {slot + 1} · {self.namespace}/{suffix} · data [unidade desconhecida]'
        self.client.find('diagnostics', direction='up')
        nodes = self.client.find(caption, direction='down')
        if ui.node_by(nodes, caption).get('checked') != str(selected).lower():
            self.client.tap(caption)
        nodes = self.client.find(caption, direction='up')
        if ui.node_by(nodes, caption).get('checked') != str(selected).lower():
            raise RuntimeError('Plot selection readback failed')
        self.client.find('plot_chart', direction='down')

    def chart(self):
        self.client.find('plot_chart', direction='down')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('run',))
    parser.add_argument('--serial', required=True)
    parser.add_argument('--namespace', required=True)
    parser.add_argument('--domain', type=int, required=True)
    parser.add_argument('--peer', default='')
    parser.add_argument('--candidate-apk', type=Path, required=True)
    parser.add_argument('--fixture-container', required=True, help='immutable owned container ID')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--authorize-device', action='store_true')
    args = parser.parse_args()
    if not args.authorize_device:
        parser.error('Explicit --authorize-device is required')
    if not re.fullmatch(r'/plot_[0-9a-f]{32}', args.namespace) or not 0 <= args.domain <= 101:
        parser.error('Invalid run-owned namespace/domain')
    if args.peer:
        args.peer = str(ipaddress.IPv4Address(args.peer))
    if not re.fullmatch(r'[a-f0-9]{64}', args.fixture_container):
        parser.error('Exact immutable fixture ID required')
    if not args.candidate_apk.is_file():
        parser.error('Actual candidate APK required')
    import os
    os.environ['POCKET_TABLET'] = args.serial
    import tablet_ui as ui
    client = ui.Client(args.serial, out=args.out)
    from plot_device_campaign import campaign
    result = campaign(client, args)
    print(json.dumps(result, indent=2))
    if result.get('passed') is not True:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
