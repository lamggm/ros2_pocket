"""Fixture-only reference for the UI actions replayed by PlotDdsReplayTest.
The replay checks these requests against the ACTUAL production controller.
Never rewrites native snapshots or synthesizes samples/clocks.
"""
class BatchSelection:
    def __init__(self):
        self.selected = set()
        self.epoch = 0
        self.last = None
        self.last_epoch = None
        self.identities = {}

    def request(self):
        fields = [[slot, 'data'] for slot in sorted(self.selected)]
        if self.last is not None and self.last != fields and self.last_epoch == self.epoch:
            self.epoch += 1
        self.last, self.last_epoch = fields, self.epoch
        return {'epoch': self.epoch, 'fields': fields}

    def select(self, slot):
        if slot not in self.selected:
            self.selected.add(slot)
            self.epoch += 1

    def remove(self, slot):
        self.selected.discard(slot)
        self.epoch += 1

    def observe(self, snapshot):
        for slot, row in enumerate(snapshot['watches']):
            identity = (snapshot['sessionGeneration'], row['generation'], row['subscription'])
            healthy = (snapshot['running'] and row['readerActive'] and row['fresh']
                       and row['subscriptionState'] == 'receiving' and not row['error'])
            if not healthy or (slot in self.identities and self.identities[slot] != identity):
                self.selected.discard(slot)
            self.identities[slot] = identity
            # Production computes the immutable selection before decoding each
            # healthy watch, including a retirement earlier in this same poll.
            if healthy:
                self.request()
