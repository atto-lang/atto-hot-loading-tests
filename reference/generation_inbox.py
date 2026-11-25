"""Hot-reload generation fence model (host-only, no actor runtime)."""
from collections import deque
from enum import Enum


class Admission(Enum):
    ACCEPTED = "accepted"
    STALE = "stale"
    REPLAY = "replay"
    FULL = "full"
    CLOSED = "closed"


class GenerationInbox:
    def __init__(self, capacity=64):
        if type(capacity) is not int or not 1 <= capacity <= 4096:
            raise ValueError("capacity must be an integer from 1 to 4096")
        self.capacity = capacity
        self.generation = 1
        self._pending = deque()
        self._seen = set()
        self._closed = False

    def send(self, generation, ticket, message):
        if self._closed:
            return Admission.CLOSED
        if type(generation) is not int or generation != self.generation:
            return Admission.STALE
        if type(ticket) is not int or not 0 <= ticket < (1 << 63):
            return Admission.REPLAY
        if ticket in self._seen:
            return Admission.REPLAY
        if len(self._pending) >= self.capacity:
            return Admission.FULL
        if len(self._seen) >= self.capacity * 4:
            return Admission.FULL
        self._seen.add(ticket)
        self._pending.append((ticket, message))
        return Admission.ACCEPTED

    def receive(self):
        if not self._pending:
            return (False, None)
        return (True, self._pending.popleft())

    def reload(self):
        """Increment generation and invalidate all old tickets and pending mail."""
        if self._closed:
            raise RuntimeError("cannot reload a closed inbox")
        if self.generation == (1 << 63) - 1:
            raise OverflowError("generation budget exhausted")
        dropped = len(self._pending)
        self._pending.clear()
        self._seen.clear()
        self.generation += 1
        return dropped

    def close(self):
        self._closed = True
        self._pending.clear()
        self._seen.clear()
