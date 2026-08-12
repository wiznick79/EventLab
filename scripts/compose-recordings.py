from pathlib import Path
import sys

from PIL import Image


SCENARIOS = {
    "duplicate": 5,
    "recovery": 6,
    "compensation": 5,
}


def main() -> None:
    repository = Path(__file__).resolve().parents[1]
    frames_directory = repository / "frontend" / "recording-frames"
    output_directory = repository / "frontend" / "public" / "recordings"
    output_directory.mkdir(parents=True, exist_ok=True)

    for scenario, count in SCENARIOS.items():
        frames = [Image.open(frames_directory / f"{scenario}-{step}.png").convert("RGB")
                  for step in range(1, count + 1)]
        frames[0].save(
            output_directory / f"{scenario}.webp",
            save_all=True,
            append_images=frames[1:],
            duration=[1400] * (count - 1) + [3200],
            loop=0,
            quality=82,
            method=6,
        )
        for frame in frames:
            frame.close()


if __name__ == "__main__":
    try:
        main()
    except FileNotFoundError as error:
        print(f"Missing rendered frame: {error.filename}", file=sys.stderr)
        raise SystemExit(1) from error
