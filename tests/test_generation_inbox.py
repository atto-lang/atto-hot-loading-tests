import unittest
from reference.generation_inbox import Admission, GenerationInbox


class GenerationFenceTest(unittest.TestCase):
    def test_replay_after_receive_is_rejected(self):
        inbox = GenerationInbox(2)
        self.assertEqual(inbox.send(1, 7, None), Admission.ACCEPTED)
        self.assertEqual(inbox.receive(), (True, (7, None)))
        self.assertEqual(inbox.send(1, 7, "again"), Admission.REPLAY)

    def test_reload_fences_stale_and_drops_old_pending(self):
        inbox = GenerationInbox(2)
        self.assertEqual(inbox.send(1, 1, "old"), Admission.ACCEPTED)
        self.assertEqual(inbox.reload(), 1)
        self.assertEqual(inbox.send(1, 2, "stale"), Admission.STALE)
        self.assertEqual(inbox.send(2, 1, "new"), Admission.ACCEPTED)
        self.assertEqual(inbox.receive(), (True, (1, "new")))

    def test_close_and_budget(self):
        inbox = GenerationInbox(1)
        self.assertEqual(inbox.send(1, 1, "a"), Admission.ACCEPTED)
        self.assertEqual(inbox.send(1, 2, "b"), Admission.FULL)
        inbox.close()
        self.assertEqual(inbox.send(1, 2, "b"), Admission.CLOSED)
        with self.assertRaises(RuntimeError):
            inbox.reload()
        for capacity in [0, 4097, True]:
            with self.assertRaises(ValueError):
                GenerationInbox(capacity)


if __name__ == "__main__":
    unittest.main()
