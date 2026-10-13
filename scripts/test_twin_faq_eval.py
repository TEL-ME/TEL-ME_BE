import unittest

from twin_faq_eval import clusters, deduped, short_lists


class ClustersTest(unittest.TestCase):

    def test_chains_pairs_and_picks_first_in_file_order(self):
        rep = clusters(["A", "B", "C", "D"], [(2, 1, 0.97), (1, 0, 0.96)])

        self.assertEqual({"A": "A", "B": "A", "C": "A", "D": "D"}, rep)


class DedupedTest(unittest.TestCase):

    rep = {"A": "A", "B": "A", "C": "C"}
    results = [{"slot_id": "B", "score": 0.9}, {"slot_id": None, "score": 0.85},
               {"slot_id": "A", "score": 0.8}, {"slot_id": "C", "score": 0.7}]

    def test_merge_keeps_best_score_of_group_under_representative(self):
        self.assertEqual([{"slot_id": "A", "score": 0.9}, {"slot_id": None, "score": 0.85},
                          {"slot_id": "C", "score": 0.7}], deduped(self.results, self.rep, "통합"))

    def test_delete_drops_non_representatives(self):
        self.assertEqual([{"slot_id": None, "score": 0.85}, {"slot_id": "A", "score": 0.8},
                          {"slot_id": "C", "score": 0.7}], deduped(self.results, self.rep, "삭제"))


class ShortListsTest(unittest.TestCase):

    def test_flags_only_when_cut_raw_list_still_above_line(self):
        raw = [{"slot_id": "A", "score": 0.9}] * 10
        new = [{"slot_id": "A", "score": 0.9}]

        self.assertEqual(["E1"], short_lists({"E1": (raw, new)}, 0.8))
        self.assertEqual([], short_lists({"E1": (raw, new)}, 0.95))


if __name__ == "__main__":
    unittest.main()
