#!/usr/bin/env python3
"""Offline protocol smoke check."""
from app.main import create_app


if __name__ == "__main__":
    assert create_app().title == "Robot Platform Bridge"
    print("bridge smoke: ok")
