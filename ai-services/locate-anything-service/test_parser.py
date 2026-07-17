import unittest

from parser import parse_answer


class ParseAnswerTests(unittest.TestCase):
    def test_keeps_valid_box_when_y1_is_greater_than_x2(self):
        parsed = parse_answer(
            "<box><100><500><300><900></box>",
            image_width=1000,
            image_height=1000,
        )

        self.assertEqual([100, 500, 300, 900], parsed["normalizedBox"])

    def test_orders_each_axis_independently(self):
        parsed = parse_answer(
            "<box><800><700><200><100></box>",
            image_width=1000,
            image_height=1000,
        )

        self.assertEqual([200, 100, 800, 700], parsed["normalizedBox"])


if __name__ == "__main__":
    unittest.main()
