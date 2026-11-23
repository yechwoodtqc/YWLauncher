#!/usr/bin/env python3
"""Fetch the pinned Breezy Weather condition artwork before Android resource processing."""
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
UPSTREAM = "51c4f148241a543e9142994417af41481b75773e"
RAW = f"https://raw.githubusercontent.com/breezy-weather/breezy-weather/{UPSTREAM}"
DRAWABLES = ROOT / "app" / "src" / "main" / "res" / "drawable"
LICENSE_DIR = ROOT / "licenses"
ICONS = (
    "weather_clear_day",
    "weather_clear_night",
    "weather_partly_cloudy_day",
    "weather_partly_cloudy_night",
    "weather_cloudy",
    "weather_rain",
    "weather_snow",
    "weather_sleet",
    "weather_thunderstorm",
    "weather_fog",
    "weather_haze",
    "weather_hail",
    "weather_wind",
)

def fetch(url: str, destination: pathlib.Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "YWLauncher-build/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read()
    if not data:
        raise RuntimeError(f"Empty upstream asset: {url}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)

for icon in ICONS:
    fetch(f"{RAW}/app/src/main/res/drawable/{icon}.png",
          DRAWABLES / f"breezy_{icon}.png")

LICENSE_DIR.mkdir(parents=True, exist_ok=True)
fetch(f"{RAW}/LICENSE", LICENSE_DIR / "BREEZY-WEATHER-LGPL-3.0.txt")
print(f"Fetched {len(ICONS)} Breezy Weather icons from pinned commit {UPSTREAM}.")
