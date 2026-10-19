# PulseStamp

Put your real heart rate and GPS on your Insta360 videos. PulseStamp reads a workout from your phone (Health Connect, or Samsung Health for Galaxy Watch GPS) and saves a GPX file that the Insta360 app can use for its gauges.

Everything stays on your phone. The app has no internet access, no account and no cloud. A file only leaves your phone if you choose to share it.

## Credits

- Made by Akshay Kumar Saha, with help from Claude by Anthropic.
- Galaxy Watch GPS routes are read with the Samsung Health Data SDK.
- Fonts: Unbounded and Manrope (SIL Open Font License, see `docs/licenses/`).

PulseStamp is an independent app. Not affiliated with Insta360 (Arashi Vision), Instagram (Meta), Samsung or Google. Samsung and Samsung Health are trademarks of Samsung Electronics Co., Ltd.

## Building

The Samsung Health Data SDK can't be shared, so it isn't in this repo. To build locally, download it from Samsung and put `samsung-health-data-api-1.1.0.aar` in `app/libs/`. CI fetches it from a private repo.
