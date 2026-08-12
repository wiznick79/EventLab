# Regenerating recorded demonstrations

The permanent tour embeds three animated WebP recordings: duplicate suppression, dead-letter recovery, and saga compensation. They are deterministic presentation captures, not claims that a backend was contacted during playback. Their states and decision attributes mirror the executable scenario checks and trace-reading guide.

## Capture frames

1. Start the frontend with `npm run dev`.
2. Use a 1280 × 720 browser viewport.
3. For each scenario, capture each numbered step from these URLs:

   - `/?recording=duplicate&step=1` through `step=5`
   - `/?recording=recovery&step=1` through `step=6`
   - `/?recording=compensation&step=1` through `step=5`

4. Save PNG frames as `frontend/recording-frames/<scenario>-<step>.png`.

The frame directory is ignored by Git. Each page is exactly 1280 × 720 and has no animation, network request, clock, or random data, so repeated captures are stable.

## Compose browser media

Run:

```powershell
python scripts/compose-recordings.py
```

The script uses Pillow to create optimized looping WebP assets in `frontend/public/recordings/`. Intermediate frames remain untracked and may be removed after inspecting the results.

## Verify

- Open each WebP directly and confirm every delivery remains readable.
- Run `npm test` and `npm run build` from `frontend/`.
- Check the static tour at `/?tour`, including a narrow mobile viewport.

The recordings explain the execution sequence. The live lab, decision spans, and persisted scenario assertions remain the authoritative executable evidence.
