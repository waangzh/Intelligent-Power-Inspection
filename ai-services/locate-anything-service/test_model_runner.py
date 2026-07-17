import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from common.schemas import DetectionPrompt, LocateCheckpointRequest
from model_runner import LocateAnythingRunner, save_combined_annotated_image


class CombinedAnnotationTests(unittest.TestCase):
    def test_multiple_findings_share_one_annotated_image(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.jpg"
            output = Path(directory) / "output"
            Image.new("RGB", (400, 300), "white").save(source)
            findings = [
                {
                    "type": "SWITCH",
                    "label": "红色刀闸开关",
                    "outputType": "box",
                    "normalizedBox": [100, 100, 300, 300],
                    "point": None,
                },
                {
                    "type": "FIRE",
                    "label": "明火烟雾",
                    "outputType": "box",
                    "normalizedBox": [600, 500, 900, 900],
                    "point": None,
                },
            ]

            with patch("model_runner.settings.annotated_output_dir", str(output)), patch(
                "model_runner.settings.annotated_base_url", "http://127.0.0.1:9001/files/annotated"
            ):
                image_url = save_combined_annotated_image(str(source), findings, "request/001")

            self.assertEqual(
                image_url,
                "http://127.0.0.1:9001/files/annotated/request_001_annotated.jpg",
            )
            result = output / "request_001_annotated.jpg"
            self.assertTrue(result.is_file())
            with Image.open(result) as annotated:
                self.assertNotEqual(annotated.getpixel((40, 30)), (255, 255, 255))
                self.assertNotEqual(annotated.getpixel((240, 150)), (255, 255, 255))

    def test_empty_findings_do_not_create_an_annotated_image(self):
        self.assertIsNone(save_combined_annotated_image("unused.jpg", [], "request_002"))

    def test_runner_assigns_one_combined_image_to_all_findings(self):
        runner = LocateAnythingRunner()
        request = LocateCheckpointRequest(
            requestId="request_003",
            imageUrl="source.jpg",
            imageWidth=400,
            imageHeight=300,
            detections=[
                DetectionPrompt(type="SWITCH", displayLabel="刀闸开关", prompt="定位红色刀闸开关"),
                DetectionPrompt(type="METER", displayLabel="压力表", prompt="定位压力表"),
            ],
        )
        answers = ["<box><100><100><300><300></box>", "<box><600><500><900><900></box>"]
        combined_url = "http://127.0.0.1:9001/files/annotated/request_003_annotated.jpg"

        with patch.object(runner, "_predict", side_effect=answers), patch(
            "model_runner.save_combined_annotated_image", return_value=combined_url
        ) as save_image:
            findings, result_image_url = runner.locate_checkpoint(request)

        self.assertEqual(result_image_url, combined_url)
        self.assertEqual([finding.label for finding in findings], ["刀闸开关", "压力表"])
        self.assertEqual([finding.imageUrl for finding in findings], [combined_url, combined_url])
        save_image.assert_called_once()


if __name__ == "__main__":
    unittest.main()
