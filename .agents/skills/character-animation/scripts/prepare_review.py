#!/usr/bin/env python3
"""Build a self-contained timed preview; never marks visual checks as passed."""

import argparse
import base64
import hashlib
import io
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw


def positive(value):
    return type(value) in (int, float) and math.isfinite(value) and value > 0


def prepare(clip_path, output):
    clip_path, output = Path(clip_path), Path(output)
    clip_bytes = clip_path.read_bytes()
    clip = json.loads(clip_bytes)
    canvas = clip["canvas_px"]
    display = clip["display_px"]
    for size in (canvas, display):
        if not isinstance(size, list) or len(size) != 2 or any(type(v) is not int or v <= 0 for v in size):
            raise ValueError("canvas_px and display_px must be positive integer pairs")
    if canvas[0] * display[1] != canvas[1] * display[0]:
        raise ValueError("Display must preserve the canvas aspect ratio")
    if type(clip["loop"]) is not bool or not clip["frames"]:
        raise ValueError("A boolean loop and nonempty frames timeline are required")
    timeline, thumbs = [], []
    elapsed = 0
    for index, frame in enumerate(clip["frames"]):
        duration = frame["duration_ms"]
        if not positive(duration):
            raise ValueError(f"Invalid duration at frame {index}")
        relative = frame["path"]
        path = (clip_path.parent / relative).resolve()
        if Path(relative).is_absolute() or not path.is_relative_to(clip_path.parent.resolve()):
            raise ValueError("Frame paths must stay within the clip directory")
        raw = path.read_bytes()
        with Image.open(io.BytesIO(raw)) as source:
            if getattr(source, "n_frames", 1) != 1 or list(source.size) != canvas:
                raise ValueError(f"Expected one restored full-canvas frame: {relative}")
            rgba = source.convert("RGBA")
        encoded = io.BytesIO()
        rgba.save(encoded, format="PNG")
        timeline.append({"path": relative, "sha256": hashlib.sha256(raw).hexdigest(),
                         "start_ms": elapsed, "duration_ms": duration,
                         "src": "data:image/png;base64," + base64.b64encode(encoded.getvalue()).decode()})
        elapsed += duration
        thumb = rgba.copy()
        thumb.thumbnail((160, 160))
        thumbs.append(thumb)
    if not positive(clip["duration_ms"]) or not math.isclose(elapsed, clip["duration_ms"], abs_tol=0.001):
        raise ValueError("Timeline sum does not match duration_ms")
    if not clip["loop"]:
        terminal = clip.get("terminal", {})
        if terminal.get("preview_end") != "hold_last":
            raise ValueError("One-shot preview must declare terminal.preview_end = hold_last")
        if not all(isinstance(terminal.get(k), str) and terminal[k].strip()
                   for k in ("pose", "handoff", "completion")):
            raise ValueError("One-shot needs terminal pose, handoff and completion rules")
    data = {"canvas": canvas, "display": display, "loop": clip["loop"],
            "total": elapsed, "frames": timeline}
    # Paths are labels only; escape '<' so even unusual filenames cannot end the script.
    payload = json.dumps(data).replace("<", "\\u003c")
    html = HTML.replace("__CLIP_DATA__", payload)
    columns = min(4, len(thumbs))
    sheet = Image.new("RGB", (columns * 180, math.ceil(len(thumbs) / columns) * 202), "#d0d0d0")
    draw = ImageDraw.Draw(sheet)
    for i, thumb in enumerate(thumbs):
        x, y = (i % columns) * 180, (i // columns) * 202
        sheet.paste(thumb, (x + (180 - thumb.width) // 2, y), thumb)
        draw.text((x + 4, y + 163), f"{i:03d}  {timeline[i]['start_ms']:g} ms", fill="black")
        draw.text((x + 4, y + 180), f"hold {timeline[i]['duration_ms']:g} ms", fill="black")
    inventory = {
        "status": "prepared_unreviewed",
        "clip_sha256": hashlib.sha256(clip_bytes).hexdigest(),
        "duration_ms": elapsed,
        "frames": [{k: v for k, v in f.items() if k != "src"} for f in timeline],
        "limits": "Browser preview is not runtime performance or visual acceptance evidence.",
    }
    # Refuse to overwrite evidence from another candidate or a previous review.
    output.mkdir(parents=True, exist_ok=False)
    (output / "preview.html").write_text(html)
    sheet.save(output / "contact-sheet.png")
    inventory["artifacts_sha256"] = {
        name: hashlib.sha256((output / name).read_bytes()).hexdigest()
        for name in ("preview.html", "contact-sheet.png")
    }
    (output / "inventory.json").write_text(json.dumps(inventory, indent=2) + "\n")
    return inventory


HTML = r'''<!doctype html>
<html lang="en"><meta charset="utf-8"><title>Animation review candidate</title>
<style>body{font:16px sans-serif;margin:24px;background:#888}button,select{font:inherit;margin:6px}
#stage{display:block;background:#eee}#info{white-space:pre-wrap}</style>
<h1>Animation review candidate — unreviewed</h1>
<button id="play" disabled>Pause</button><button id="restart" disabled>Restart</button>
<label>Speed <select id="speed"><option value="1">1×</option><option value="0.25">0.25×</option></select></label>
<label>Background <select id="bg"><option value="#eee">Light</option><option value="#151515">Dark</option></select></label>
<p>Foreground tab only. CSS size is calibrated using devicePixelRatio; record browser zoom,
DPR and observed dimensions. A stalled preview is not evidence of smooth motion.</p>
<canvas id="stage"></canvas><p id="info">Loading frames…</p>
<script>
const clip = __CLIP_DATA__;
const stage = document.getElementById('stage'), ctx = stage.getContext('2d');
const info = document.getElementById('info'), play = document.getElementById('play');
const restart = document.getElementById('restart'), speedSelect = document.getElementById('speed');
stage.width = clip.display[0]; stage.height = clip.display[1];
stage.style.width = `${clip.display[0] / devicePixelRatio}px`;
stage.style.height = `${clip.display[1] / devicePixelRatio}px`;
document.getElementById('bg').onchange = e => stage.style.background = e.target.value;
function frameAt(elapsed) {
  const t = clip.loop ? elapsed % clip.total : Math.min(elapsed, clip.total);
  let i = 0;
  while (i + 1 < clip.frames.length && t >= clip.frames[i + 1].start_ms) i++;
  return i;
}
let elapsed = 0, last = null, playing = true, rate = 1, cycles = 0;
let maxGap = 0;
const pictures = clip.frames.map(f => { const image = new Image(); image.src = f.src; return image; });
function render() {
  const i = frameAt(elapsed);
  ctx.clearRect(0, 0, stage.width, stage.height);
  ctx.drawImage(pictures[i], 0, 0, stage.width, stage.height);
  info.textContent = `Frame ${i} | ${elapsed.toFixed(1)} ms | completed cycles ${cycles}\n` +
    `Display ${stage.width}×${stage.height} px | DPR ${devicePixelRatio} | max RAF gap ${maxGap.toFixed(1)} ms`;
}
function tick(now) {
  if (playing && last !== null) {
    const gap = now - last; maxGap = Math.max(maxGap, gap);
    elapsed += gap * rate;
    cycles = clip.loop ? Math.floor(elapsed / clip.total) : 0;
    if (!clip.loop && elapsed >= clip.total) { elapsed = clip.total; playing = false; play.textContent = 'Play'; }
  }
  last = now; render(); requestAnimationFrame(tick);
}
play.onclick = () => { playing = !playing; last = null; play.textContent = playing ? 'Pause' : 'Play'; };
restart.onclick = () => { elapsed = 0; cycles = 0; maxGap = 0; last = null; playing = true; play.textContent = 'Pause'; };
speedSelect.onchange = () => { rate = Number(speedSelect.value); last = null; };
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { playing = false; play.textContent = 'Play'; last = null; }
});
Promise.all(pictures.map(p => p.decode())).then(() => {
  play.disabled = false; restart.disabled = false; requestAnimationFrame(tick);
}).catch(e => { info.textContent = `Decode failed: ${e}`; });
</script></html>'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--clip", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    try:
        result = prepare(args.clip, args.out)
    except (OSError, ValueError, KeyError, TypeError, Image.DecompressionBombError) as error:
        parser.exit(1, f"Review preparation failed: {error}\n")
    print(json.dumps({"status": result["status"], "output": str(args.out)}))


if __name__ == "__main__":
    main()
