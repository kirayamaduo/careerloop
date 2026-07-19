"""Fail-closed regression tests for body-language frame analysis."""

import importlib.util
import sys
from types import SimpleNamespace
from types import ModuleType
import unittest
from unittest.mock import patch

import numpy as np

# The local repository test runner may not have the sidecar's web-only
# dependencies installed. Supply tiny import shims in that case so these unit
# tests still exercise the scoring/fail-closed logic; the Docker image uses the
# real pinned FastAPI and Pydantic packages.
if importlib.util.find_spec("fastapi") is None:
    fastapi_stub = ModuleType("fastapi")

    class _FastAPI:
        def __init__(self, **_kwargs):
            pass

        def get(self, _path, **_kwargs):
            return lambda fn: fn

        def post(self, _path, **_kwargs):
            return lambda fn: fn

    class _HTTPException(Exception):
        def __init__(self, status_code, detail):
            super().__init__(detail)
            self.status_code = status_code
            self.detail = detail

    fastapi_stub.FastAPI = _FastAPI
    fastapi_stub.HTTPException = _HTTPException
    sys.modules["fastapi"] = fastapi_stub

if importlib.util.find_spec("pydantic") is None:
    pydantic_stub = ModuleType("pydantic")

    class _BaseModel:
        def __init__(self, **values):
            for name in self.__class__.__annotations__:
                setattr(self, name, values.get(name, getattr(self.__class__, name, None)))

    pydantic_stub.BaseModel = _BaseModel
    sys.modules["pydantic"] = pydantic_stub

import main


class _Analyzer:
    def __init__(self, result):
        self.result = result
        self.closed = False

    def process(self, _image):
        return self.result

    def close(self):
        self.closed = True


class BodyLanguageFailClosedTest(unittest.TestCase):
    def _mock_mediapipe(self, face_result, pose_result):
        face = _Analyzer(face_result)
        pose = _Analyzer(pose_result)
        fake_mp = SimpleNamespace(
            solutions=SimpleNamespace(
                face_mesh=SimpleNamespace(FaceMesh=lambda **_kwargs: face),
                pose=SimpleNamespace(Pose=lambda **_kwargs: pose),
            )
        )
        return fake_mp, face, pose

    def test_no_face_is_explicitly_invalid_and_has_no_scores(self):
        fake_mp, face, pose = self._mock_mediapipe(
            SimpleNamespace(multi_face_landmarks=[]),
            SimpleNamespace(pose_landmarks=None),
        )
        with patch.object(main, "mp", fake_mp):
            response = main._score_with_mediapipe(np.zeros((8, 8, 3), dtype=np.uint8))

        self.assertFalse(response.valid)
        self.assertEqual("no-face-detected", response.note)
        self.assertIsNone(response.eye_contact)
        self.assertIsNone(response.expression)
        self.assertIsNone(response.posture)
        self.assertTrue(face.closed)
        self.assertTrue(pose.closed)

    def test_no_pose_is_explicitly_invalid_and_has_no_scores(self):
        face_landmarks = SimpleNamespace(landmark=[SimpleNamespace(x=0.5, y=0.5)] * 474)
        fake_mp, _, _ = self._mock_mediapipe(
            SimpleNamespace(multi_face_landmarks=[face_landmarks]),
            SimpleNamespace(pose_landmarks=None),
        )
        with patch.object(main, "mp", fake_mp):
            response = main._score_with_mediapipe(np.zeros((8, 8, 3), dtype=np.uint8))

        self.assertFalse(response.valid)
        self.assertEqual("no-pose-detected", response.note)
        self.assertIsNone(response.eye_contact)
        self.assertIsNone(response.expression)
        self.assertIsNone(response.posture)

    def test_incomplete_landmarks_do_not_receive_plausible_defaults(self):
        too_short = SimpleNamespace(landmark=[SimpleNamespace(x=0.5, y=0.5)])
        fake_mp, _, _ = self._mock_mediapipe(
            SimpleNamespace(multi_face_landmarks=[too_short]),
            SimpleNamespace(pose_landmarks=too_short),
        )
        with patch.object(main, "mp", fake_mp):
            response = main._score_with_mediapipe(np.zeros((8, 8, 3), dtype=np.uint8))

        self.assertFalse(response.valid)
        self.assertEqual("incomplete-landmarks", response.note)
        self.assertIsNone(response.eye_contact)
        self.assertIsNone(response.expression)
        self.assertIsNone(response.posture)

    def test_scoring_helpers_raise_on_missing_landmarks_instead_of_returning_55(self):
        with self.assertRaises(IndexError):
            main._eye_contact_score([], (8, 8, 3))
        with self.assertRaises(IndexError):
            main._expression_score([])
        with self.assertRaises(IndexError):
            main._posture_score([])

    def test_encoded_and_decoded_size_limits_fail_before_analysis(self):
        with patch.object(main, "MAX_BASE64_CHARS", 8):
            with self.assertRaises(main.HTTPException) as encoded:
                main._decode_image("A" * 9)
        self.assertEqual(413, encoded.exception.status_code)

        oversized_image = SimpleNamespace(size=(5000, 1000), close=lambda: None)
        with patch.object(main.Image, "open", return_value=oversized_image):
            with self.assertRaises(main.HTTPException) as decoded:
                main._decode_image("aGVsbG8=")
        self.assertEqual(413, decoded.exception.status_code)


if __name__ == "__main__":
    unittest.main()
