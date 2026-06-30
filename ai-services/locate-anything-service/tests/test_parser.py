from parser import parse_answer


def test_parse_box_with_pixel_coordinates():
    parsed = parse_answer("<ref>switch</ref><box><100><200><300><400></box>", 1000, 500)
    assert parsed["outputType"] == "box"
    assert parsed["normalizedBox"] == [100, 200, 300, 400]
    assert parsed["pixelBox"] == [100, 100, 300, 200]


def test_parse_point():
    parsed = parse_answer("<box><500><250></box>", 1000, 1000)
    assert parsed["outputType"] == "point"
    assert parsed["point"] == [500, 250]


def test_parse_none():
    parsed = parse_answer("<box>none</box>", 1000, 1000)
    assert parsed["outputType"] == "none"

def test_parse_box_with_official_coordinate_order():
    parsed = parse_answer("<box><100><300><200><400></box>", 1000, 500)
    assert parsed["outputType"] == "box"
    assert parsed["normalizedBox"] == [100, 200, 300, 400]
    assert parsed["pixelBox"] == [100, 100, 300, 200]


def test_parse_box_with_spaces_and_escaped_text():
    parsed = parse_answer("&lt;box&gt; <100> <300> <200> <400> &lt;/box&gt;", 1000, 500)
    assert parsed["outputType"] == "box"
    assert parsed["normalizedBox"] == [100, 200, 300, 400]
